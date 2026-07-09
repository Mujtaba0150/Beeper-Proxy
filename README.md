# Beeper Proxy

> **Disclaimer:** Beeper Proxy is an independent, community-built project and is not affiliated with, endorsed by, or sponsored by Beeper. "Beeper" is a trademark of its respective owner; it is referenced here only to describe interoperability with Beeper's Android content-provider APIs.

An Android app that acts as a **secure proxy** in front of Beeper's ContentProvider APIs. Automation apps like Tasker and Automate lack the custom runtime permissions Beeper requires — Beeper Proxy holds those permissions and re-exposes the same data behind an auth-token-protected interface those apps can actually reach.

---

## How It Works

```
Tasker / Automate
      │
      │  Intent (com.beeperproxy.QUERY / INSERT)
      │  or ContentProvider query (content://com.beeperproxy.provider/…)
      ▼
┌─────────────────────────────────┐
│          Beeper Proxy           │
│                                 │
│  1. Validates auth token        │
│  2. Checks app blacklist        │
│  3. Forwards to Beeper API      │
│  4. Returns results / cursor    │
└─────────────────────────────────┘
      │
      │  ContentProvider query
      ▼
   Beeper (com.beeper.api)
```

Two interfaces are exposed — use whichever fits your tool:

- **Intent Bridge** (`BeeperIntentReceiver`) — send a broadcast, get a result broadcast back.
- **ContentProvider** (`BeeperProxyProvider`) — standard Android `content://` URI queries. Better for apps and scripts that can work with cursors directly.

---

## Building

### Via GitHub Actions (recommended)

1. Push this repo to GitHub
2. Go to **Actions → Build APK → Run workflow**
3. Download the APK from the **Artifacts** section of the completed run

The workflow also triggers automatically on every push to `main`.

### Locally

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

# Or build and install directly to a connected device:
./gradlew installDebug
```

Requires Android SDK with API level 26+. Java 17+.

---

## First Launch

1. Open the app — it lands on the **Home** tab
2. Tap **Grant Permissions** to request Beeper's `READ_PERMISSION` and `SEND_PERMISSION`
3. Your auth token is auto-generated and shown on screen — copy it, you'll need it in every call
4. Use **Copy Token** to put it on your clipboard, or **Refresh Token** to rotate it

> Beeper must be installed and you must be logged in for the proxy to work.

---

## UI Overview

### Home Tab

- Displays the current auth token with copy and refresh buttons
- Shows live permission status for both Beeper permissions
- Has quick-test buttons for **Chats**, **Messages**, and **Contacts** — runs a live query through the proxy and prints the raw cursor output on screen for quick testing.

### Builder Tab

A query builder that generates ready-to-use ContentProvider URIs and `adb shell am broadcast` commands as you fill in fields. Covers all operations and parameters:

- **Operation selector** — Chats, Chats/Count, Messages, Messages/Count, Contacts, Contacts/Count, Insert
- **Filters** — limit, offset, roomIds, isUnread, isArchived, isLowPriority, showInAllChats, protocol, message query text, senderId, contextBefore/After, openAtUnread, contact query, senderIds
- **Protocol picker** — Any, WhatsApp, Signal, Telegram, iMessage, Instagram, Slack, Discord, SMS, or custom
- **Columns** — comma-separated projection (leave blank for all columns)
- **Insert fields** — Room ID and message text with live validation
- **Copy Query** — copies the generated `content://` URI
- **Copy Intent** — copies the generated `adb` broadcast command
- **Run Test** — executes the current query live and shows results inline

### Apps Tab

Lists every launchable app installed on the device. Use it to control which apps are allowed to call the proxy:

- **Check a row** to block that app — any query or insert from that package will be rejected by the proxy before it reaches Beeper
- Blocked state is persisted in SharedPreferences and enforced in real time

---

## Auth Token

- Generated on first launch using `SecureRandom` (256-bit / 32 bytes, Base64url-encoded)
- Stored in `EncryptedSharedPreferences` (AES-256-GCM)
- Every call to the proxy — Intent or ContentProvider — must include this token or it is rejected
- Rotate it any time from the Home tab; the old token becomes invalid immediately

---

## App Blacklist

Any launchable app on the device can be blocked from using the proxy via the Apps tab. When a blocked package calls the proxy:

- ContentProvider queries return an error cursor with a `"Caller package '…' is blacklisted"` message
- Inserts return `null`
- A warning is written to logcat

The check uses `Binder.getCallingPackage()` which cannot be spoofed by the caller.

---

## Intent Bridge

> **Recommended for Tasker and Automate.**

Send a broadcast to `com.beeperproxy.BeeperIntentReceiver`. The proxy processes it and sends a result broadcast to `com.beeperproxy.INTENT_RESULT`.

### Request

| Field | Value |
|---|---|
| Package | `com.beeperproxy` |
| Class | `com.beeperproxy.BeeperIntentReceiver` |
| Action | `com.beeperproxy.QUERY` or `com.beeperproxy.INSERT` |

### Request Extras

| Extra | Required | Type | Description |
|---|---|---|---|
| `authToken` | ✓ | String | Token from the Home tab |
| `path` | ✓ | String | See [Supported Paths](#supported-paths) |
| `params` | ✗ | String | URL-encoded query params e.g. `limit=10&isUnread=1` |
| `columns` | ✗ | String | Comma-separated column names to return (query only). Omit for all columns. |
| `roomId` | ✗ | String | INSERT only. Can be in `params` instead. |
| `text` | ✗ | String | INSERT only. Can be in `params` instead. |

### Result Broadcast

**Action:** `com.beeperproxy.INTENT_RESULT`

| Extra | Type | Description |
|---|---|---|
| `success` | Boolean | Whether the call succeeded |
| `error` | String | Error message if `success` is false |
| `result` | String | JSON array of row objects (query) or `{"messageId":"…","roomId":"…"}` (insert) |
| `rowCount` | Int | Number of rows returned (query only) |
| `columns` | ArrayList\<String\> | Column names actually returned (query only) |
| `col_<columnName>` | ArrayList\<String\> | One extra per column — each is an array of that column's values across all rows, in row order. Makes it easy to grab a single column in Automate without parsing JSON. |

---

## ContentProvider

The proxy is also accessible as a standard ContentProvider at authority `com.beeperproxy.provider`.

```
content://com.beeperproxy.provider/<path>?authToken=TOKEN&<params>
```

All parameters beyond `authToken` are forwarded verbatim to `com.beeper.api`. If you supply a `projection` array, only those columns are returned; leave it null for all columns.

---

## Supported Paths

| Path | Operation | Description |
|---|---|---|
| `chats` | Query | List of chat rooms |
| `chats/count` | Query | Number of matching chats |
| `messages` | Query | Messages within one or more rooms |
| `messages/count` | Query | Number of matching messages |
| `contacts` | Query | Contacts / buddy list |
| `contacts/count` | Query | Number of matching contacts |
| `messages` | Insert | Send a message to a room |

---

## Query Parameters

Parameters are passed either as URI query params (ContentProvider) or inside the `params` extra as a URL-encoded string (Intent Bridge).

### Chats

| Param | Type | Description |
|---|---|---|
| `limit` | Int | Max rows to return |
| `offset` | Int | Row offset for paging |
| `isUnread` | 1/0 | Filter to unread chats only |
| `isArchived` | 1/0 | Filter to archived chats |
| `isLowPriority` | 1/0 | Filter to low-priority chats |
| `showInAllChats` | 1/0 | Include chats shown in All Chats view |
| `protocol` | String | Filter by network: `whatsapp`, `signal`, `telegram`, `imessage`, `instagramgo`, `slack`, `discord`, `sms` |
| `roomIds` | String | Comma-separated list of room IDs |

**Columns returned:** `roomId`, `title`, `messagePreview`, `senderEntityId`, `protocol`, `isMuted`, `unreadCount`, `timestamp`, `oneToOne`

### Messages

| Param | Type | Description |
|---|---|---|
| `roomIds` | String | Room ID(s) to fetch messages from |
| `query` | String | Full-text search within messages |
| `limit` | Int | Max rows |
| `offset` | Int | Row offset |
| `senderId` | String | Filter by sender |
| `contextBefore` | Int | Extra messages before each match |
| `contextAfter` | Int | Extra messages after each match |
| `openAtUnread` | true/false | Anchor results at the first unread message |

**Columns returned:** `roomId`, `originalId`, `senderContactId`, `timestamp`, `isSentByMe`, `isDeleted`, `type`, `text_content`, `reactions`, `displayName`, `is_search_match`, `paging_offset` (openAtUnread only), `last_read` (openAtUnread only)

### Contacts

| Param | Type | Description |
|---|---|---|
| `query` | String | Search contacts by name |
| `protocol` | String | Filter by network |
| `limit` | Int | Max rows |
| `senderIds` | String | Filter to specific sender IDs |

**Columns returned:** `id`, `roomIds`, `displayName`, `contactDisplayName`, `linkedContactId`, `itsMe`, `protocol`

### Insert (send message)

| Param | Required | Description |
|---|---|---|
| `roomId` | ✓ | Target room ID (e.g. `!abc123:beeper.com`) |
| `text` | ✓ | Message text to send |

**Result:** URI with `roomId` and `messageId` query params, or null on failure.

---

## Tasker Examples

### Get unread chats

```
[Action] Send Intent
  Action: com.beeperproxy.QUERY
  Package: com.beeperproxy
  Class: com.beeperproxy.BeeperIntentReceiver
  Extra: authToken = YOUR_TOKEN
  Extra: path = chats
  Extra: params = isUnread=1&limit=20&protocol=whatsapp

[Action] Wait for broadcast
  Action: com.beeperproxy.INTENT_RESULT
  → %success, %result, %rowCount
```

### Read one column across all rows

```
[Action] Send Intent
  ...
  Extra: columns = title,unreadCount

[Action] Wait for broadcast
  Action: com.beeperproxy.INTENT_RESULT
  Extra: col_title → %titles()     ← string array, one entry per row
  Extra: col_unreadCount → %counts()
```

### Send a message

```
[Action] Send Intent
  Action: com.beeperproxy.INSERT
  Package: com.beeperproxy
  Class: com.beeperproxy.BeeperIntentReceiver
  Extra: authToken = YOUR_TOKEN
  Extra: path = messages
  Extra: roomId = !room123:beeper.com
  Extra: text = Hello from Tasker!

[Action] Wait for broadcast
  Action: com.beeperproxy.INTENT_RESULT
  → %success, %result (contains messageId)
```

---

## Automate Examples

### Get recent messages from a room

```
[Send Intent]
  Action: com.beeperproxy.QUERY
  Package: com.beeperproxy
  Class: com.beeperproxy.BeeperIntentReceiver
  Extras:
    authToken → "YOUR_TOKEN"
    path → "messages"
    params → "roomIds=!abc:beeper.com&limit=10"

[Receive Broadcast]
  Action: com.beeperproxy.INTENT_RESULT
  → success, result, col_text_content (array)

[If] success == true
  → parse col_text_content array for message texts
```

### Send a message (params style)

```
[Send Intent]
  Action: com.beeperproxy.INSERT
  Extras:
    authToken → "YOUR_TOKEN"
    path → "messages"
    params → "roomId=!room:beeper.com&text=Hello+from+Automate"
```

---

## adb / Shell Examples

Useful for testing without Tasker/Automate. The Builder tab generates these for you.

```bash
# Query unread WhatsApp chats
adb shell am broadcast \
  -a com.beeperproxy.QUERY \
  -n com.beeperproxy/.BeeperIntentReceiver \
  --es authToken "YOUR_TOKEN" \
  --es path "chats" \
  --es params "isUnread=1&protocol=whatsapp&limit=10"

# Search messages
adb shell am broadcast \
  -a com.beeperproxy.QUERY \
  -n com.beeperproxy/.BeeperIntentReceiver \
  --es authToken "YOUR_TOKEN" \
  --es path "messages" \
  --es params "roomIds=!abc:beeper.com&query=hello&limit=5"

# Send a message
adb shell am broadcast \
  -a com.beeperproxy.INSERT \
  -n com.beeperproxy/.BeeperIntentReceiver \
  --es authToken "YOUR_TOKEN" \
  --es path "messages" \
  --es roomId "!abc:beeper.com" \
  --es text "Hello from adb"
```

---

## Security Notes

- **Store the token securely** — In Tasker, mark the variable as a password type. In Automate, keep it in a private variable. Do not hardcode it in flows you share.
- **Rotate the token periodically** — Tap Refresh Token in the app. Any automation using the old token will break immediately — update all your flows after rotating.
- **Use the app blacklist** — If only Tasker should have access, block every other launchable app from the Apps tab. The blacklist is enforced at the IPC layer using `Binder.getCallingPackage()`.
- **The ContentProvider is exported** — Any app on the device can attempt to query it. The auth token and blacklist are the only access controls, so treat the token like a password.

---

## Trademarks & Branding

This project uses its own name, icon, and visual identity — it does not use Beeper's logo, colors, or branding, and should not be mistaken for an official Beeper product. "Beeper" and any related marks belong to their respective owner.

## Project Docs

- [ARCHITECTURE.md](ARCHITECTURE.md) — component overview and data flow
- [SECURITY.md](SECURITY.md) — threat model and vulnerability reporting
- [LICENSE](LICENSE) — MIT

## References

- [Beeper Content Providers](https://developers.beeper.com/android/content-providers)
- [API Reference](https://developers.beeper.com/android/content-providers/api-reference)
- [Integration Guide](https://developers.beeper.com/android/content-providers/integration-guide)
