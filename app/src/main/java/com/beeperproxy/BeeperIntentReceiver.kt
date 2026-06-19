package com.beeperproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

/**
 * Receives intents from Tasker/Automate with format:
 *
 * Intent extras:
 *   - authToken (String): Required. Must match stored token.
 *   - action (String): Required. One of: query, insert
 *   - path (String): Required. chats, messages, contacts, chats/count, etc.
 *   - params (String): Optional. URL-encoded query params (e.g., "limit=10&isUnread=1")
 *   - roomId (String): For insert. Room ID to send message to.
 *   - text (String): For insert. Message text.
 *
 * Result broadcast sent back via:
 *   - Action: com.beeperproxy.INTENT_RESULT
 *   - Extras:
 *       - success (Boolean): Whether the operation succeeded
 *       - result (String): JSON array of rows (for query) or messageId (for insert)
 *       - error (String): Error message if failed
 *
 * Usage from Tasker:
 *   Send Intent
 *   Package: com.beeperproxy
 *   Class: com.beeperproxy.BeeperIntentReceiver
 *   Action: com.beeperproxy.QUERY
 *   Extra: authToken=YOUR_TOKEN
 *   Extra: action=query
 *   Extra: path=chats
 *   Extra: params=limit=10&isUnread=1
 */
class BeeperIntentReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BeeperIntentReceiver"
        private const val RESULT_ACTION = "com.beeperproxy.INTENT_RESULT"
        private const val BEEPER_AUTHORITY = "com.beeper.api"
        private val ALLOWED_PATHS = setOf(
            "chats", "messages", "contacts",
            "chats/count", "messages/count", "contacts/count"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Intent received: ${intent.action}")

        val authToken = intent.getStringExtra("authToken")
        val action = intent.getStringExtra("action")
        val path = intent.getStringExtra("path")
        val params = intent.getStringExtra("params") ?: ""

        // Validation
        if (authToken == null) {
            sendResult(context, false, null, "Missing authToken extra")
            return
        }

        if (!AuthTokenManager.validateToken(context, authToken)) {
            sendResult(context, false, null, "Invalid or expired authToken")
            return
        }

        if (action == null) {
            sendResult(context, false, null, "Missing action extra (query or insert)")
            return
        }

        if (path == null || path !in ALLOWED_PATHS) {
            sendResult(
                context,
                false,
                null,
                "Invalid or missing path. Allowed: ${ALLOWED_PATHS.joinToString()}"
            )
            return
        }

        when (action.lowercase()) {
            "query" -> handleQuery(context, path, params)
            "insert" -> handleInsert(context, path, intent)
            else -> sendResult(context, false, null, "Unknown action: $action")
        }
    }

    private fun handleQuery(context: Context, path: String, params: String) {
        return try {
            // Build Beeper URI
            val beeperUri = buildUri(path, params)
            Log.d(TAG, "Querying: $beeperUri")

            val cursor = context.contentResolver.query(beeperUri, null, null, null, null)
            if (cursor == null) {
                sendResult(context, false, null, "ContentProvider returned null cursor")
                return
            }

            cursor.use { c ->
                val columnNames = c.columnNames
                val rows = mutableListOf<Map<String, Any?>>()

                while (c.moveToNext()) {
                    val row = mutableMapOf<String, Any?>()
                    for (colName in columnNames) {
                        val idx = c.getColumnIndex(colName)
                        row[colName] = when (c.getType(idx)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> null
                            android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(idx)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(idx)
                            android.database.Cursor.FIELD_TYPE_STRING -> c.getString(idx)
                            android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(idx)
                            else -> null
                        }
                    }
                    rows.add(row)
                }

                // Convert to JSON
                val json = com.beeperproxy.JsonHelper.toJsonArray(rows)
                sendResult(context, true, json, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query failed", e)
            sendResult(context, false, null, e.message ?: "Unknown error")
        }
    }

    private fun handleInsert(context: Context, path: String, intent: Intent) {
        return try {
            if (path != "messages") {
                sendResult(context, false, null, "Only messages can be inserted")
                return
            }

            val roomId = intent.getStringExtra("roomId")
            val text = intent.getStringExtra("text")

            if (roomId == null || text == null) {
                sendResult(context, false, null, "Missing roomId or text extra for insert")
                return
            }

            // Build insert URI
            val encodedText = android.net.Uri.encode(text)
            val insertUri = buildUri("messages", "roomId=$roomId&text=$encodedText")
            Log.d(TAG, "Inserting to: $insertUri")

            val resultUri = context.contentResolver.insert(insertUri, null)
            if (resultUri != null) {
                val messageId = resultUri.getQueryParameter("messageId") ?: "unknown"
                val result = mapOf("messageId" to messageId, "roomId" to roomId)
                val json = com.beeperproxy.JsonHelper.toJson(result)
                sendResult(context, true, json, null)
            } else {
                sendResult(context, false, null, "Insert failed (null result)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Insert failed", e)
            sendResult(context, false, null, e.message ?: "Unknown error")
        }
    }

    private fun buildUri(path: String, params: String): android.net.Uri {
        val uri = "content://$BEEPER_AUTHORITY/$path"
        return if (params.isNotEmpty()) {
            "$uri?$params".toUri()
        } else {
            uri.toUri()
        }
    }

    private fun sendResult(
        context: Context,
        success: Boolean,
        result: String?,
        error: String?
    ) {
        val resultIntent = Intent(RESULT_ACTION).apply {
            putExtra("success", success)
            if (result != null) putExtra("result", result)
            if (error != null) putExtra("error", error)
        }
        context.sendBroadcast(resultIntent)
        Log.d(TAG, "Result sent: success=$success, error=$error")
    }
}
