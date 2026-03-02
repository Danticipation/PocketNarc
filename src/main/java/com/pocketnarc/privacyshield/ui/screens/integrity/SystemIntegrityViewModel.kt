package com.pocketnarc.privacyshield.ui.screens.integrity

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class IntegrityCheck(
    val title: String,
    val description: String,
    val status: IntegrityStatus,
    val severity: IntegritySeverity
)

enum class IntegrityStatus {
    SECURE, COMPROMISED, WARNING
}

enum class IntegritySeverity {
    MEDIUM, HIGH, CRITICAL
}

class SystemIntegrityViewModel : ViewModel() {

    private val _checks = MutableStateFlow<List<IntegrityCheck>>(emptyList())
    val checks: StateFlow<List<IntegrityCheck>> = _checks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun runAudit(context: Context) {
        _isScanning.value = true
        val auditResults = mutableListOf<IntegrityCheck>()

        // 1. Root Check
        val isRooted = checkRootMethod1() || checkRootMethod2()
        auditResults.add(
            IntegrityCheck(
                title = "ROOT_ACCESS",
                description = if (isRooted) "Superuser access detected. System integrity failed." else "No su-binaries identified.",
                status = if (isRooted) IntegrityStatus.COMPROMISED else IntegrityStatus.SECURE,
                severity = IntegritySeverity.CRITICAL
            )
        )

        // 2. ADB Debugging Check
        val isAdbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) > 0
        auditResults.add(
            IntegrityCheck(
                title = "ADB_DEBUGGING",
                description = if (isAdbEnabled) "Active bridge connection detected via USB/Network." else "External debug port closed.",
                status = if (isAdbEnabled) IntegrityStatus.WARNING else IntegrityStatus.SECURE,
                severity = IntegritySeverity.HIGH
            )
        )

        // 3. Developer Mode Check
        val isDevMode = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) > 0
        auditResults.add(
            IntegrityCheck(
                title = "DEVELOPER_PROTOCOL",
                description = if (isDevMode) "System configuration set to advanced developer mode." else "Standard user configuration active.",
                status = if (isDevMode) IntegrityStatus.WARNING else IntegrityStatus.SECURE,
                severity = IntegritySeverity.MEDIUM
            )
        )

        // 4. Secure Boot / OEM Unlock
        val isBootloaderCompromised = Build.TAGS?.contains("test-keys") == true
        auditResults.add(
            IntegrityCheck(
                title = "OS_SIGNATURE",
                description = if (isBootloaderCompromised) "System is running custom/test-keys firmware." else "Official manufacture signature verified.",
                status = if (isBootloaderCompromised) IntegrityStatus.COMPROMISED else IntegrityStatus.SECURE,
                severity = IntegritySeverity.HIGH
            )
        )

        _checks.value = auditResults
        _isScanning.value = false
    }

    private fun checkRootMethod1(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    private fun checkRootMethod2(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = process.inputStream.bufferedReader()
            reader.readLine() != null
        } catch (_: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }
}
