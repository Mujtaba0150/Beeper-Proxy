package com.beeperproxy

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.beeperproxy.databinding.FragmentAppsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsFragment : Fragment() {

    companion object {
        const val TAG = "AppsFragment"
    }

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AppListAdapter
    private var apps: List<InstalledAppInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AppListAdapter(
            onToggle = { packageName, blocked ->
                AppBlacklistManager.setBlocked(requireContext(), packageName, blocked)
            }
        )

        binding.recyclerAppList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AppsFragment.adapter
            // Stable size means the RecyclerView won't re-measure itself when
            // the adapter content changes — keeps the fragment view light.
            setHasFixedSize(true)
        }

        binding.btnSelectAll.setOnClickListener {
            val pkgs = apps.map { it.packageName }
            AppBlacklistManager.setAllBlocked(requireContext(), pkgs, true)
            adapter.updateBlocked(pkgs.toSet())
        }

        binding.btnDeselectAll.setOnClickListener {
            AppBlacklistManager.setAllBlocked(requireContext(), apps.map { it.packageName }, false)
            adapter.updateBlocked(emptySet())
        }

        loadApps()
    }

    private fun loadApps() {
        binding.groupAppsLoading.visibility = View.VISIBLE
        binding.recyclerAppList.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val pm = requireContext().packageManager
            val selfPackage = requireContext().packageName

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
                } catch (e: Exception) { }
            }

            result.sortBy { it.label.lowercase() }

            val blocked = AppBlacklistManager.getBlockedPackages(requireContext())

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                apps = result

                // A single adapter.submitList() call replaces all the manual
                // view inflation + suppressLayout + postDelayed chunking.
                // RecyclerView only ever draws the rows visible on screen
                // (~10–15), so the fragment view stays lightweight during
                // animations regardless of how many apps are installed.
                adapter.submitList(result, blocked)

                binding.groupAppsLoading.visibility = View.GONE
                binding.recyclerAppList.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
