package com.example.machinetestproject.coreextensions

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService

class ApkInstallationExtension(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val apkUris = inputData.getStringArray("apkUris") ?: return Result.failure()

        for (apkUriString in apkUris) {
            val apkUri = Uri.parse(apkUriString)
            val result = installApk(apkUri)
            if (result != Result.success()) {
                return result
            }
        }
        return Result.success()
    }

    private fun installApk(apkUri: Uri): Result {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return Result.failure()
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, "APK_INSTALL_CHANNEL")
                .setContentTitle("APK Installation")
                .setContentText("Installing APK: ${apkUri.lastPathSegment}")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(1, notification)
            applicationContext.startActivity(intent)

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


