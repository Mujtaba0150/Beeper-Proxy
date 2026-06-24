package com.beeperproxy

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.beeperproxy.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Tab order matches bottom_nav_menu item order — drives slide direction
    private val tabOrder = listOf(R.id.nav_home, R.id.nav_builder, R.id.nav_apps)
    private var currentTabIndex = 0

    // All three kept alive after first creation so revisiting a tab is instant
    private val homeFragment    by lazy { HomeFragment() }
    private val builderFragment by lazy { BuilderFragment() }
    private val appsFragment    by lazy { AppsFragment() }

    private val allFragments get() = listOf(homeFragment, builderFragment, appsFragment)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            // Add all three up-front; only home is visible
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, homeFragment,    HomeFragment.TAG)
                .add(R.id.fragment_container, builderFragment, BuilderFragment.TAG)
                .add(R.id.fragment_container, appsFragment,    AppsFragment.TAG)
                .hide(builderFragment)
                .hide(appsFragment)
                .commitNow()
        }

        binding.bottomNav.selectedItemId = R.id.nav_home

        binding.bottomNav.setOnItemSelectedListener { item ->
            val newIndex = tabOrder.indexOf(item.itemId)
            if (newIndex < 0 || newIndex == currentTabIndex) return@setOnItemSelectedListener true

            val goingRight = newIndex > currentTabIndex
            val prevIndex  = currentTabIndex
            currentTabIndex = newIndex

            val enterAnim = if (goingRight) R.anim.slide_in_right else R.anim.slide_in_left
            val exitAnim  = if (goingRight) R.anim.slide_out_left  else R.anim.slide_out_right

            val incoming: Fragment = allFragments[newIndex]
            val outgoing: Fragment = allFragments[prevIndex]

            val transaction = supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)

            // Hide already-hidden middle fragments with no animation
            allFragments.forEachIndexed { i, frag ->
                if (i != prevIndex && i != newIndex) {
                    transaction.hide(frag)   // no-op if already hidden, no anim
                }
            }

            // Only the outgoing/incoming pair gets the slide
            transaction
                .setCustomAnimations(enterAnim, exitAnim)
                .hide(outgoing)
                .show(incoming)
                .commit()

            true
        }
    }
}
