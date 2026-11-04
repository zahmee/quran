package com.mushaf.reader.recitation

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.scheduler.Requirements

/** Whether recitation audio may use mobile data. Only the audio: the small reciter list and
 *  timing tables are fetched on any network. */
enum class MobileDataPolicy(val id: String) {
    /** Not decided yet: the reader is asked the first time audio would stream on mobile data. */
    ASK("ask"),
    ALLOW("allow"),
    WIFI_ONLY("wifi");

    companion object {
        fun fromId(id: String?): MobileDataPolicy = entries.firstOrNull { it.id == id } ?: ASK
    }
}

/**
 * The mobile-data choice, kept in plain SharedPreferences rather than the DataStore the other
 * settings use: the playback data source and the download manager read it synchronously, from the
 * service as well as the activity. It is a device-local choice, so it never travels in a backup.
 */
object MobileData {

    private const val PREFS = "recitation"
    private const val KEY = "mobile_data"

    fun policy(context: Context): MobileDataPolicy = MobileDataPolicy.fromId(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    )

    fun setPolicy(context: Context, policy: MobileDataPolicy) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, policy.id) }
    }

    /** There is a network and it costs money (mobile data, a metered hotspot). With no network
     *  at all this is false, so being offline reads as offline rather than as "mobile data". */
    fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.activeNetwork != null && cm.isActiveNetworkMetered
    }

    /** Streaming new audio would break the reader's choice right now. */
    fun blocksStreaming(context: Context): Boolean =
        policy(context) != MobileDataPolicy.ALLOW && isMetered(context)

    /** Downloads wait for an unmetered network unless mobile data was allowed. */
    @OptIn(UnstableApi::class)
    fun downloadRequirements(context: Context): Requirements = Requirements(
        if (policy(context) == MobileDataPolicy.ALLOW) Requirements.NETWORK
        else Requirements.NETWORK_UNMETERED
    )
}
