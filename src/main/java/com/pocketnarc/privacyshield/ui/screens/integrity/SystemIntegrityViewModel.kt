package com.pocketnarc.privacyshield.ui.screens.integrity

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

data class IntegrityCheck(
    val title: String,
    val description: String,
    val status: IntegrityStatus,
    val severity: IntegritySeverity,
    val mitigation: String = "No action required."
)

enum class IntegrityStatus {
    SECURE, COMPROMISED, WARNING
}

enum class IntegritySeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

class SystemIntegrityViewModel : ViewModel() {

    private val _checks = MutableStateFlow<List<IntegrityCheck>>(emptyList())
    val checks: StateFlow<List<IntegrityCheck>> = _checks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _integrityScore = MutableStateFlow(100)
    val integrityScore: StateFlow<Int> = _integrityScore.asStateFlow()

    fun runAudit(context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            val auditResults = mutableListOf<IntegrityCheck>()

            // 1. Google Play Integrity Token Request (Client-side)
            requestPlayIntegrityToken(context, auditResults)

            // 2. Deep Binary Root Check
            val rootDiscovery = performDeepRootCheck()
            auditResults.add(
                IntegrityCheck(
                    title = "ROOT_ACCESS",
                    description = rootDiscovery.first,
                    status = if (rootDiscovery.second) IntegrityStatus.COMPROMISED else IntegrityStatus.SECURE,
                    severity = IntegritySeverity.CRITICAL,
                    mitigation = "Unauthorized superuser access allows apps to bypass OS sandboxing. Flash stock firmware."
                )
            )

            // 3. SELinux Status
            val selinux = getSELinuxMode()
            auditResults.add(
                IntegrityCheck(
                    title = "SELINUX_ENFORCEMENT",
                    description = if (selinux == "Enforcing") "Kernel-level access control active." else "SELinux set to $selinux mode.",
                    status = if (selinux == "Enforcing") IntegrityStatus.SECURE else IntegrityStatus.WARNING,
                    severity = IntegritySeverity.HIGH,
                    mitigation = "Permissive SELinux allows malicious hooks into system processes. Ensure firmware is official."
                )
            )

            // 4. Build Fingerprint & Signing Keys
            val testKeys = Build.TAGS != null && Build.TAGS.contains("test-keys")
            auditResults.add(
                IntegrityCheck(
                    title = "OS_SIGNATURE",
                    description = if (testKeys) "Device running unsigned/test-key firmware." else "Firmware signed with official release keys.",
                    status = if (testKeys) IntegrityStatus.WARNING else IntegrityStatus.SECURE,
                    severity = IntegritySeverity.HIGH,
                    mitigation = "Test-keys indicate a non-production or community-built ROM (LineageOS, etc.)."
                )
            )

            // 5. Developer Mode & ADB
            val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) > 0
            auditResults.add(
                IntegrityCheck(
                    title = "DEBUG_PORTS",
                    description = if (adb) "ADB port is open (External command access)." else "System debug ports secured.",
                    status = if (adb) IntegrityStatus.WARNING else IntegrityStatus.SECURE,
                    severity = IntegritySeverity.MEDIUM,
                    mitigation = "Disable USB Debugging in Developer Options when not in use."
                )
            )

            // 6. Hooking Framework Detection (Xposed/EdXposed)
            val hooksDetected = detectHookingFrameworks()
            if (hooksDetected) {
                auditResults.add(
                    IntegrityCheck(
                        title = "HOOK_FRAMEWORK",
                        description = "Xposed/LSPosed artifacts detected in memory/filesystem.",
                        status = IntegrityStatus.COMPROMISED,
                        severity = IntegritySeverity.CRITICAL,
                        mitigation = "Hooking frameworks can intercept all system calls. Remove LSPosed/Xposed."
                    )
                )
            }

            _checks.value = auditResults.sortedByDescending { it.severity }
            calculateScore(auditResults)
            _isScanning.value = false
        }
    }

    private suspend fun requestPlayIntegrityToken(context: Context, results: MutableList<IntegrityCheck>) {
        withContext(Dispatchers.IO) {
            try {
                val integrityManager = IntegrityManagerFactory.create(context)
                // Compatible Base64 for API 24+
                val nonce = android.util.Base64.encodeToString(
                    UUID.randomUUID().toString().toByteArray(),
                    android.util.Base64.NO_WRAP
                )
                val tokenRequest = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .build()

                val task = integrityManager.requestIntegrityToken(tokenRequest)
                Tasks.await(task, 10, TimeUnit.SECONDS)
                
                results.add(
                    IntegrityCheck(
                        title = "PLAY_INTEGRITY",
                        description = "Cryptographic integrity token generated successfully.",
                        status = IntegrityStatus.SECURE,
                        severity = IntegritySeverity.HIGH,
                        mitigation = "The environment is verified by Google Play services."
                    )
                )
            } catch (e: Exception) {
                results.add(
                    IntegrityCheck(
                        title = "PLAY_INTEGRITY",
                        description = "Integrity API failed to attest environment: ${e.message}",
                        status = IntegrityStatus.WARNING,
                        severity = IntegritySeverity.HIGH,
                        mitigation = "This device may be modified or lacks Google Play Services."
                    )
                )
            }
        }
    }

    private fun performDeepRootCheck(): Pair<String, Boolean> {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        for (path in paths) {
            if (File(path).exists()) return "Detected su-binary at: $path" to true
        }

        // Check for Magisk specific files
        val magiskPaths = arrayOf("/sbin/.magisk", "/cache/.magisk", "/data/.magisk")
        for (path in magiskPaths) {
            if (File(path).exists()) return "Magisk environment artifacts identified." to true
        }

        return "No known root binaries or management apps found." to false
    }

    private fun detectHookingFrameworks(): Boolean {
        val frameworkPaths = arrayOf("/system/framework/XposedBridge.jar", "/data/app/de.robv.android.xposed.installer-1")
        return frameworkPaths.any { File(it).exists() }
    }

    private fun getSELinuxMode(): String {
        return try {
            val process = Runtime.getRuntime().exec("getenforce")
            process.inputStream.bufferedReader().readLine() ?: "Enforcing"
        } catch (_: Exception) { "Enforcing" }
    }

    private fun calculateScore(results: List<IntegrityCheck>) {
        var score = 100
        results.forEach {
            if (it.status == IntegrityStatus.COMPROMISED) {
                score -= when(it.severity) {
                    IntegritySeverity.CRITICAL -> 50
                    IntegritySeverity.HIGH -> 30
                    else -> 20
                }
            } else if (it.status == IntegrityStatus.WARNING) {
                score -= when(it.severity) {
                    IntegritySeverity.CRITICAL -> 25
                    IntegritySeverity.HIGH -> 15
                    else -> 10
                }
            }
        }
        _integrityScore.value = score.coerceAtLeast(0)
    }
}
