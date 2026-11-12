package com.mushaf.reader.share

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.mushaf.reader.data.recitation.Reciter
import com.mushaf.reader.recitation.RecitationStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Makes the files the share sheet sends from a recitation, with Media3's Transformer.
 *
 * [exportAudio] cuts a stretch of a reciter's surah recording into a file of its own: AAC in an
 * MP4 container (.m4a), which every messaging app plays. The recording is read the way the player
 * reads it — through the recitation cache, the network only for what is missing — so a surah
 * downloaded or already heard is cut without a connection, and the reader's mobile-data choice
 * holds here too. It is re-encoded rather than copied: Media3 cannot write MP3, and copying MP3
 * frames would need the whole surah file on the device.
 *
 * [exportVideo] lays such a clip under a still picture: an MP4 for status updates and the apps
 * that take video but not audio files.
 */
@OptIn(UnstableApi::class)
class ClipExporter(context: Context) {
    private val app = context.applicationContext

    /**
     * Writes [range] of [reciter]'s recording of [surah] to [out]. [onProgress] gets 0..100 when
     * the exporter can tell. Cancelling the call cancels the export and deletes the partial file.
     */
    internal suspend fun exportAudio(
        reciter: Reciter,
        surah: Int,
        range: ClipRange,
        out: File,
        onProgress: (Int) -> Unit,
    ) {
        val item = MediaItem.Builder()
            .setUri(reciter.surahUrl(surah))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(range.startMs)
                    .setEndPositionMs(range.endMs)
                    .build()
            )
            .build()
        val edited = EditedMediaItem.Builder(item)
            .setRemoveVideo(true)
            .setEffects(Effects(listOf(FadeAudioProcessor(range.durationMs * 1000)), emptyList()))
            .build()
        run(out, onProgress) { transformer, path -> transformer.start(edited, path) }
    }

    /**
     * Writes [picture] held for [durationMs] over [audio] (a clip [exportAudio] made) to [out] as
     * an MP4. The audio is carried over as it is, not encoded a second time.
     */
    suspend fun exportVideo(
        picture: File,
        audio: File,
        durationMs: Long,
        out: File,
        onProgress: (Int) -> Unit,
    ) {
        val still = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(Uri.fromFile(picture))
                .setImageDurationMs(durationMs)
                .build()
        )
            .setFrameRate(VIDEO_FPS)
            .build()
        val sound = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audio)))
            .setRemoveVideo(true)
            .build()
        val composition = Composition.Builder(
            EditedMediaItemSequence.withVideoFrom(listOf(still)),
            EditedMediaItemSequence.withAudioFrom(listOf(sound)),
        )
            .setTransmuxAudio(true)
            .build()
        run(out, onProgress) { transformer, path -> transformer.start(composition, path) }
    }

    /** Builds a Transformer, has [start] start it towards [out], and waits for it to finish. */
    private suspend fun run(
        out: File,
        onProgress: (Int) -> Unit,
        start: (Transformer, String) -> Unit,
    ): Unit = withContext(Dispatchers.Main) {
        // The Transformer runs on, and calls back on, the thread it was built on: this one.
        out.parentFile?.mkdirs()
        out.delete()
        val done = CompletableDeferred<Unit>()
        val transformer = Transformer.Builder(app)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(app)
                    // Left alone, the encoder follows the source file's bitrate, which MP3Quran
                    // varies from surah to surah (up to 320 kbps): the same ayah length came out
                    // two or three times the size. A fixed rate keeps sizes predictable and is
                    // ample for a voice.
                    .setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder().setBitrate(AUDIO_BITRATE).build()
                    )
                    // A still picture: a key frame now and then is all it needs, and every frame
                    // between repeats the last. The bitrate is a ceiling the encoder rarely nears.
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(VIDEO_BITRATE)
                            .setiFrameIntervalSeconds(VIDEO_KEY_FRAME_SECONDS)
                            .build()
                    )
                    .build()
            )
            // A streamable MP4 reserves room ahead of the media for its index — about 400 KB of
            // nothing, which tripled a five-second clip. A shared file is received whole before
            // it plays, so the index can simply go at the end.
            .setMuxerFactory(InAppMp4Muxer.Factory().setAttemptStreamableOutputEnabled(false))
            .setAssetLoaderFactory(
                DefaultAssetLoaderFactory(
                    app,
                    DefaultDecoderFactory.Builder(app).build(),
                    Clock.DEFAULT,
                    DefaultMediaSourceFactory(RecitationStorage.playbackDataSourceFactory(app)),
                    DataSourceBitmapLoader.Builder(app).build(),
                )
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    done.completeExceptionally(exportException)
                }
            })
            .build()

        start(transformer, out.absolutePath)
        val poll = launch {
            val holder = ProgressHolder()
            while (true) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress)
                }
                delay(250)
            }
        }
        try {
            done.await()
        } catch (e: Throwable) {
            if (e is CancellationException) transformer.cancel()
            out.delete()
            throw e
        } finally {
            poll.cancel()
        }
    }

    private companion object {
        /** AAC at 128 kbps: transparent for recitation, about a megabyte a minute. */
        const val AUDIO_BITRATE = 128_000

        /** A still picture needs few frames; fewer frames also leave each one more of the bitrate,
         *  which is what keeps the mushaf's fine marks sharp. */
        const val VIDEO_FPS = 2
        const val VIDEO_BITRATE = 1_000_000
        const val VIDEO_KEY_FRAME_SECONDS = 10f
    }
}

/**
 * Fades the clip in over its first [fadeInUs] and out over its last [fadeOutUs]; see
 * [AudioClips.fadeGain]. Counts time from the first sample it is given, which with a clipped
 * input is the clip's start.
 */
@OptIn(UnstableApi::class)
private class FadeAudioProcessor(
    private val durationUs: Long,
    private val fadeInUs: Long = 120_000,
    private val fadeOutUs: Long = 300_000,
) : BaseAudioProcessor() {

    private var frames = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        // PCM is in the platform's byte order; a buffer does not always say so.
        inputBuffer.order(ByteOrder.nativeOrder())
        val out = replaceOutputBuffer(inputBuffer.remaining())
        val channels = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate.toLong()
        while (inputBuffer.remaining() >= 2 * channels) {
            val gain = AudioClips.fadeGain(frames * 1_000_000 / rate, durationUs, fadeInUs, fadeOutUs)
            repeat(channels) {
                val s = (inputBuffer.getShort() * gain).roundToInt()
                out.putShort(s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
            frames++
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        frames = 0
    }

    override fun onReset() {
        frames = 0
    }
}
