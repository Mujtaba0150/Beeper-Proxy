package com.beeperproxy

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri

/**
 * A proxy ContentProvider that sits in front of Beeper's Content Provider.
 *
 * Callers must include an `authToken` query parameter matching the stored token.
 * The token is generated on first run and can be refreshed from MainActivity.
 *
 * Usage:
 *   content://com.beeperproxy.provider/chats?authToken=<TOKEN>&limit=50
 *   content://com.beeperproxy.provider/messages?authToken=<TOKEN>&query=hello
 *   content://com.beeperproxy.provider/contacts?authToken=<TOKEN>
 *
 * All parameters beyond `authToken` are forwarded verbatim to:
 *   content://com.beeper.api/<path>?<params>
 */
class BeeperProxyProvider : ContentProvider() {

    companion object {
        private const val BEEPER_AUTHORITY = "com.beeper.api"
        private const val AUTH_TOKEN_PARAM = "authToken"

        private val ALLOWED_PATHS = setOf(
            "chats",
            "messages",
            "contacts",
            "chats/count",
            "messages/count",
            "contacts/count"
        )
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return errorCursor("No context")

        val token = uri.getQueryParameter(AUTH_TOKEN_PARAM)
            ?: return errorCursor("Missing authToken parameter")

        if (!AuthTokenManager.validateToken(ctx, token)) {
            return errorCursor("Invalid or expired authToken")
        }

        val path = uri.path?.trimStart('/')
            ?: return errorCursor("No path specified")

        if (path !in ALLOWED_PATHS) {
            return errorCursor(
                "Unknown path: $path. Allowed: ${ALLOWED_PATHS.joinToString()}"
            )
        }

        val beeperUriBuilder = StringBuilder("content://$BEEPER_AUTHORITY/$path")

        val params = uri.queryParameterNames
            .filter { it != AUTH_TOKEN_PARAM }
            .mapNotNull { key ->
                uri.getQueryParameter(key)?.let { value ->
                    "$key=$value"
                }
            }

        if (params.isNotEmpty()) {
            beeperUriBuilder.append("?").append(params.joinToString("&"))
        }

        val beeperUri = beeperUriBuilder.toString().toUri()

        Log.d("BeeperProxy", "Forwarding to: $beeperUri")

        return try {
            val cursor = ctx.contentResolver.query(
                beeperUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            Log.d("BeeperProxy", "Cursor: $cursor")
            Log.d("BeeperProxy", "Cursor count: ${cursor?.count}")
            Log.d(
                "BeeperProxy",
                "Cursor columns: ${cursor?.columnNames?.joinToString()}"
            )

            cursor
        } catch (e: Exception) {
            Log.e("BeeperProxy", "Beeper query threw exception", e)
            errorCursor("Beeper query failed: ${e.message}")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val ctx = context ?: return null

        val token = uri.getQueryParameter(AUTH_TOKEN_PARAM)
            ?: return null

        if (!AuthTokenManager.validateToken(ctx, token)) {
            return null
        }

        val path = uri.path?.trimStart('/')
            ?: return null

        if (path != "messages") {
            return null
        }

        val beeperUriBuilder = StringBuilder("content://$BEEPER_AUTHORITY/$path")

        val params = uri.queryParameterNames
            .filter { it != AUTH_TOKEN_PARAM }
            .mapNotNull { key ->
                uri.getQueryParameter(key)?.let { value ->
                    "$key=$value"
                }
            }

        if (params.isNotEmpty()) {
            beeperUriBuilder.append("?").append(params.joinToString("&"))
        }

        return try {
            ctx.contentResolver.insert(
                beeperUriBuilder.toString().toUri(),
                values
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    override fun getType(uri: Uri): String? = null

    private fun errorCursor(message: String): Cursor {
        val cursor = MatrixCursor(arrayOf("error"))
        cursor.addRow(arrayOf(message))
        return cursor
    }
}