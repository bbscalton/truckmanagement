package com.truckmgmt.customer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

object LocationPermissionHelper {
    fun hasLocationPermission(activity: AppCompatActivity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestOrRun(
        activity: AppCompatActivity,
        launcher: ActivityResultLauncher<Array<String>>,
        onGranted: () -> Unit,
    ) {
        if (hasLocationPermission(activity)) {
            onGranted()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.location_permission_title)
            .setMessage(R.string.location_permission_rationale)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
