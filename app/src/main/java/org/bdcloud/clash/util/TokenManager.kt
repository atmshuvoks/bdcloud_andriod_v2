package org.bdcloud.clash.util

import android.content.Context

object TokenManager {

    private const val PREFS_NAME = "bdcloud_auth"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_ROLE = "user_role"
    private const val KEY_SUB_STATUS = "subscription_status"
    private const val KEY_SUB_EXPIRES = "subscription_expires"

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun getToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
    }

    fun saveUserInfo(
        context: Context,
        email: String,
        role: String,
        subscriptionStatus: String?,
        subscriptionExpires: String?
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_ROLE, role)
            .putString(KEY_SUB_STATUS, subscriptionStatus ?: "none")
            .putString(KEY_SUB_EXPIRES, subscriptionExpires)
            .apply()
    }

    fun getEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)
    }

    fun getSubscriptionStatus(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUB_STATUS, "none") ?: "none"
    }

    fun getSubscriptionExpires(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUB_EXPIRES, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        return !getToken(context).isNullOrBlank()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
