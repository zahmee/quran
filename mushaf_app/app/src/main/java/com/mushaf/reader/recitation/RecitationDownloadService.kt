package com.mushaf.reader.recitation

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import com.mushaf.reader.R

/**
 * Downloads whole surah files into [RecitationStorage]'s cache, with a progress notification.
 * When a download waits for its network (Wi-Fi unless mobile data was allowed), WorkManager
 * restarts this service once that network is back.
 */
@OptIn(UnstableApi::class)
class RecitationDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.recitation_download_channel_name,
    /* channelDescriptionResourceId = */ 0,
) {

    override fun getDownloadManager(): DownloadManager = RecitationStorage.downloadManager(this)

    override fun getScheduler(): Scheduler = WorkManagerScheduler(this, "recitation-downloads")

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification = DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
        this,
        R.drawable.ic_launcher_monochrome,
        /* contentIntent = */ null,
        getString(R.string.recitation_downloading),
        downloads,
        notMetRequirements,
    )

    companion object {
        const val CHANNEL_ID = "recitation_downloads"
        private const val NOTIFICATION_ID = 2001
    }
}
