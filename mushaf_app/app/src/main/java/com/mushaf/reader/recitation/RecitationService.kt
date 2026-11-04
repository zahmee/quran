package com.mushaf.reader.recitation

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mushaf.reader.R

/**
 * Plays recitation in the background. The media session gives it the system's notification and
 * lock-screen controls, takes audio focus (a call pauses it) and pauses when headphones are pulled.
 *
 * The surahs are whole MP3 files streamed from MP3Quran. They are constant-bitrate (checked on the
 * main reciters on 2026-11-04), so a seek to an ayah is a byte offset that the CDN serves with a
 * range request: starting mid-Baqarah downloads from that point only, never the whole file.
 */
@OptIn(UnstableApi::class)
class RecitationService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Files without a Xing header seek by bitrate; say so explicitly rather than rely on the
        // extractor's fallback.
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                // The device cache first: what was heard or downloaded plays without the network.
                DefaultMediaSourceFactory(RecitationStorage.playbackDataSourceFactory(this), extractors)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        session = MediaSession.Builder(this, player)
            .apply { if (openApp != null) setSessionActivity(openApp) }
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                // The library's default channel name is English ("Now playing"); this is what
                // the reader sees in the system's notification settings.
                .setChannelName(R.string.recitation_channel_name)
                .build().apply {
                setSmallIcon(R.drawable.ic_launcher_monochrome)
            }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away keeps a playing recitation going; anything else stops the service. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
