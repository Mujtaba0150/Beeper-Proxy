# Beeper Proxy

An Android app that acts as a **secure proxy** in front of Beeper's Content Provider APIs. It:

- Requests the required `READ_PERMISSION` and `SEND_PERMISSION` Beeper runtime permissions
- Exposes a **local Content Provider** at `com.beeperproxy.provider` that forwards queries to `com.beeper.api`
- Exposes an **Intent Receiver** at `com.beeperproxy.BeeperIntentReceiver` that forwards intents to Beeper (better for Tasker/Automate)
- Protects all access with an auto-generated **auth token** (stored in EncryptedSharedPreferences)
- Provides a minimal UI to view, copy, and refresh the token

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
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

---

## How it works

### Permissions

The app declares and requests these Beeper custom permissions at runtime:

```xml
<uses-permission android:name="com.beeper.android.permission.READ_PERMISSION" />
<uses-permission android:name="com.beeper.android.permission.SEND_PERMISSION" />
```

### Auth Token

On first launch a cryptographically random 256-bit token is generated and stored in `EncryptedSharedPreferences`. You can refresh it from the UI at any time. Every caller must pass it as a parameter.

---

## Usage: Intent Bridge (Recommended for Tasker/Automate)

The **Intent approach is cleaner** for external apps. Send a broadcast intent to `com.beeperproxy.BeeperIntentReceiver` and get a result broadcast back.

### Intent Format

**Send to:** `com.beeperproxy.BeeperIntentReceiver`

**Extras:**

| Extra | Required | Type | Description |
|-------|----------|------|-------------|
| `authToken` | ✓ | String | Your token from the proxy app |
| `action` | ✓ | String | `query` or `insert` |
| `path` | ✓ | String | `chats`, `messages`, `contacts`, `chats/count`, `messages/count`, `contacts/count` |
| `params` | ✗ | String | URL-encoded query params (e.g., `limit=10&isUnread=1`) |
| `roomId` | ✗ | String | For insert only. Room ID to send message to |
| `text` | ✗ | String | For insert only. Message text |

**Result broadcast:**

- **Action:** `com.beeperproxy.INTENT_RESULT`
- **Extras:**
  - `success` (Boolean): Whether the operation succeeded
  - `result` (String): JSON array of rows (for query) or `{"messageId": "...", "roomId": "..."}` (for insert)
  - `error` (String): Error message if failed

---

## Tasker Examples

### Get unread chats

```
Task: Get Unread Chats

[Action] Variable Set
  %token = YOUR_TOKEN_HERE

[Action] Send Intent
  Action: com.beeperproxy.QUERY
  Package: com.beeperproxy
  Class: com.beeperproxy.BeeperIntentReceiver
  Extra: authToken = %token
  Extra: action = query
  Extra: path = chats
  Extra: params = isUnread=1&limit=10

[Action] Wait
  MS: 1000

[Action] Broadcast Receiver Event
  Action: com.beeperproxy.INTENT_RESULT
  Variable: %result
  
  [When Success = True]
  [Action] Say
    %result
```

### Send a message

```
Task: Send Message via Beeper

[Action] Variable Set
  %token = YOUR_TOKEN_HERE
  %roomId = !room123:server.com
  %message = Hello from Tasker!

[Action] Send Intent
  Action: com.beeperproxy.INSERT
  Package: com.beeperproxy
  Class: com.beeperproxy.BeeperIntentReceiver
  Extra: authToken = %token
  Extra: action = insert
  Extra: path = messages
  Extra: roomId = %roomId
  Extra: text = %message

[Action] Wait
  MS: 1000

[Action] Broadcast Receiver Event
  Action: com.beeperproxy.INTENT_RESULT
  Variable: %result
  
  [When Success = True]
  [Action] Toast
    Message sent!
  
  [When Success = False]
  [Action] Toast
    Failed: %error
```

### Parse JSON results

```
Task: List Chats and Show Titles

[Action] Send Intent
  ... (query chats as above)

[Action] JavaScript
  const result = JSON.parse('%result');
  const titles = result.map(chat => chat.title).join('\n');
  task.setLocal('titles', titles);

[Action] Say
  %titles
```

---

## Automate by llama Labs Examples

### Get unread chats

```
[START]
  ↓
[BLOCK: Variable Set]
  token = "YOUR_TOKEN_HERE"
  ↓
[BLOCK: Send Intent]
  Action: com.beeperproxy.QUERY
  Package: com.beeperproxy
  Class: com.beeperproxy.BeeperIntentReceiver
  Extras:
    authToken = {token}
    action = "query"
    path = "chats"
    params = "isUnread=1&limit=10"
  Result variable: %intent_result
  ↓
[BLOCK: Get Intent Extra]
  Variable: %intent_result
  Extra: success → %success
  Extra: result → %result
  ↓
[BLOCK: If] %success == true
  ↓
  [BLOCK: JavaScript]
    const chats = JSON.parse('%result');
    // Now iterate or display
  ↓
[END]
```

### Send a message

```
[START]
  ↓
[BLOCK: Variable Set]
  token = "YOUR_TOKEN_HERE"
  roomId = "!room:server.com"
  text = "Hello from Automate!"
  ↓
[BLOCK: Send Intent]
  Action: com.beeperproxy.INSERT
  Package: com.beeperproxy
  Class: com.beeperproxy.BeeperIntentReceiver
  Extras:
    authToken = {token}
    action = "insert"
    path = "messages"
    roomId = {roomId}
    text = {text}
  Result variable: %response
  ↓
[BLOCK: Get Intent Extra]
  Variable: %response
  Extra: success → %ok
  Extra: error → %err
  ↓
[BLOCK: If] %ok == true
  YES → [Dialog] Message sent!
  NO → [Dialog] Error: {%err}
  ↓
[END]
```

---

## Usage: ContentProvider (Advanced)

If you prefer the ContentProvider approach (for other apps), the proxy is also available at `com.beeperproxy.provider`:

```
content://com.beeperproxy.provider/chats?authToken=TOKEN&limit=50
```

See the [original README](#) for details.

---

## Supported Query Parameters

See the [Beeper API Reference](https://developers.beeper.com/android/content-providers/api-reference):

- **chats**: `limit`, `isUnread`, `isArchived`, `isLowPriority`, `protocol`, `roomIds`
- **messages**: `roomIds`, `query`, `limit`, `contextBefore`, `contextAfter`, `openAtUnread`
- **contacts**: `query`, `protocol`, `limit`

Everything gets forwarded verbatim to Beeper's real API, so you get all the power of the official Content Provider but protected by the auth token.

---

## Security Notes

- **Store the token securely** — Don't hardcode it. Read it from a config file or store it in Tasker variables marked as "password"
- **Rotate the token periodically** — Use the "Refresh Token" button in the Beeper Proxy app UI
- **Limit access** — Only allow Tasker/Automate to send intents (restrict via permissions if possible)

---

## References

- [Beeper Content Providers](https://developers.beeper.com/android/content-providers)
- [API Reference](https://developers.beeper.com/android/content-providers/api-reference)
- [Integration Guide](https://developers.beeper.com/android/content-providers/integration-guide)

