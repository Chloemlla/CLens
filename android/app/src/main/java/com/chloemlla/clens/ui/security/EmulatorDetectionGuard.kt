package com.chloemlla.clens.ui.security

import android.content.Context
import android.os.Build
import java.io.File

object EmulatorDetectionGuard {

    fun assess(context: Context): List<String> {
        return runCatching {
            mutableListOf<String>().apply {
                checkFingerprint()
                checkBuildFields()
                checkManufacturer()
                checkBrand()
                checkPipes()
            }.toList()
        }.getOrDefault(emptyList())
    }

    private fun MutableList<String>.checkFingerprint() {
        runCatching {
            val fp = Build.FINGERPRINT ?: return@runCatching
            if (fp.startsWith("generic") || fp.startsWith("goldfish") ||
                fp.startsWith("ranchu") || fp.contains("unknown")
            ) {
                add("emulator-fingerprint")
            }
        }
    }

    private fun MutableList<String>.checkBuildFields() {
        val markers = arrayOf("sdk", "emulator", "google_sdk", "goldfish", "ranchu", "Android SDK built for")
        val fields = mapOf(
            "product" to Build.PRODUCT,
            "device" to Build.DEVICE,
            "model" to Build.MODEL,
            "hardware" to Build.HARDWARE,
        )
        for ((name, value) in fields) {
            runCatching {
                val v = value ?: return@runCatching
                if (markers.any { v.contains(it) }) {
                    add("emulator-build-$name")
                }
            }
        }
    }

    private fun MutableList<String>.checkManufacturer() {
        runCatching {
            val mfr = Build.MANUFACTURER ?: return@runCatching
            if (mfr == "Genymotion" || mfr == "unknown") {
                add("emulator-manufacturer")
            }
        }
    }

    private fun MutableList<String>.checkBrand() {
        runCatching {
            val brand = Build.BRAND ?: return@runCatching
            if (brand.startsWith("generic")) {
                add("emulator-brand")
            }
        }
    }

    private fun MutableList<String>.checkPipes() {
        runCatching {
            if (File("/dev/qemu_pipe").exists() || File("/dev/goldfish_pipe").exists()) {
                add("qemu-pipe")
            }
        }
    }
}