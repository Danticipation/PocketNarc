package com.pocketnarc.privacyshield.utils

import android.Manifest
import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.MultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberVibratePermissionState(
    onGranted: () -> Unit = {}
): MultiplePermissionsState {
    return rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.VIBRATE),
        onPermissionsResult = { results ->
            if (results.values.all { it }) {
                onGranted()
            }
        }
    )
}