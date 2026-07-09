# Architecture

Beeper Proxy is a single Android application module (`app`) with no server-side component. Everything described below runs locally on-device.

## Component Overview

```
┌───────────────────────────────────────────────────────────────────┐
│                         Beeper Proxy (app)                        │
│                                                                     │
│  UI (Fragments)                Core                                │
│  ┌─────────────┐        ┌──────────────────────┐                  │
│  │ HomeFragment │───────▶│ AuthTokenManager      │                 │
│  ├─────────────┤        │ (EncryptedSharedPrefs) │                 │
│  │ BuilderFragment│──────▶│                      │                 │
│  ├─────────────┤        ├──────────────────────┤                  │
│  │ AppsFragment │───────▶│ AppBlacklistManager   │                 │
│  └─────────────┘        │ (SharedPreferences)    │                 │
│                          └──────────────────────┘                  │
│                                     │                               │
│                                     ▼                               │
│                          ┌──────────────────────┐                  │
│  External callers  ────▶ │ BeeperProxyProvider   │                 │
│  (ContentResolver)       │ BeeperIntentReceiver  │                 │
│                          └──────────┬───────────┘                  │
│                                     │                               │
│                          ┌──────────▼───────────┐                  │
│                          │ PackageInstallReceiver│                  │
│                          │ (auto-blacklist new   │                  │
│                          │  installs)            │                  │
│                          └──────────────────────┘                  │
└──────────────────────────────────┬────────────────────────────────┘
                                    │ ContentProvider query
                                    ▼
                         Beeper (com.beeper.android)
```

## Components

### UI Layer

- **`MainActivity`** — hosts the three tabs below.
- **`HomeFragment`** — shows/rotates the auth token, live permission status, quick-test buttons.
- **`BuilderFragment`** — builds `content://` URIs and `adb`/Tasker broadcast commands from form input.
- **`AppsFragment`** + **`AppListAdapter`** — lists launchable apps and lets the user block/unblock each one from calling the proxy.

### Core

- **`AuthTokenManager`** — generates and stores the 256-bit auth token in `EncryptedSharedPreferences` (AES-256-GCM). Every proxy call must present this token.
- **`AppBlacklistManager`** — persists the set of blocked package names in `SharedPreferences` and exposes `isBlocked()` for the enforcement layer.
- **`JsonHelper`** — serializes Beeper cursor rows to JSON for the Intent Bridge result payload.

### Entry Points (external callers hit these)

- **`BeeperProxyProvider`** — exported `ContentProvider` at authority `com.beeperproxy.provider`. Validates the auth token, checks the caller's package against the blacklist (via `Binder.getCallingPackage()`, which can't be spoofed by the caller), then forwards the query to Beeper's provider and returns the resulting cursor.
- **`BeeperIntentReceiver`** — exported `BroadcastReceiver` for `com.beeperproxy.QUERY` / `com.beeperproxy.INSERT`, for automation apps (Tasker, Automate) that can't do raw ContentProvider queries. Performs the same token/blacklist checks, then sends back a result broadcast.
- **`PackageInstallReceiver`** — listens for `ACTION_PACKAGE_ADDED` and blacklists newly installed apps by default, so a fresh install can't reach the proxy until the user explicitly allows it from the Apps tab.

## Data Flow

1. An external app (Tasker, Automate, or a script) sends a request, either as a broadcast to `BeeperIntentReceiver` or a query to `BeeperProxyProvider`.
2. The entry point validates the supplied `authToken` against `AuthTokenManager`.
3. The entry point resolves the caller's package via `Binder.getCallingPackage()` and checks it against `AppBlacklistManager`.
4. If both checks pass, the request is forwarded verbatim (params) to Beeper's own `ContentProvider` (`com.beeper.android`).
5. The resulting cursor is either returned directly (ContentProvider path) or serialized to JSON and sent back as a result broadcast (Intent Bridge path).

## Storage

| Data | Mechanism | Notes |
|---|---|---|
| Auth token | `EncryptedSharedPreferences` | AES-256-GCM, rotated on demand |
| App blacklist | `SharedPreferences` (plain) | Package names only, no sensitive data |

## No Network / No Backend

Beeper Proxy does not talk to any server it controls, and does not collect analytics or telemetry. All communication is local IPC between apps on the same device (Binder calls / broadcasts), plus whatever network calls Beeper's own provider makes on Beeper's behalf.
