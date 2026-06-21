package com.beeperproxy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.beeperproxy.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var queryJob: Job? = null
    private var selectedPath: String = "chats"

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
        setupBottomNav()
        checkAndRequestPermissions()
    }

    private fun setupUi() {
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

        // Test buttons — highlight selected and run query
        val testButtons = listOf(
            binding.btnTestChats to "chats",
            binding.btnTestMessages to "messages",
            binding.btnTestContacts to "contacts"
        )

        testButtons.forEach { (btn, path) ->
            btn.setOnClickListener {
                selectedPath = path
                testButtons.forEach { (b, _) ->
                    val selected = b == btn
                    b.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        getColor(if (selected) R.color.accent else R.color.btn_secondary)
                    )
                    b.setTextColor(getColor(if (selected) R.color.bg_dark else R.color.text_primary))
                }
                runTestQuery(path)
            }
        }

        // Trigger default selection highlight
        binding.btnTestChats.performClick()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_builder -> {
                    startActivity(Intent(this, BuilderActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_apps -> {
                    startActivity(Intent(this, AppsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }

    private fun runTestQuery(path: String, limit: Int = 10) {
        // Cancel any in-flight query
        queryJob?.cancel()

        val token = AuthTokenManager.getOrCreateToken(this)
        val uri = Uri.Builder()
            .scheme("content")
            .authority("com.beeperproxy.provider")
            .appendPath(path)
            .appendQueryParameter("authToken", token)
            .appendQueryParameter("limit", limit.toString())
            .build()

        binding.tvLoadingLabel.text = "Querying $path…"
        binding.groupTestLoading.visibility = View.VISIBLE
        binding.svTestResult.visibility = View.GONE
        setTestButtonsEnabled(false)

        queryJob = CoroutineScope(Dispatchers.IO).launch {
            val result = try {
                contentResolver.query(uri, null, null, null, null).use { cursor ->
                    if (cursor == null) {
                        "cursor = null\n\nBeeper returned null — check permissions."
                    } else {
                        val rowCount = cursor.count
                        val columns = cursor.columnNames.toList()

                        if (rowCount == 0) {
                            "✓ Cursor OK — 0 rows returned\nColumns: ${columns.joinToString()}"
                        } else {
                            val sb = StringBuilder("✓ $rowCount row(s) — columns: ${columns.joinToString()}\n")
                            sb.append("─".repeat(40)).append("\n")
                            var row = 0
                            while (cursor.moveToNext()) {
                                sb.append("\n── Row ${++row} ──\n")
                                columns.forEachIndexed { i, col ->
                                    val value = try {
                                        cursor.getString(i) ?: "<null>"
                                    } catch (e: Exception) {
                                        "<error>"
                                    }
                                    sb.append("$col = $value\n")
                                }
                            }
                            sb.toString()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BeeperProxy", "Test query exception", e)
                "Exception: ${e.javaClass.simpleName}\n${e.message}"
            }

            withContext(Dispatchers.Main) {
                binding.tvTestResult.text = result
                binding.groupTestLoading.visibility = View.GONE
                binding.svTestResult.visibility = View.VISIBLE
                setTestButtonsEnabled(true)
            }
        }
    }

    private fun setTestButtonsEnabled(enabled: Boolean) {
        binding.btnTestChats.isEnabled = enabled
        binding.btnTestMessages.isEnabled = enabled
        binding.btnTestContacts.isEnabled = enabled
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

    override fun onDestroy() {
        super.onDestroy()
        queryJob?.cancel()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
