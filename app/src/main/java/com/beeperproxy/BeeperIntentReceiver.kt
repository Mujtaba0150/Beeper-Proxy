package com.beeperproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

/**
 * Receives intents from Tasker/Automate with format:
 *
 * Intent actions (use one, no action extra needed):
 *   - com.beeperproxy.QUERY  → query operation
 *   - com.beeperproxy.INSERT → insert operation
 *
 * Intent extras:
 *   - authToken (String): Required. Must match stored token.
 *   - path (String): Required. chats, messages, contacts, chats/count, etc.
 *   - params (String): Optional. URL-encoded query params (e.g., "limit=10&isUnread=1")
 *   - columns (String): Optional. Comma-separated list of columns to return (projection).
 *                        Leave blank to return every column. For QUERY only.
 *   - roomId (String): For INSERT. Room ID to send message to. Can also be passed inside `params`.
 *   - text (String): For INSERT. Message text. Can also be passed inside `params`.
 *     When using `params` for insert (Automate-style), set params="roomId=!id:server.com&text=hello".
 *     Dedicated extras take precedence over values inside `params`.
 *
 * Result broadcast sent back via:
 *   - Action: com.beeperproxy.INTENT_RESULT
 *   - Extras:
 *       - success (Boolean): Whether the operation succeeded
 *       - error (String): Error message if failed
 *       - result (String): JSON array of rows (for query) or JSON object (for insert) —
 *                           kept for clients that can parse JSON.
 *       - rowCount (Int): Number of rows returned (query only)
 *       - columns (ArrayList<String>): Names of the columns actually returned (query only)
 *       - col_<columnName> (ArrayList<String>): One string-array extra PER COLUMN, with one
 *           entry per row, in row order. This lets array-oriented automation tools (e.g.
 *           Automate by LlamaLabs, Tasker) pull a single column straight out of the
 *           broadcast as a variable/array instead of having to parse the "result" JSON blob.
 *
 * Usage from Tasker/Automate:
 *   Send Intent
 *   Package: com.beeperproxy
 *   Class: com.beeperproxy.BeeperIntentReceiver
 *   Action: com.beeperproxy.QUERY
 *   Extra: authToken=YOUR_TOKEN
 *   Extra: path=chats
 *   Extra: params=limit=10&isUnread=1
 *   Extra: columns=roomId,title,unreadCount
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

        val authToken = intent.getStringExtra("authToken")
        val path = intent.getStringExtra("path")
        val params = intent.getStringExtra("params") ?: ""
        val columnsExtra = intent.getStringExtra("columns") ?: ""

        // Validation
        if (authToken == null) {
            sendResult(context, false, error = "Missing authToken extra")
            return
        }

        if (!AuthTokenManager.validateToken(context, authToken)) {
            sendResult(context, false, error = "Invalid or expired authToken")
            return
        }

        if (path == null || path !in ALLOWED_PATHS) {
            sendResult(
                context,
                false,
                error = "Invalid or missing path. Allowed: ${ALLOWED_PATHS.joinToString()}"
            )
            return
        }

        when (intent.action) {
            "com.beeperproxy.QUERY" -> handleQuery(context, path, params, columnsExtra)
            "com.beeperproxy.INSERT" -> handleInsert(context, path, intent)
            else -> sendResult(context, false, error = "Unknown intent action: ${intent.action}")
        }
    }

    /** Parses a CSV "columns" extra into a clean projection array, or null for "all columns". */
    private fun parseProjection(columnsExtra: String): Array<String>? {
        if (columnsExtra.isBlank()) return null
        val cols = columnsExtra.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (cols.isEmpty()) null else cols.toTypedArray()
    }

    private fun handleQuery(context: Context, path: String, params: String, columnsExtra: String) {
        try {
            val beeperUri = buildUri(path, params)
            val projection = parseProjection(columnsExtra)

            val cursor = context.contentResolver.query(beeperUri, projection, null, null, null)
            if (cursor == null) {
                sendResult(context, false, error = "ContentProvider returned null cursor")
                return
            }

            cursor.use { c ->
                val columnNames = c.columnNames
                val rows = mutableListOf<Map<String, Any?>>()
                // Parallel column -> values arrays, preserving row order, for array-style consumers.
                val columnValues = columnNames.associateWith { mutableListOf<String>() }

                while (c.moveToNext()) {
                    val row = mutableMapOf<String, Any?>()
                    for (colName in columnNames) {
                        val idx = c.getColumnIndex(colName)
                        val value: Any? = when (c.getType(idx)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> null
                            android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(idx)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(idx)
                            android.database.Cursor.FIELD_TYPE_STRING -> c.getString(idx)
                            android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(idx)
                            else -> null
                        }
                        row[colName] = value
                        columnValues[colName]?.add(value?.toString() ?: "")
                    }
                    rows.add(row)
                }

                val json = com.beeperproxy.JsonHelper.toJsonArray(rows)
                sendResult(
                    context,
                    success = true,
                    result = json,
                    rowCount = rows.size,
                    columns = columnNames.toList(),
                    columnValues = columnValues
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query failed", e)
            sendResult(context, false, error = e.message ?: "Unknown error")
        }
    }

    private fun handleInsert(context: Context, path: String, intent: Intent) {
        try {
            if (path != "messages") {
                sendResult(context, false, error = "Only messages can be inserted")
                return
            }

            val params = intent.getStringExtra("params") ?: ""

            // roomId and text can come either as dedicated extras OR inside the params string.
            // Dedicated extras take precedence; params string is the fallback (Automate-style).
            val paramsUri = if (params.isNotEmpty()) {
                android.net.Uri.parse("content://dummy?$params")
            } else null

            val roomId = intent.getStringExtra("roomId")
                ?: paramsUri?.getQueryParameter("roomId")
            val text = intent.getStringExtra("text")
                ?: paramsUri?.getQueryParameter("text")

            if (roomId == null || text == null) {
                sendResult(
                    context, false,
                    error = "Missing roomId or text. Provide as extras or inside the params string (e.g. params=\"roomId=!id:server.com&text=hello\")"
                )
                return
            }

            // Merge any extra params from the params string into the URI (excluding roomId/text
            // which are already handled above to avoid duplicates).
            val uriBuilder = android.net.Uri.Builder()
                .scheme("content")
                .authority(BEEPER_AUTHORITY)
                .appendEncodedPath(path)
                .appendQueryParameter("roomId", roomId)
                .appendQueryParameter("text", text)

            paramsUri?.queryParameterNames
                ?.filter { it != "roomId" && it != "text" }
                ?.forEach { key ->
                    val value = paramsUri.getQueryParameter(key)
                    if (value != null) uriBuilder.appendQueryParameter(key, value)
                }

            val resultUri = context.contentResolver.insert(uriBuilder.build(), null)
            if (resultUri != null) {
                val messageId = resultUri.getQueryParameter("messageId") ?: "unknown"
                val result = mapOf("messageId" to messageId, "roomId" to roomId)
                val json = com.beeperproxy.JsonHelper.toJson(result)
                sendResult(context, true, result = json)
            } else {
                sendResult(context, false, error = "Insert failed (null result)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Insert failed", e)
            sendResult(context, false, error = e.message ?: "Unknown error")
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
        result: String? = null,
        error: String? = null,
        rowCount: Int? = null,
        columns: List<String>? = null,
        columnValues: Map<String, List<String>>? = null
    ) {
        val resultIntent = Intent(RESULT_ACTION).apply {
            putExtra("success", success)
            if (result != null) putExtra("result", result)
            if (error != null) putExtra("error", error)
            if (rowCount != null) putExtra("rowCount", rowCount)
            if (columns != null) putStringArrayListExtra("columns", ArrayList(columns))
            columnValues?.forEach { (colName, values) ->
                putStringArrayListExtra("col_$colName", ArrayList(values))
            }
        }
        context.sendBroadcast(resultIntent)
    }
}
