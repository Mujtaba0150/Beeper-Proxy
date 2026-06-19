package com.beeperproxy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
