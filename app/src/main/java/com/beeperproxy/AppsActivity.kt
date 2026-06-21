package com.beeperproxy

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.beeperproxy.databinding.ActivityAppsBinding
import com.beeperproxy.databinding.ItemAppRowBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?
)

class AppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppsBinding
    private val rowBindings = mutableMapOf<String, ItemAppRowBinding>()
    private var apps: List<InstalledAppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav()

        binding.btnSelectAll.setOnClickListener {
            AppBlacklistManager.setAllBlocked(this, apps.map { it.packageName }, true)
            apps.forEach { rowBindings[it.packageName]?.cbAppBlocked?.isChecked = true }
        }

        binding.btnDeselectAll.setOnClickListener {
            AppBlacklistManager.setAllBlocked(this, apps.map { it.packageName }, false)
            apps.forEach { rowBindings[it.packageName]?.cbAppBlocked?.isChecked = false }
        }

        loadApps()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_apps
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_apps -> true
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_builder -> {
                    startActivity(Intent(this, BuilderActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadApps() {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val selfPackage = packageName

            val launchables = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL
            )

            val seen = mutableSetOf<String>()
            val result = mutableListOf<InstalledAppInfo>()

            for (resolveInfo in launchables) {
                val pkg = resolveInfo.activityInfo?.packageName ?: continue
                if (pkg == selfPackage || pkg in seen) continue
                seen.add(pkg)
                try {
                    val appInfo: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                    result.add(InstalledAppInfo(pkg, label, icon))
                } catch (e: Exception) {
                    // Skip apps we can't resolve
                }
            }

            result.sortBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                apps = result
                renderRows()
            }
        }
    }

    private fun renderRows() {
        binding.appListContainer.removeAllViews()
        rowBindings.clear()

        val blocked = AppBlacklistManager.getBlockedPackages(this)
        val inflater = LayoutInflater.from(this)

        for (app in apps) {
            val rowBinding = ItemAppRowBinding.inflate(inflater, binding.appListContainer, false)
            rowBinding.tvAppName.text = app.label
            rowBinding.tvAppPackage.text = app.packageName
            rowBinding.ivAppIcon.setImageDrawable(app.icon)
            rowBinding.cbAppBlocked.isChecked = app.packageName in blocked

            rowBinding.root.setOnClickListener {
                val newState = !rowBinding.cbAppBlocked.isChecked
                rowBinding.cbAppBlocked.isChecked = newState
                AppBlacklistManager.setBlocked(this, app.packageName, newState)
            }

            rowBindings[app.packageName] = rowBinding
            binding.appListContainer.addView(rowBinding.root)
        }
    }
}
