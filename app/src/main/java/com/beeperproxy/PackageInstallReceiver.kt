package com.beeperproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Listens for newly installed apps and blacklists them by default, so a
 * freshly installed app can't query the Beeper proxy until the user
 * explicitly unblocks it from the Apps screen.
 */
class PackageInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return

        // EXTRA_REPLACING is true for app updates, not fresh installs -
        // don't re-blacklist an app the user has already allowed.
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return

        AppBlacklistManager.setBlocked(context, packageName, true)
    }
}
