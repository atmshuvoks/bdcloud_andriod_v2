package org.bdcloud.clash.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.bdcloud.clash.R

/**
 * Shown when the app detects a rooted device, emulator,
 * or attached debugger. The user cannot proceed.
 */
class BlockedActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REASON = "security_reason"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        val reason = intent.getStringExtra(EXTRA_REASON)
            ?: "This device is not supported."

        findViewById<TextView>(R.id.textSecurityReason).text = reason
        findViewById<MaterialButton>(R.id.btnExit).setOnClickListener {
            finishAffinity()
            System.exit(0)
        }
    }

    // Prevent back navigation
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAffinity()
        System.exit(0)
    }
}
