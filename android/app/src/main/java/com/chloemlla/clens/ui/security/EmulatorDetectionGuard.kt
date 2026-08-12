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
                checkExtraProps()
                checkCpuInfo()
                checkQemuSockets()
                checkEmulatorFiles()
                checkKnownEmulatorModels()
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
            if (systemProperty("ro.kernel.qemu") == "1") {
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

    private fun MutableList<String>.checkExtraProps() {
        val props = mapOf(
            "ro.boot.qemu" to "1",
            "qemu.hw.mainkeys" to "1",
            "sys.qemu.mainkeys" to "1",
            "init.svc.qemud" to "running",
            "init.svc.qemu-props" to "running",
        )
        for ((key, value) in props) {
            runCatching {
                if (systemProperty(key) == value) {
                    add("emulator-prop:$key")
                }
            }
        }
    }

    private fun MutableList<String>.checkCpuInfo() {
        runCatching {
            val cpu = File("/proc/cpuinfo")
            if (cpu.canRead()) {
                val text = cpu.readText()
                val markers = listOf("QEMU", "goldfish", "ranchu", "vbox", "hypervisor", "VMware")
                if (markers.any { text.contains(it, ignoreCase = true) }) {
                    add("emulator-cpuinfo")
                }
            }
        }
    }

    private fun MutableList<String>.checkQemuSockets() {
        runCatching {
            if (File("/dev/socket/qemud").exists() || File("/dev/socket/qemu_pipe").exists()) {
                add("qemu-socket")
            }
        }
    }

    private fun MutableList<String>.checkEmulatorFiles() {
        runCatching {
            val files = listOf(
                "/system/bin/qemu-props",
                "/system/lib/libc_malloc_debug_qemu.so",
                "/system/lib64/libc_malloc_debug_qemu.so",
                "/system/app/QemuTrace",
            )
            if (files.any { File(it).exists() }) {
                add("emulator-files")
            }
        }
    }

    private fun MutableList<String>.checkKnownEmulatorModels() {
        runCatching {
            val model = Build.MODEL ?: return@runCatching
            val emulators = listOf("Genymotion", "vbox86p", "Nox", "MuMu", "LDPlayer", "BlueStacks", "Droid4X")
            if (emulators.any { model.contains(it, ignoreCase = true) }) {
                add("emulator-model")
            }
        }
    }

    private fun systemProperty(key: String): String? {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        }.getOrNull()
    }
}
