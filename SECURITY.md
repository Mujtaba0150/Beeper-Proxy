# Security Policy

## Overview

Beeper Proxy holds Beeper's `READ_PERMISSION` and `SEND_PERMISSION` on your behalf and re-exposes that access to other apps on your device through an auth-token-protected `ContentProvider` and `BroadcastReceiver`. Because it widens access to your messages, it's worth understanding its threat model before installing it.

## Threat Model

**In scope / mitigated:**

- **Unauthorized apps calling the proxy.** Every call must include the auth token generated in the Home tab. The token is 256-bit, generated with `SecureRandom`, and stored in `EncryptedSharedPreferences` (AES-256-GCM).
- **A specific app you don't trust calling the proxy, even with a leaked token.** Use the Apps tab to blacklist it. The check uses `Binder.getCallingPackage()`, which reflects the real calling UID and cannot be spoofed by the caller.
- **A newly installed app immediately having access.** `PackageInstallReceiver` blacklists every newly installed app by default; you must explicitly unblock an app before it can use the proxy.

**Out of scope / not mitigated:**

- **A malicious app with root or the ability to read another app's encrypted storage.** `EncryptedSharedPreferences` protects against casual inspection (e.g. via `adb backup` or a file manager) but is not a defense against a compromised or rooted device.
- **Compromise of Beeper itself**, or of the `com.beeper.android` package. Beeper Proxy only forwards calls; it trusts Beeper's own provider to return correct data.
- **Physical access to an unlocked device.** Anyone with the Home tab open can read and copy the current auth token.
- **Leaking the token yourself.** Anything with the token can call the proxy as if it were an authorized automation. Treat it like a password — don't paste it into shared Tasker profiles, screenshots, or public bug reports.

## Recommendations

- Rotate the auth token (**Refresh Token** on the Home tab) periodically and after sharing your device or debugging output with anyone.
- Keep the Apps blacklist as restrictive as possible — only unblock the specific automation app you're using (e.g. Tasker).
- Don't hardcode the token in flows or scripts you share with others.
- Review the Apps tab after installing new automation-adjacent apps, since anything you unblock can read/send messages through the proxy.

## Reporting a Vulnerability

If you find a security issue in Beeper Proxy itself (not in Beeper's own service), please open a private security advisory on the GitHub repository (**Security → Advisories → Report a vulnerability**) rather than a public issue, so it can be fixed before details are public.

Please include:

- A description of the issue and its impact
- Steps to reproduce
- The app version / commit you tested against

This is a community project maintained on a best-effort basis; there's no guaranteed response time, but reports are taken seriously and fixes will be released as soon as practical.

## Not a Beeper Channel

This policy covers Beeper Proxy only. Do not report vulnerabilities in Beeper's own apps or services here — see Beeper's own security contact for that.
