package com.beeperproxy

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beeperproxy.databinding.ItemAppRowBinding

class AppListAdapter(
    private var apps: List<InstalledAppInfo> = emptyList(),
    private var blockedPackages: Set<String> = emptySet(),
    private var sortBlockedToTop: Boolean = false,
    private val onToggle: (packageName: String, blocked: Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    // The list actually shown in the RecyclerView. Kept separate from `apps`
    // so we can reorder (blocked-to-top) without losing the original,
    // alphabetically-sorted source list.
    private var displayApps: List<InstalledAppInfo> = apps

    init {
        recomputeDisplayList()
    }

    class ViewHolder(val binding: ItemAppRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = displayApps[position]
        val binding = holder.binding

        binding.tvAppName.text = app.label
        binding.tvAppPackage.text = app.packageName
        binding.ivAppIcon.setImageDrawable(app.icon)
        binding.cbAppBlocked.isChecked = app.packageName in blockedPackages

        binding.root.setOnClickListener {
            val newState = app.packageName !in blockedPackages
            binding.cbAppBlocked.isChecked = newState
            // Update the set the adapter itself keeps, not just the checkbox view,
            // so that when this row is recycled and rebound (e.g. after scrolling
            // away and back) onBindViewHolder reads the up-to-date blocked state.
            blockedPackages = if (newState) {
                blockedPackages + app.packageName
            } else {
                blockedPackages - app.packageName
            }
            onToggle(app.packageName, newState)

            if (sortBlockedToTop) {
                recomputeDisplayList()
                notifyDataSetChanged()
            }
        }
    }

    override fun getItemCount(): Int = displayApps.size

    fun submitList(newApps: List<InstalledAppInfo>, newBlocked: Set<String>) {
        apps = newApps
        blockedPackages = newBlocked
        recomputeDisplayList()
        notifyDataSetChanged()
    }

    fun updateBlocked(newBlocked: Set<String>) {
        blockedPackages = newBlocked
        recomputeDisplayList()
        notifyDataSetChanged()
    }

    /** Enables/disables showing blocked apps at the top of the list. */
    fun setSortBlockedToTop(enabled: Boolean) {
        if (sortBlockedToTop == enabled) return
        sortBlockedToTop = enabled
        recomputeDisplayList()
        notifyDataSetChanged()
    }

    private fun recomputeDisplayList() {
        displayApps = if (sortBlockedToTop) {
            // sortedByDescending is a stable sort, so alphabetical order within
            // the blocked group and within the unblocked group is preserved.
            apps.sortedByDescending { it.packageName in blockedPackages }
        } else {
            apps
        }
    }
}
