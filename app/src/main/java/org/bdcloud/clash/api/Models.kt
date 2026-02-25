package org.bdcloud.clash.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Base response ──────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class BaseResponse(
    val success: Boolean,
    val error: ErrorBody? = null
)

@JsonClass(generateAdapter = true)
data class ErrorBody(
    val code: String? = null,
    val message: String? = null
)

// ── Auth ───────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val success: Boolean,
    val token: String? = null,
    val user: UserProfile? = null,
    val error: ErrorBody? = null
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: Int,
    val email: String,
    val role: String,
    @Json(name = "account_status") val accountStatus: String? = null,
    @Json(name = "subscription_status") val subscriptionStatus: String? = null,
    @Json(name = "subscription_expires") val subscriptionExpires: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class MeResponse(
    val success: Boolean,
    val user: UserProfile? = null,
    val error: ErrorBody? = null
)

@JsonClass(generateAdapter = true)
data class RefreshResponse(
    val success: Boolean,
    val token: String? = null,
    val error: ErrorBody? = null
)

// ── Proxy / Encryption ────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SessionKeyResponse(
    val success: Boolean,
    val sessionId: String? = null,
    val hint: String? = null,
    val keyData: String? = null,
    val expiresIn: Long? = null,
    val error: ErrorBody? = null
)

@JsonClass(generateAdapter = true)
data class SessionKeyInfo(
    val sessionId: String,
    val hint: String,
    val keyData: String,
    val expiresIn: Long
)

@JsonClass(generateAdapter = true)
data class EncryptedFragment(
    val iv: String,
    val data: String,
    val tag: String
)

@JsonClass(generateAdapter = true)
data class EncryptedPayload(
    val sessionId: String,
    val fragments: List<EncryptedFragment>,
    val ts: Long
)

@JsonClass(generateAdapter = true)
data class ProxiesResponse(
    val success: Boolean,
    val payload: EncryptedPayload? = null,
    val error: ErrorBody? = null
)

// Decrypted fragments
@JsonClass(generateAdapter = true)
data class Frag1Item(
    @Json(name = "_id") val id: Int,
    val n: String? = null,   // name
    val s: String? = null,   // server/host
    val l: String? = null    // location
)

@JsonClass(generateAdapter = true)
data class Frag2Item(
    @Json(name = "_id") val id: Int,
    val p: Int? = null,      // port
    val u: String? = null    // username (base64)
)

@JsonClass(generateAdapter = true)
data class Frag3Item(
    @Json(name = "_id") val id: Int,
    val k: String? = null,   // password (base64)
    @Json(name = "_w") val w: String? = null  // watermark
)

// Assembled proxy
data class DecryptedProxy(
    val id: Int,
    val name: String,
    val server: String,
    val port: Int,
    val username: String,
    val password: String,
    val location: String
)

// ── Payments ──────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class Plan(
    val id: Int,
    val code: String? = null,
    val name: String,
    @Json(name = "duration_days") val durationDays: Int,
    val price: Double,
    val currency: String = "BDT",
    val badge: String? = null
)

@JsonClass(generateAdapter = true)
data class PlansResponse(
    val success: Boolean,
    val plans: List<Plan>? = null,
    val error: ErrorBody? = null
)

@JsonClass(generateAdapter = true)
data class PaymentHistoryItem(
    val id: Int,
    val amount: Double,
    val status: String,
    val provider: String,
    @Json(name = "bkash_payment_id") val bkashPaymentId: String? = null,
    @Json(name = "plan_name") val planName: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class PaymentHistoryResponse(
    val success: Boolean,
    val payments: List<PaymentHistoryItem>? = null,
    val error: ErrorBody? = null
)

// ── Portal SSO ────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SsoRequest(
    val redirectPath: String = "/pricing"
)

@JsonClass(generateAdapter = true)
data class SsoResponse(
    val success: Boolean,
    val portalUrl: String? = null,
    val expiresInSeconds: Int? = null,
    val error: ErrorBody? = null
)
