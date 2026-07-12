package dev.opencode.bilimobile.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {
    @Suppress("DEPRECATION")
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "session_cookies",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        preferences.all.values.filterIsInstance<String>().forEach { value ->
            runCatching { json.decodeFromString<StoredCookie>(value).toCookie() }
                .getOrNull()?.let { cookies[key(it)] = it }
        }
        removeExpired()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val key = key(cookie)
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                this.cookies.remove(key)
                preferences.edit().remove(key).apply()
            } else {
                this.cookies[key] = cookie
                // Bilibili uses several session cookies during login and risk checks. Keep
                // them encrypted across process restarts instead of silently losing auth.
                preferences.edit().putString(key, json.encodeToString(StoredCookie(cookie))).apply()
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        removeExpired()
        return cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        preferences.edit().clear().apply()
    }

    @Synchronized
    fun valueFor(url: HttpUrl, name: String): String? = loadForRequest(url).firstOrNull { it.name == name }?.value

    @Synchronized
    private fun removeExpired() {
        val now = System.currentTimeMillis()
        cookies.filterValues { it.expiresAt <= now }.keys.forEach {
            cookies.remove(it)
            preferences.edit().remove(it).apply()
        }
    }

    private fun key(cookie: Cookie) = "${cookie.domain}|${cookie.path}|${cookie.name}"
}

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean
) {
    constructor(cookie: Cookie) : this(
        cookie.name, cookie.value, cookie.expiresAt, cookie.domain, cookie.path,
        cookie.secure, cookie.httpOnly, cookie.hostOnly
    )

    fun toCookie(): Cookie = Cookie.Builder().name(name).value(value).expiresAt(expiresAt).path(path)
        .let { if (hostOnly) it.hostOnlyDomain(domain) else it.domain(domain) }
        .let { if (secure) it.secure() else it }
        .let { if (httpOnly) it.httpOnly() else it }
        .build()
}
