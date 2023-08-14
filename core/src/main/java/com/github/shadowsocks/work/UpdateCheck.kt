package com.github.shadowsocks.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.work.*
import com.github.shadowsocks.Core
import com.github.shadowsocks.Core.app
import com.github.shadowsocks.core.BuildConfig
import com.github.shadowsocks.core.R
import com.github.shadowsocks.preference.DataStore
import com.github.shadowsocks.utils.printLog
import com.github.shadowsocks.utils.useCancellable
import com.google.gson.JsonStreamParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit


class UpdateCheck(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    val url =context.getResources().getString(R.string.update_check_url)
    val update_uri =context.getResources().getString(R.string.update_uri)
    companion object {
        fun enqueue() = WorkManager.getInstance(Core.deviceStorage).enqueueUniquePeriodicWork(
                "UpdateCheck", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateCheck>(1, TimeUnit.DAYS).run {
                    setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresCharging(false)
                            .build())
                    build()
                })
    }

    override suspend fun doWork(): Result = try {
        if(update_uri!="") {
            val connection = URL(url).openConnection() as HttpURLConnection
            val json = connection.useCancellable { inputStream.bufferedReader() }
            val info = JsonStreamParser(json).asSequence().single().asJsonObject
            if (info["version"].asInt > BuildConfig.VERSION_CODE) {
                val nm = app.getSystemService<NotificationManager>()!!
                val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(update_uri))
                var intentFlad=0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) intentFlad=PendingIntent.FLAG_IMMUTABLE
                val builder = NotificationCompat.Builder(app as Context, "update")
                    .setColor(ContextCompat.getColor(app, R.color.material_primary_500))
                    .setContentIntent(PendingIntent.getActivity(app, 0, intent, intentFlad))
                    .setVisibility(
                        if (DataStore.canToggleLocked) NotificationCompat.VISIBILITY_PUBLIC
                        else NotificationCompat.VISIBILITY_PRIVATE
                    )
                    .setSmallIcon(R.drawable.ic_service_active)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setContentTitle(info["title"].asString)
                    .setContentText(info["text"].asString)
                    .setAutoCancel(true)
                nm.notify(62, builder.build())
            }
        }
        Result.success()
    } catch (e: Exception) {
        printLog(e)
        if (runAttemptCount > 5) Result.failure() else Result.retry()
    }
}
