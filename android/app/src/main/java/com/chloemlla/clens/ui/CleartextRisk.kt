package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.MongoConnectionProfile

internal object CleartextRisk {
    fun forForm(form: ConnectionFormState): String? {
        if (form.useUri) {
            val uri = form.uri.trim().lowercase()
            if (uri.startsWith("mongodb://") && !uri.contains("tls=true") && !uri.contains("ssl=true")) {
                return "当前 URI 可能使用明文 MongoDB 连接。仅在受信任网络使用，生产环境请启用 TLS。"
            }
            return null
        }
        return if (!form.tls) {
            "当前连接未启用 TLS。明文凭据可能在网络中暴露，仅建议用于受信任局域网。"
        } else {
            null
        }
    }

    fun forProfile(profile: MongoConnectionProfile): String? {
        if (profile.uri.isNotBlank()) {
            val uri = profile.uri.trim().lowercase()
            if (uri.startsWith("mongodb://") && !uri.contains("tls=true") && !uri.contains("ssl=true")) {
                return "当前连接可能使用明文 MongoDB 通道。仅在受信任网络使用。"
            }
            return null
        }
        return if (!profile.tls) {
            "当前连接未启用 TLS。明文凭据可能在网络中暴露。"
        } else {
            null
        }
    }
}
