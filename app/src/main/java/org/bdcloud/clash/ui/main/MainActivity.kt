package org.bdcloud.clash.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.bdcloud.clash.R
import org.bdcloud.clash.ui.login.LoginActivity
import org.bdcloud.clash.util.AppNotificationManager
import org.bdcloud.clash.util.AppUpdateManager
import org.bdcloud.clash.util.TokenManager

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!TokenManager.isLoggedIn(this)) {
            navigateToLogin()
            return
        }

        bottomNav = findViewById(R.id.bottomNav)

        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

        // Check for updates and notifications after UI loads
        Handler(Looper.getMainLooper()).postDelayed({
            AppUpdateManager.checkForUpdate(this)
            AppNotificationManager.checkNotifications(this)
        }, 2000)

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_dashboard -> DashboardFragment()
                R.id.nav_proxies -> ProxyListFragment()
                R.id.nav_pricing -> PricingFragment()
                R.id.nav_logs -> LogsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fragment_fade_in, R.anim.fragment_fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun navigateToLogin() {
        TokenManager.clear(this)
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
