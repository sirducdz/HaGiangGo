package com.reed.hagianggo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reed.hagianggo.data.HaGiangDatabase
import com.reed.hagianggo.domain.AutoCheckInCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Xử lý các nút bấm nhanh ngay trên Thông báo màn hình khóa ("+10 phút", "✓ Đã lên xe")
 * mà không cần mở khóa điện thoại khi đang chạy xe đèo.
 */
class PacerActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ADD_10_MIN = "com.reed.hagianggo.ACTION_PIT_STOP_ADD_10"
        const val ACTION_COMPLETE_STOP = "com.reed.hagianggo.ACTION_PIT_STOP_COMPLETE"
        const val EXTRA_PLACE_ID = "extra_place_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appCtx = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = HaGiangDatabase.getInstance(appCtx)
                when (action) {
                    ACTION_ADD_10_MIN -> {
                        AutoCheckInCoordinator.addPitStopMinutes(appCtx, db, 10)
                    }
                    ACTION_COMPLETE_STOP -> {
                        val placeId = intent.getStringExtra(EXTRA_PLACE_ID)
                            ?: AutoCheckInCoordinator.activeStopTimer.value?.placeId
                            ?: return@launch
                        AutoCheckInCoordinator.completePitStop(
                            context = appCtx,
                            database = db,
                            placeId = placeId,
                            allPlaces = db.getAllPlaces(),
                            isAuto = false
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
