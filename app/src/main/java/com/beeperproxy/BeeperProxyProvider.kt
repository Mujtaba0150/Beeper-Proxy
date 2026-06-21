package com.beeperproxy

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
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
        private val ALLOWED_PATHS = setOf("chats", "messages", "contacts",
            "chats/count", "messages/count", "contacts/count")
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

        if (AppBlacklistManager.isBlocked(ctx, callingPackage)) {
            Log.w("BeeperProxy", "Rejected query from blacklisted package: $callingPackage")
            return errorCursor("Caller package '$callingPackage' is blacklisted")
        }

        val path = uri.path?.trimStart('/') ?: return errorCursor("No path specified")

        if (path !in ALLOWED_PATHS) {
            return errorCursor("Unknown path: $path. Allowed: ${ALLOWED_PATHS.joinToString()}")
        }

        // Rebuild URI for Beeper, stripping our authToken param
        val beeperUriBuilder = StringBuilder("content://$BEEPER_AUTHORITY/$path")
        val params = uri.queryParameterNames
            .filter { it != AUTH_TOKEN_PARAM }
            .mapNotNull { key ->
                val value = uri.getQueryParameter(key)
                if (value != null) "$key=$value" else null
            }

        if (params.isNotEmpty()) {
            beeperUriBuilder.append("?").append(params.joinToString("&"))
        }

        val beeperUri = beeperUriBuilder.toString().toUri()


        // Log everything we're about to send to Beeper
        Log.d("BeeperProxy", "=== Forwarding to Beeper ===")
        Log.d("BeeperProxy", "URI: $beeperUri")
        Log.d("BeeperProxy", "Projection: ${projection?.joinToString() ?: "null (all columns)"}")
        Log.d("BeeperProxy", "Selection: $selection")
        Log.d("BeeperProxy", "SelectionArgs: ${selectionArgs?.joinToString() ?: "null"}")
        Log.d("BeeperProxy", "SortOrder: $sortOrder")
        Log.d("BeeperProxy", "Caller UID: ${Binder.getCallingUid()}")
        Log.d("BeeperProxy", "Our UID: ${android.os.Process.myUid()}")

        // Log Beeper provider info for diagnostics (non-blocking)
        val pm = ctx.packageManager
        try {
            val info = pm.getPackageInfo("com.beeper.android", 0)
            Log.d("BeeperProxy", "Beeper version: ${info.versionName} (${info.longVersionCode})")
            val providerInfo = pm.resolveContentProvider("com.beeper.api", 0)
            Log.d("BeeperProxy", "Provider exported: ${providerInfo?.exported}, readPermission: ${providerInfo?.readPermission}")
        } catch (e: Exception) {
            Log.w("BeeperProxy", "Could not resolve Beeper package info (may be invisible): ${e.message}")
        }

        return try {
            Log.d("BeeperProxy", "Calling contentResolver.query()...")
            // Always request all columns from Beeper — it ignores projection anyway.
            // We enforce the caller's projection manually below.
            val cursor = ctx.contentResolver.query(
                beeperUri,
                null,
                selection,
                selectionArgs,
                sortOrder
            )
            Log.d("BeeperProxy", "Query returned: cursor=$cursor")
            if (cursor != null) {
                Log.d("BeeperProxy", "  count=${cursor.count}")
                Log.d("BeeperProxy", "  columns=${cursor.columnNames.joinToString()}")
            } else {
                Log.e("BeeperProxy", "  NULL cursor — Beeper rejected or returned nothing")
                return null
            }

            // Post-filter: if the caller requested specific columns, build a MatrixCursor
            // with only those columns so tools like Automate see a clean single-column result.
            if (projection == null || projection.isEmpty()) {
                cursor
            } else {
                val validCols = projection.filter { it in cursor.columnNames }
                if (validCols.isEmpty()) return cursor
                val matrix = MatrixCursor(validCols.toTypedArray())
                while (cursor.moveToNext()) {
                    val row = validCols.map { col ->
                        cursor.getString(cursor.getColumnIndexOrThrow(col))
                    }.toTypedArray()
                    matrix.addRow(row)
                }
                cursor.close()
                matrix
            }
        } catch (e: Exception) {
            Log.e("BeeperProxy", "Exception calling Beeper: ${e.javaClass.name}: ${e.message}", e)
            errorCursor("Beeper query failed: ${e.message}")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val ctx = context ?: return null

        val token = uri.getQueryParameter(AUTH_TOKEN_PARAM) ?: return null
        if (!AuthTokenManager.validateToken(ctx, token)) return null

        if (AppBlacklistManager.isBlocked(ctx, callingPackage)) {
            Log.w("BeeperProxy", "Rejected insert from blacklisted package: $callingPackage")
            return null
        }

        val path = uri.path?.trimStart('/') ?: return null
        if (path != "messages") return null

        val beeperUriBuilder = StringBuilder("content://$BEEPER_AUTHORITY/$path")
        val params = uri.queryParameterNames
            .filter { it != AUTH_TOKEN_PARAM }
            .mapNotNull { key ->
                val value = uri.getQueryParameter(key)
                if (value != null) "$key=$value" else null
            }

        if (params.isNotEmpty()) {
            beeperUriBuilder.append("?").append(params.joinToString("&"))
        }

        return try {
            ctx.contentResolver.insert(beeperUriBuilder.toString().toUri(), values)
        } catch (e: Exception) {
            null
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun getType(uri: Uri): String? = null

    private fun errorCursor(message: String): Cursor {
        val cursor = MatrixCursor(arrayOf("error"))
        cursor.addRow(arrayOf(message))
        return cursor
    }
}
