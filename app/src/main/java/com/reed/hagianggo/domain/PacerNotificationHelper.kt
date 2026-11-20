package com.reed.hagianggo.domain

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reed.hagianggo.MainActivity
import com.reed.hagianggo.model.ActiveStopTimer
import com.reed.hagianggo.service.PacerActionReceiver

/**
 * Trung tâm quản lý rung phản hồi xúc giác & Thông báo màn hình khóa
 * cho các sự kiện Auto Check-in, Đếm giờ dừng trạm và Nhắc giờ lên xe.
 */
object PacerNotificationHelper {

    const val CHANNEL_CHECKIN_ID = "hagiang_checkin_events"
    const val CHANNEL_OVERSTAY_ID = "hagiang_pacer_alert"
    const val NOTIF_ID_CHECKIN = 2098
    const val NOTIF_ID_OVERSTAY = 2099

    private const val REQ_OPEN_APP = 3100
    private const val REQ_ACTION_ADD_10 = 3101
    private const val REQ_ACTION_COMPLETE = 3102

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val checkInChannel = NotificationChannel(
            CHANNEL_CHECKIN_ID,
            "Tự động Check-in & Đến địa điểm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Thông báo & rung nhẹ khi GPS nhận diện bạn vừa đến hoặc đi qua một địa điểm trên đèo"
            enableVibration(true)
        }

        val overstayChannel = NotificationChannel(
            CHANNEL_OVERSTAY_ID,
            "Nhắc giờ lên xe & Chống lố giờ đèo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Cảnh báo rung và thông báo khi dừng quá giờ tại điểm check-in đèo Hà Giang"
            enableVibration(true)
        }

        nm.createNotificationChannel(checkInChannel)
        nm.createNotificationChannel(overstayChannel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun vibratePattern(context: Context, pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern.lastOrNull()?.coerceAtLeast(120L) ?: 200L)
                }
            }
        } catch (_: Exception) {
            // Bỏ qua nếu thiết bị không có motor rung hoặc bị hạn chế bởi chế độ tiết kiệm pin
        }
    }

    private fun buildOpenAppIntent(context: Context): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQ_OPEN_APP,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildQuickActions(
        context: Context,
        placeId: String
    ): Pair<NotificationCompat.Action, NotificationCompat.Action> {
        val add10Intent = Intent(context, PacerActionReceiver::class.java).apply {
            action = PacerActionReceiver.ACTION_ADD_10_MIN
            putExtra(PacerActionReceiver.EXTRA_PLACE_ID, placeId)
        }
        val add10Pending = PendingIntent.getBroadcast(
            context,
            REQ_ACTION_ADD_10,
            add10Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, PacerActionReceiver::class.java).apply {
            action = PacerActionReceiver.ACTION_COMPLETE_STOP
            putExtra(PacerActionReceiver.EXTRA_PLACE_ID, placeId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            REQ_ACTION_COMPLETE,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val addAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "+10 phút",
            add10Pending
        ).build()

        val completeAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "✓ Đã lên xe",
            completePending
        ).build()

        return addAction to completeAction
    }

    @SuppressLint("MissingPermission")
    fun notifyArrivedWithTimer(
        context: Context,
        placeId: String,
        placeName: String,
        durationMin: Int,
        shouldVibrate: Boolean = true
    ) {
        ensureChannels(context)
        if (shouldVibrate) {
            vibratePattern(context, longArrayOf(0L, 180L))
        }
        if (!canPostNotifications(context)) return

        val (addAction, completeAction) = buildQuickActions(context, placeId)
        val body = "Bắt đầu đếm ngược ${durationMin}p dừng chân. Rời 350m sẽ tự chốt đi tiếp!"
        val notif = NotificationCompat.Builder(context, CHANNEL_CHECKIN_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("📍 Đã đến $placeName • Đã đánh dấu Đã đi")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(buildOpenAppIntent(context))
            .addAction(addAction)
            .addAction(completeAction)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_CHECKIN, notif)
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    fun notifyPassThroughCheckIn(context: Context, placeName: String) {
        ensureChannels(context)
        vibratePattern(context, longArrayOf(0L, 120L))
        if (!canPostNotifications(context)) return

        val body = "Đã đánh dấu Đã đi qua & cập nhật lại giờ các điểm tiếp theo."
        val notif = NotificationCompat.Builder(context, CHANNEL_CHECKIN_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("✓ Đã tự động check-in: $placeName")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(buildOpenAppIntent(context))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_CHECKIN, notif)
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    fun notifyDepartedPitStop(context: Context, placeName: String) {
        ensureChannels(context)
        val nmCompat = NotificationManagerCompat.from(context)
        nmCompat.cancel(NOTIF_ID_OVERSTAY)
        if (!canPostNotifications(context)) return

        val body = "Tự động cập nhật giờ đến điểm tiếp theo & mốc về trước 17:15."
        val notif = NotificationCompat.Builder(context, CHANNEL_CHECKIN_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("🛵 Đã rời $placeName")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(buildOpenAppIntent(context))
            .build()

        try {
            nmCompat.notify(NOTIF_ID_CHECKIN, notif)
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    fun notifyOverstay(context: Context, timer: ActiveStopTimer) {
        ensureChannels(context)
        vibratePattern(context, longArrayOf(0L, 280L, 160L, 380L))
        if (!canPostNotifications(context)) return

        val (addAction, completeAction) = buildQuickActions(context, timer.placeId)
        val body = "Bạn đã dừng đủ ${timer.targetDurationMin} phút. Nên lên xe đi tiếp để kịp về thị trấn trước 17:15!"
        val notif = NotificationCompat.Builder(context, CHANNEL_OVERSTAY_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Đã hết giờ dừng tại ${timer.placeName}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(buildOpenAppIntent(context))
            .addAction(addAction)
            .addAction(completeAction)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_OVERSTAY, notif)
        } catch (_: SecurityException) {}
    }

    fun cancelOverstayAlert(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_OVERSTAY)
    }

    fun cancelPitStopAlerts(context: Context) {
        val nmCompat = NotificationManagerCompat.from(context)
        nmCompat.cancel(NOTIF_ID_CHECKIN)
        nmCompat.cancel(NOTIF_ID_OVERSTAY)
    }
}
