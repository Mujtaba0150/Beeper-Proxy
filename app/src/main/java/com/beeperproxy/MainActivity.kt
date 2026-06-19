package com.beeperproxy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.beeperproxy.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val hasRead = results["com.beeper.android.permission.READ_PERMISSION"] == true
        val hasSend = results["com.beeper.android.permission.SEND_PERMISSION"] == true
        updatePermissionStatus(hasRead, hasSend)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
        checkAndRequestPermissions()
    }

    private fun setupUi() {
        // Show the current token on launch
        val token = AuthTokenManager.getOrCreateToken(this)
        binding.tvAuthToken.text = token

        binding.btnRefreshToken.setOnClickListener {
            val newToken = AuthTokenManager.refreshToken(this)
            binding.tvAuthToken.text = newToken
            Toast.makeText(this, "Token refreshed", Toast.LENGTH_SHORT).show()
        }

        binding.btnCopyToken.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Beeper Proxy Auth Token", binding.tvAuthToken.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnRequestPermissions.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.btnTestChats.setOnClickListener { runTestQuery("chats", limit = 5) }
        binding.btnTestMessages.setOnClickListener { runTestQuery("messages", limit = 5) }
        binding.btnTestContacts.setOnClickListener { runTestQuery("contacts", limit = 5) }
    }

    /**
     * Queries the proxy provider directly (no ADB, no shell quoting issues).
     * Uses Uri.Builder so params are always correctly encoded.
     */
    private fun runTestQuery(path: String, limit: Int = 5) {
        val token = AuthTokenManager.getOrCreateToken(this)

        val uri = Uri.Builder()
            .scheme("content")
            .authority("com.beeperproxy.provider")
            .appendPath(path)
            .appendQueryParameter("authToken", token)
            .appendQueryParameter("limit", limit.toString())
            .build()

        Log.d("BeeperProxy", "Test query URI: $uri")
        binding.tvTestResult.text = "Querying $path…"

        try {
            contentResolver.query(uri, null, null, null, null).use { cursor ->
                if (cursor == null) {
                    val msg = "cursor = null\n\nBeeper returned null — check logcat for BeeperProxy tag."
                    binding.tvTestResult.text = msg
                    Log.e("BeeperProxy", "Test query returned null cursor for $path")
                    return
                }

                val rowCount = cursor.count
                val columns = cursor.columnNames.toList()
                Log.d("BeeperProxy", "Test [$path] rows=$rowCount columns=$columns")

                if (rowCount == 0) {
                    binding.tvTestResult.text =
                        "✓ Cursor OK — 0 rows returned\nColumns: ${columns.joinToString()}"
                    return
                }

                // Show first row as key=value pairs
                val sb = StringBuilder("✓ $rowCount row(s) — showing first:\n\n")
                cursor.moveToFirst()
                columns.forEachIndexed { i, col ->
                    val value = try { cursor.getString(i) } catch (e: Exception) { "<error>" }
                    sb.append("$col = $value\n")
                }
                binding.tvTestResult.text = sb.toString()
            }
        } catch (e: Exception) {
            val msg = "Exception: ${e.javaClass.simpleName}\n${e.message}"
            binding.tvTestResult.text = msg
            Log.e("BeeperProxy", "Test query exception for $path", e)
        }
    }

    private fun checkAndRequestPermissions() {
        val hasRead = ContextCompat.checkSelfPermission(
            this, "com.beeper.android.permission.READ_PERMISSION"
        ) == PackageManager.PERMISSION_GRANTED

        val hasSend = ContextCompat.checkSelfPermission(
            this, "com.beeper.android.permission.SEND_PERMISSION"
        ) == PackageManager.PERMISSION_GRANTED

        updatePermissionStatus(hasRead, hasSend)

        if (!hasRead || !hasSend) {
            requestPermissions.launch(
                arrayOf(
                    "com.beeper.android.permission.READ_PERMISSION",
                    "com.beeper.android.permission.SEND_PERMISSION"
                )
            )
        }
    }

    private fun updatePermissionStatus(hasRead: Boolean, hasSend: Boolean) {
        val readStatus = if (hasRead) "✓ Granted" else "✗ Denied"
        val sendStatus = if (hasSend) "✓ Granted" else "✗ Denied"
        binding.tvPermissionRead.text = "READ_PERMISSION: $readStatus"
        binding.tvPermissionSend.text = "SEND_PERMISSION: $sendStatus"

        val allGranted = hasRead && hasSend
        binding.btnRequestPermissions.isEnabled = !allGranted
        binding.tvPermissionHint.text = if (allGranted) {
            "All permissions granted. Proxy is active."
        } else {
            "Tap below to grant Beeper permissions."
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
