package org.bdcloud.clash.ui.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.bdcloud.clash.R
import org.bdcloud.clash.api.ApiClient
import org.bdcloud.clash.api.LoginRequest
import org.bdcloud.clash.ui.main.MainActivity
import org.bdcloud.clash.ui.BlockedActivity
import org.bdcloud.clash.util.SecurityChecker
import org.bdcloud.clash.util.TokenManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.ProgressBar
import android.widget.TextView

class LoginActivity : AppCompatActivity() {

    private lateinit var editEmail: TextInputEditText
    private lateinit var editPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnCreateAccount: MaterialButton
    private lateinit var textError: TextView
    private lateinit var progressLogin: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security check FIRST — block rooted/emulator devices
        val security = SecurityChecker.check(this)
        if (!security.isSecure) {
            val intent = Intent(this, BlockedActivity::class.java)
            intent.putExtra(BlockedActivity.EXTRA_REASON, security.reason)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // Check if already logged in
        if (TokenManager.isLoggedIn(this)) {
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_login)

        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        textError = findViewById(R.id.textError)
        progressLogin = findViewById(R.id.progressLogin)

        btnLogin.setOnClickListener { performLogin() }
        btnCreateAccount.setOnClickListener { openSignup() }
    }

    private fun performLogin() {
        val email = editEmail.text?.toString()?.trim() ?: ""
        val password = editPassword.text?.toString() ?: ""

        if (email.length < 4) {
            showError("Please enter a valid email")
            return
        }
        if (password.isEmpty()) {
            showError("Please enter your password")
            return
        }

        setLoading(true)
        hideError()

        lifecycleScope.launch {
            try {
                val response = ApiClient.service.login(LoginRequest(email, password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && !body.token.isNullOrBlank()) {
                        // Save token and user info
                        TokenManager.saveToken(this@LoginActivity, body.token!!)
                        body.user?.let { user ->
                            TokenManager.saveUserInfo(
                                this@LoginActivity,
                                user.email,
                                user.role,
                                user.subscriptionStatus,
                                user.subscriptionExpires
                            )
                        }
                        navigateToMain()
                    } else {
                        showError(body?.error?.message ?: "Login failed")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val message = try {
                        val parsed = ApiClient.moshi.adapter(
                            org.bdcloud.clash.api.LoginResponse::class.java
                        ).fromJson(errorBody ?: "")
                        parsed?.error?.message ?: "Login failed (${response.code()})"
                    } catch (e: Exception) {
                        "Login failed (${response.code()})"
                    }
                    showError(message)
                }
            } catch (e: Exception) {
                showError(e.message ?: "Network error")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun openSignup() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://new.bdcloud.eu.org/software/signup")))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open signup page", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        btnLogin.text = if (loading) getString(R.string.signing_in) else getString(R.string.sign_in)
        editEmail.isEnabled = !loading
        editPassword.isEnabled = !loading
    }

    private fun showError(message: String) {
        textError.text = message
        textError.visibility = View.VISIBLE
    }

    private fun hideError() {
        textError.visibility = View.GONE
    }
}
