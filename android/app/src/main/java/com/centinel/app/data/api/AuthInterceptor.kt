package com.centinel.app.data.api

import com.centinel.app.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches "Authorization: Bearer <token>" to every request that isn't auth/register|login. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        if (path.endsWith("/auth/register") || path.endsWith("/auth/login") || path.endsWith("/auth/refresh")) {
            return chain.proceed(original)
        }
        val token = runBlocking { tokenStore.getAccessToken() }
        val request = if (token != null) {
            original.newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else original
        return chain.proceed(request)
    }
}

/**
 * On a 401, attempts one silent refresh using the stored refresh token, then
 * retries the original request once with the new access token.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshCall: suspend (String) -> String?, // returns new access token, or null on failure
) : okhttp3.Authenticator {
    override fun authenticate(route: okhttp3.Route?, response: Response): okhttp3.Request? {
        if (response.request.header("Authorization") == null) return null
        if (responseCount(response) >= 2) return null // avoid infinite retry loops

        val refreshToken = runBlocking { tokenStore.getRefreshToken() } ?: return null
        val newAccessToken = runBlocking { refreshCall(refreshToken) } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var res: Response? = response
        var count = 1
        while (res?.priorResponse != null) {
            count++
            res = res.priorResponse
        }
        return count
    }
}
