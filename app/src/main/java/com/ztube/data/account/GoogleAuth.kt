package com.ztube.data.account

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthResult {
    data class Token(val token: String) : AuthResult
    data class NeedsResolution(val pendingIntent: PendingIntent) : AuthResult
    data object Cancelled : AuthResult
    data class Failure(val error: Exception) : AuthResult
}

@Singleton
class GoogleAuth @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val client = Identity.getAuthorizationClient(context)
    private val scopes = listOf(Scope(YOUTUBE_SCOPE), Scope(EMAIL_SCOPE))

    suspend fun authorize(email: String?): AuthResult {
        val builder = AuthorizationRequest.builder().setRequestedScopes(scopes)
        if (email != null) builder.setAccount(Account(email, ACCOUNT_TYPE))
        return try {
            client.authorize(builder.build()).await().toAuthResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(e)
        }
    }

    fun completeFromIntent(data: Intent?): AuthResult {
        if (data == null) return AuthResult.Cancelled
        return try {
            client.getAuthorizationResultFromIntent(data).toAuthResult()
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) AuthResult.Cancelled else AuthResult.Failure(e)
        }
    }

    suspend fun clearToken(token: String) {
        try {
            client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    suspend fun revoke(email: String) {
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(email, ACCOUNT_TYPE))
            .setScopes(scopes)
            .build()
        try {
            client.revokeAccess(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    private fun AuthorizationResult.toAuthResult(): AuthResult {
        if (hasResolution()) {
            return pendingIntent?.let { AuthResult.NeedsResolution(it) }
                ?: AuthResult.Failure(IllegalStateException("Missing resolution"))
        }
        return accessToken?.let { AuthResult.Token(it) }
            ?: AuthResult.Failure(IllegalStateException("Missing access token"))
    }

    private companion object {
        const val YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
        const val EMAIL_SCOPE = "email"
        const val ACCOUNT_TYPE = "com.google"
    }
}
