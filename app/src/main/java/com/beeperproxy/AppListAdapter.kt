package com.beeperproxy

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beeperproxy.databinding.ItemAppRowBinding

class AppListAdapter(
    private var apps: List<InstalledAppInfo> = emptyList(),
    private var blockedPackages: Set<String> = emptySet(),
    private val onToggle: (packageName: String, blocked: Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAppRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val binding = holder.binding

        binding.tvAppName.text = app.label
        binding.tvAppPackage.text = app.packageName
        binding.ivAppIcon.setImageDrawable(app.icon)
        binding.cbAppBlocked.isChecked = app.packageName in blockedPackages

        binding.root.setOnClickListener {
            val newState = !binding.cbAppBlocked.isChecked
            binding.cbAppBlocked.isChecked = newState
            onToggle(app.packageName, newState)
        }
    }

    override fun getItemCount(): Int = apps.size

    fun submitList(newApps: List<InstalledAppInfo>, newBlocked: Set<String>) {
        apps = newApps
        blockedPackages = newBlocked
        notifyDataSetChanged()
    }

    fun updateBlocked(newBlocked: Set<String>) {
        blockedPackages = newBlocked
        notifyItemRangeChanged(0, apps.size)
    }
}
