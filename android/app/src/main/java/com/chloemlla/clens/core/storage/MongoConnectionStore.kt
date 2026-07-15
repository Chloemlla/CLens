package com.chloemlla.clens.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chloemlla.clens.core.crash.CrashBreadcrumbs
import com.chloemlla.clens.core.mongo.MongoConnectionProfile
import org.json.JSONArray
import org.json.JSONObject

class MongoConnectionStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    fun listProfiles(): List<MongoConnectionProfile> {
        val raw = prefs.getString(KEY_PROFILES, "[]").orEmpty()
        return runCatching { parseProfiles(raw) }.getOrDefault(emptyList())
    }

    fun getActiveProfileId(): String? = prefs.getString(KEY_ACTIVE_ID, null)?.takeIf { it.isNotBlank() }

    fun getActiveProfile(): MongoConnectionProfile? {
        val activeId = getActiveProfileId() ?: return null
        return listProfiles().firstOrNull { it.id == activeId }
    }

    fun upsert(profile: MongoConnectionProfile) {
        val now = System.currentTimeMillis()
        val existing = listProfiles().toMutableList()
        val index = existing.indexOfFirst { it.id == profile.id }
        val normalized = profile.copy(
            name = profile.name.trim().ifBlank { "未命名连接" },
            updatedAtMillis = now,
            createdAtMillis = if (index >= 0) existing[index].createdAtMillis else profile.createdAtMillis,
        )
        if (index >= 0) {
            existing[index] = normalized
        } else {
            existing.add(0, normalized)
        }
        writeProfiles(existing)
        if (getActiveProfileId().isNullOrBlank()) {
            setActiveProfileId(normalized.id)
        }
        CrashBreadcrumbs.record("Connection upsert: ${normalized.name}")
    }

    fun delete(profileId: String) {
        val remaining = listProfiles().filterNot { it.id == profileId }
        writeProfiles(remaining)
        if (getActiveProfileId() == profileId) {
            setActiveProfileId(remaining.firstOrNull()?.id)
        }
        CrashBreadcrumbs.record("Connection deleted")
    }

    fun setActiveProfileId(profileId: String?) {
        prefs.edit().putString(KEY_ACTIVE_ID, profileId.orEmpty()).apply()
    }

    private fun writeProfiles(profiles: List<MongoConnectionProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("uri", profile.uri)
                    put("host", profile.host)
                    put("port", profile.port)
                    put("username", profile.username)
                    put("password", profile.password)
                    put("authDatabase", profile.authDatabase)
                    put("defaultDatabase", profile.defaultDatabase)
                    put("replicaSet", profile.replicaSet)
                    put("tls", profile.tls)
                    put("directConnection", profile.directConnection)
                    put("createdAtMillis", profile.createdAtMillis)
                    put("updatedAtMillis", profile.updatedAtMillis)
                },
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun parseProfiles(raw: String): List<MongoConnectionProfile> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    MongoConnectionProfile(
                        id = item.getString("id"),
                        name = item.optString("name", "未命名连接"),
                        uri = item.optString("uri"),
                        host = item.optString("host", "127.0.0.1"),
                        port = item.optInt("port", 27017),
                        username = item.optString("username"),
                        password = item.optString("password"),
                        authDatabase = item.optString("authDatabase", "admin"),
                        defaultDatabase = item.optString("defaultDatabase"),
                        replicaSet = item.optString("replicaSet"),
                        tls = item.optBoolean("tls", false),
                        directConnection = item.optBoolean("directConnection", true),
                        createdAtMillis = item.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = item.optLong("updatedAtMillis", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            CrashBreadcrumbs.record("Encrypted prefs fallback: ${it::class.java.simpleName}")
            context.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val PREFS_NAME = "clens_mongo_connections"
        const val PREFS_FALLBACK_NAME = "clens_mongo_connections_fallback"
        const val KEY_PROFILES = "profiles_json"
        const val KEY_ACTIVE_ID = "active_profile_id"
    }
}