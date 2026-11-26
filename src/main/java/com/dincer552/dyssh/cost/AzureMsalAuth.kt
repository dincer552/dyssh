package com.dincer552.dyssh.cost

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.Prompt
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class AzureMsalToken(
    val accessToken: String,
    val accountId: String,
    val username: String,
    val tenantId: String
)

/**
 * MSAL owns the refresh-token cache. The app never handles or persists refresh tokens itself.
 * Interactive sign-in uses the system browser and authorization-code flow, not device-code flow.
 */
internal class AzureMsalAuth(context: Context) {
    private val appContext = context.applicationContext

    suspend fun acquireInteractive(activity: Activity, tenantId: String): AzureMsalToken =
        suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createMultipleAccountPublicClientApplication(
                appContext,
                R.raw.azure_msal_config,
                object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                    override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                        val parameters = AcquireTokenParameters.Builder()
                            .startAuthorizationFromActivity(activity)
                            .withScopes(listOf(MANAGEMENT_SCOPE))
                            .withPrompt(Prompt.SELECT_ACCOUNT)
                            .fromAuthority(authority(tenantId))
                            .withCallback(object : AuthenticationCallback {
                                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                    if (continuation.isActive) {
                                        try {
                                            continuation.resume(toToken(authenticationResult))
                                        } catch (error: Exception) {
                                            continuation.resumeWithException(error)
                                        }
                                    }
                                }

                                override fun onError(exception: MsalException) {
                                    if (continuation.isActive) continuation.resumeWithException(
                                        IllegalStateException(describe(exception), exception)
                                    )
                                }

                                override fun onCancel() {
                                    if (continuation.isActive) continuation.resumeWithException(
                                        IllegalStateException("Microsoft oturum açma işlemi iptal edildi.")
                                    )
                                }
                            })
                            .build()
                        application.acquireToken(parameters)
                    }

                    override fun onError(exception: MsalException) {
                        if (continuation.isActive) continuation.resumeWithException(
                            IllegalStateException("MSAL başlatılamadı: " + describe(exception), exception)
                        )
                    }
                }
            )
        }

    suspend fun acquireSilent(
        tenantId: String,
        accountId: String,
        username: String
    ): AzureMsalToken = suspendCancellableCoroutine { continuation ->
        PublicClientApplication.createMultipleAccountPublicClientApplication(
            appContext,
            R.raw.azure_msal_config,
            object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                    application.getAccounts(object : IPublicClientApplication.LoadAccountsCallback {
                        override fun onTaskCompleted(accounts: List<IAccount>) {
                            val account = accounts.firstOrNull {
                                it.getId() == accountId &&
                                    it.getTenantId().equals(tenantId, ignoreCase = true)
                            } ?: accounts.firstOrNull {
                                username.isNotBlank() &&
                                    it.getUsername().equals(username, ignoreCase = true) &&
                                    it.getTenantId().equals(tenantId, ignoreCase = true)
                            }
                            if (account == null) {
                                if (continuation.isActive) continuation.resumeWithException(
                                    IllegalStateException("Bu Azure hesabının MSAL oturumu bulunamadı. Yeniden bağlan.")
                                )
                                return
                            }

                            val parameters = AcquireTokenSilentParameters.Builder()
                                .withScopes(listOf(MANAGEMENT_SCOPE))
                                .forAccount(account)
                                .fromAuthority(authority(tenantId))
                                .forceRefresh(false)
                                .withCallback(object : SilentAuthenticationCallback {
                                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                        if (continuation.isActive) {
                                            try {
                                                continuation.resume(toToken(authenticationResult))
                                            } catch (error: Exception) {
                                                continuation.resumeWithException(error)
                                            }
                                        }
                                    }

                                    override fun onError(exception: MsalException) {
                                        if (continuation.isActive) continuation.resumeWithException(
                                            IllegalStateException(describe(exception), exception)
                                        )
                                    }
                                })
                                .build()
                            application.acquireTokenSilentAsync(parameters)
                        }

                        override fun onError(exception: MsalException) {
                            if (continuation.isActive) continuation.resumeWithException(
                                IllegalStateException("Microsoft hesapları okunamadı: " + describe(exception), exception)
                            )
                        }
                    })
                }

                override fun onError(exception: MsalException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("MSAL başlatılamadı: " + describe(exception), exception)
                    )
                }
            }
        )
    }

    private fun toToken(result: IAuthenticationResult): AzureMsalToken {
        val account = result.account
            ?: throw IllegalStateException("Microsoft token yanıtında hesap bilgisi bulunamadı.")
        val token = result.accessToken
        if (token.isBlank()) throw IllegalStateException("Microsoft boş erişim belirteci döndürdü.")
        return AzureMsalToken(token, account.id, account.username, account.tenantId)
    }

    private fun authority(tenantId: String): String {
        val safeTenant = tenantId.trim()
        require(safeTenant.matches(Regex("[A-Za-z0-9.-]{3,128}"))) {
            "Tenant ID biçimi geçersiz."
        }
        return "https://login.microsoftonline.com/$safeTenant"
    }

    private fun describe(exception: MsalException): String {
        val code = exception.errorCode?.takeIf { it.isNotBlank() }
        return listOfNotNull(code, exception.message?.takeIf { it.isNotBlank() }).joinToString(": ")
            .ifBlank { exception.javaClass.simpleName }
    }

    private companion object {
        const val MANAGEMENT_SCOPE = "https://management.azure.com/user_impersonation"
    }
}
