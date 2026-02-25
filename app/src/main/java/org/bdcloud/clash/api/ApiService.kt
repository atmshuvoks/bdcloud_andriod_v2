package org.bdcloud.clash.api

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ────────────────────────────────────────────

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<MeResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(): Response<RefreshResponse>

    @POST("auth/reset-app-device")
    suspend fun resetDevice(): Response<BaseResponse>

    @POST("auth/portal-sso-token")
    suspend fun createPortalSso(@Body body: SsoRequest): Response<SsoResponse>

    // ── Proxies ─────────────────────────────────────────

    @GET("proxies/session-key")
    suspend fun getSessionKey(): Response<SessionKeyResponse>

    @GET("proxies")
    suspend fun getProxies(): Response<ProxiesResponse>

    // ── Payments ────────────────────────────────────────

    @GET("payments/plans")
    suspend fun getPlans(): Response<PlansResponse>

    @GET("payments/history")
    suspend fun getPaymentHistory(): Response<PaymentHistoryResponse>

    // ── App Updates & Notifications ────────────────────────

    @GET("app/version")
    suspend fun getAppVersion(): Response<AppVersionResponse>

    @GET("app/notifications")
    suspend fun getNotifications(): Response<NotificationsResponse>
}
