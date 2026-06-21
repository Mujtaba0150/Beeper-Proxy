package com.beeperproxy

import android.content.Context

/**
 * Stores the set of app package names that are blacklisted from using the proxy.
 * Any query/insert call originating from a blacklisted package is rejected by
 * [BeeperProxyProvider] before it ever reaches Beeper.
 */
object AppBlacklistManager {

    private const val PREFS_FILE = "beeper_proxy_blacklist"
    private const val KEY_BLOCKED = "blocked_packages"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun getBlockedPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
    }

    fun isBlocked(context: Context, packageName: String?): Boolean {
        if (packageName == null) return false
        return packageName in getBlockedPackages(context)
    }

    fun setBlocked(context: Context, packageName: String, blocked: Boolean) {
        val current = getBlockedPackages(context).toMutableSet()
        if (blocked) current.add(packageName) else current.remove(packageName)
        prefs(context).edit().putStringSet(KEY_BLOCKED, current).apply()
    }

    fun setAllBlocked(context: Context, packageNames: Collection<String>, blocked: Boolean) {
        val current = getBlockedPackages(context).toMutableSet()
        if (blocked) current.addAll(packageNames) else current.removeAll(packageNames.toSet())
        prefs(context).edit().putStringSet(KEY_BLOCKED, current).apply()
    }
}
