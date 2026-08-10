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
                checkQemuKernelProp()
                checkHost()
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
        val markers = arrayOf("sdk", "emulator", "google_sdk", "goldfish", "ranchu", "Android SDK built for", "sdk_gphone", "emulator64", "qemu")
        val fields = mapOf(
            "product" to Build.PRODUCT,
            "device" to Build.DEVICE,
            "model" to Build.MODEL,
            "hardware" to Build.HARDWARE,
            "brand" to Build.BRAND,
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

    private fun MutableList<String>.checkQemuKernelProp() {
        runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val value = method.invoke(null, "ro.kernel.qemu") as? String ?: return@runCatching
            if (value == "1") {
                add("qemu-kernel")
            }
        }
    }

    private fun MutableList<String>.checkHost() {
        runCatching {
            val host = Build.HOST ?: return@runCatching
            if (host.contains("generic") || host.contains("build2")) {
                add("emulator-host")
            }
        }
    }
}