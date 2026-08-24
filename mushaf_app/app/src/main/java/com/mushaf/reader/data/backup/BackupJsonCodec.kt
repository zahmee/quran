package com.mushaf.reader.data.backup

import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.stats.KhatmaEntity
import com.mushaf.reader.data.stats.SessionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object BackupJsonCodec {

    const val MAX_COMPRESSED_BYTES = 5 * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 25 * 1024 * 1024
    private const val MAX_SESSIONS = 100_000
    private const val MAX_KHATMAS = 10_000
    private val verseKeyPattern = Regex("^[0-9]{1,3}:[0-9]{1,3}$")

    fun encode(snapshot: BackupSnapshot): ByteArray {
        val root = JSONObject()
            .put("schemaVersion", snapshot.schemaVersion)
            .put("backupId", snapshot.backupId)
            .put("createdAt", snapshot.createdAt)
            .put("appVersion", snapshot.appVersion)
            .put("deviceName", snapshot.deviceName)
            .put("pageCount", snapshot.pageCount)
            .put("reading", encodeReading(snapshot.reading))
            .put("sessions", encodeSessions(snapshot.sessions))
            .put("khatmas", encodeKhatmas(snapshot.khatmas))

        val raw = root.toString().toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(raw) }
        return output.toByteArray().also {
            if (it.size > MAX_COMPRESSED_BYTES) {
                throw BackupException("حجم النسخة الاحتياطية أكبر من الحد المدعوم حالياً.")
            }
        }
    }

    fun decode(data: ByteArray, expectedPageCount: Int): BackupSnapshot {
        try {
            if (data.isEmpty() || data.size > MAX_COMPRESSED_BYTES) {
                throw BackupException("حجم ملف النسخة الاحتياطية غير صالح.")
            }
            val root = JSONObject(gunzip(data).toString(Charsets.UTF_8))
            val schemaVersion = root.getInt("schemaVersion")
            if (schemaVersion != BACKUP_SCHEMA_VERSION) {
                throw BackupException("إصدار النسخة الاحتياطية غير مدعوم في هذا الإصدار من التطبيق.")
            }

            val backupId = root.getString("backupId")
            if (backupId.isBlank() || backupId.length > 100) {
                throw BackupException("معرّف النسخة الاحتياطية غير صالح.")
            }
            val pageCount = root.getInt("pageCount")
            if (pageCount != expectedPageCount) {
                throw BackupException("عدد صفحات النسخة لا يطابق نسخة المصحف المثبتة.")
            }

            return BackupSnapshot(
                schemaVersion = schemaVersion,
                backupId = backupId,
                createdAt = root.getLong("createdAt").also {
                    if (it <= 0L) throw BackupException("تاريخ النسخة الاحتياطية غير صالح.")
                },
                appVersion = root.optString("appVersion", "غير معروف").take(40),
                deviceName = root.optString("deviceName", "جهاز غير معروف").take(80),
                pageCount = pageCount,
                reading = decodeReading(root.getJSONObject("reading"), expectedPageCount),
                sessions = decodeSessions(root.getJSONArray("sessions"), expectedPageCount),
                khatmas = decodeKhatmas(root.getJSONArray("khatmas"), expectedPageCount),
            )
        } catch (known: BackupException) {
            throw known
        } catch (error: Exception) {
            throw BackupException("تعذر قراءة النسخة الاحتياطية أو أنها غير مكتملة.", error)
        }
    }

    private fun encodeReading(state: ReadingStore.BackupState): JSONObject = JSONObject()
        .put("settings", encodeSettings(state.settings))
        .put("bookmarks", stringArray(state.bookmarks))
        .put("bookmarks2", stringArray(state.bookmarks2))

    private fun encodeSettings(value: ReadingStore.Settings): JSONObject = JSONObject()
        .put("lastPage", value.lastPage)
        .put("darkTheme", value.darkTheme)
        .put("fillScreen", value.fillScreen)
        .put("visitedPages", intArray(value.visitedPages))
        .put("readPages", intArray(value.readPages))
        .put("hiddenButtons", stringArray(value.hiddenButtons))
        .put("bigButtons", value.bigButtons)
        .put("showClock", value.showClock)
        .put("showSessionTimer", value.showSessionTimer)
        .put("showSurahNumber", value.showSurahNumber)
        .put("showSurahAyahCount", value.showSurahAyahCount)
        .put("showSurahProgress", value.showSurahProgress)
        .put("showJuzProgressPercent", value.showJuzProgressPercent)
        .put("showJuzProgressPages", value.showJuzProgressPages)
        .put("clockColor", value.clockColor)
        .put("sessionTimerColor", value.sessionTimerColor)
        .put("showButtonPage", value.showButtonPage)
        .put("buttonPageColor", value.buttonPageColor)
        .put("showHeaderButtonOpacity", value.showHeaderButtonOpacity)
        .put("buttonPosFraction", value.buttonPosFraction.toDouble())
        .put("showBottomJuzBar", value.showBottomJuzBar)
        .put("bottomJuzBarColor", value.bottomJuzBarColor)
        .put("bottomJuzBarThickness", value.bottomJuzBarThickness)
        .put("bottomJuzBarOpacity", value.bottomJuzBarOpacity)
        .put("showTopSurahBar", value.showTopSurahBar)
        .put("topSurahBarColor", value.topSurahBarColor)
        .put("topSurahBarThickness", value.topSurahBarThickness)
        .put("topSurahBarOpacity", value.topSurahBarOpacity)
        .put("showPageSideIndicator", value.showPageSideIndicator)
        .put("pageSideIndicatorColor", value.pageSideIndicatorColor)
        .put("pageSideIndicatorThickness", value.pageSideIndicatorThickness)
        .put("pageSideIndicatorLength", value.pageSideIndicatorLength)
        .put("pageSideIndicatorOpacity", value.pageSideIndicatorOpacity)
        .put("khatmaStartedAt", value.khatmaStartedAt)
        .put("verticalPaging", value.verticalPaging)

    private fun decodeReading(root: JSONObject, pageCount: Int): ReadingStore.BackupState {
        val settingsJson = root.getJSONObject("settings")
        val visitedPages = intSet(settingsJson.getJSONArray("visitedPages"), pageCount)
        val readPages = intSet(settingsJson.getJSONArray("readPages"), pageCount)
        if (!visitedPages.containsAll(readPages)) {
            throw BackupException("تقدّم الختمة في النسخة الاحتياطية غير متناسق.")
        }

        val bookmarks = stringSet(root.getJSONArray("bookmarks"), 16).also(::validateBookmarks)
        val bookmarks2 = stringSet(root.getJSONArray("bookmarks2"), 16).also(::validateBookmarks)
        val lastPage = settingsJson.getInt("lastPage")
        if (lastPage !in 1..pageCount) throw BackupException("موضع القراءة في النسخة غير صالح.")

        val settings = ReadingStore.Settings(
            lastPage = lastPage,
            darkTheme = settingsJson.optBoolean("darkTheme", false),
            fillScreen = settingsJson.optBoolean("fillScreen", false),
            visitedPages = visitedPages,
            readPages = readPages,
            hiddenButtons = stringSet(settingsJson.getJSONArray("hiddenButtons"), 100),
            bigButtons = settingsJson.optBoolean("bigButtons", false),
            showClock = settingsJson.optBoolean("showClock", false),
            showSessionTimer = settingsJson.optBoolean("showSessionTimer", false),
            showSurahNumber = settingsJson.optBoolean("showSurahNumber", false),
            showSurahAyahCount = settingsJson.optBoolean("showSurahAyahCount", false),
            showSurahProgress = settingsJson.optBoolean("showSurahProgress", false),
            showJuzProgressPercent = settingsJson.optBoolean("showJuzProgressPercent", false),
            showJuzProgressPages = settingsJson.optBoolean("showJuzProgressPages", false),
            clockColor = safeText(settingsJson, "clockColor", "muted"),
            sessionTimerColor = safeText(settingsJson, "sessionTimerColor", "muted"),
            showButtonPage = settingsJson.optBoolean("showButtonPage", true),
            buttonPageColor = safeText(settingsJson, "buttonPageColor", "red"),
            showHeaderButtonOpacity = settingsJson.optInt("showHeaderButtonOpacity", 100).coerceIn(25, 100),
            buttonPosFraction = settingsJson.optDouble("buttonPosFraction", -1.0).coerceIn(-1.0, 1.0).toFloat(),
            showBottomJuzBar = settingsJson.optBoolean("showBottomJuzBar", false),
            bottomJuzBarColor = safeText(settingsJson, "bottomJuzBarColor", "blue"),
            bottomJuzBarThickness = settingsJson.optInt("bottomJuzBarThickness", 4).coerceIn(1, 24),
            bottomJuzBarOpacity = settingsJson.optInt("bottomJuzBarOpacity", 100).coerceIn(0, 100),
            showTopSurahBar = settingsJson.optBoolean("showTopSurahBar", false),
            topSurahBarColor = safeText(settingsJson, "topSurahBarColor", "green"),
            topSurahBarThickness = settingsJson.optInt("topSurahBarThickness", 4).coerceIn(1, 24),
            topSurahBarOpacity = settingsJson.optInt("topSurahBarOpacity", 100).coerceIn(0, 100),
            showPageSideIndicator = settingsJson.optBoolean("showPageSideIndicator", true),
            pageSideIndicatorColor = safeText(settingsJson, "pageSideIndicatorColor", "green"),
            pageSideIndicatorThickness = settingsJson.optInt("pageSideIndicatorThickness", 4).coerceIn(1, 24),
            pageSideIndicatorLength = settingsJson.optInt("pageSideIndicatorLength", 40).coerceIn(8, 240),
            pageSideIndicatorOpacity = settingsJson.optInt("pageSideIndicatorOpacity", 70).coerceIn(0, 100),
            khatmaStartedAt = settingsJson.optLong("khatmaStartedAt", 0L).also {
                if (it < 0L) throw BackupException("تاريخ بداية الختمة غير صالح.")
            },
            verticalPaging = settingsJson.optBoolean("verticalPaging", false),
        )
        return ReadingStore.BackupState(settings, bookmarks, bookmarks2)
    }

    private fun encodeSessions(values: List<SessionEntity>): JSONArray = JSONArray().apply {
        values.forEach { value ->
            put(
                JSONObject()
                    .put("id", value.id)
                    .put("startedAt", value.startedAt)
                    .put("endedAt", value.endedAt)
                    .put("startPage", value.startPage)
                    .put("endPage", value.endPage)
                    .put("pagesRead", value.pagesRead)
            )
        }
    }

    private fun decodeSessions(values: JSONArray, pageCount: Int): List<SessionEntity> {
        if (values.length() > MAX_SESSIONS) throw BackupException("عدد جلسات القراءة في النسخة كبير جداً.")
        return List(values.length()) { index ->
            val value = values.getJSONObject(index)
            val startedAt = value.getLong("startedAt")
            val endedAt = value.getLong("endedAt")
            val startPage = value.getInt("startPage")
            val endPage = value.getInt("endPage")
            val pagesRead = value.getInt("pagesRead")
            if (startedAt < 0L || endedAt < 0L || startPage !in 1..pageCount ||
                endPage !in 1..pageCount || pagesRead !in 0..pageCount
            ) {
                throw BackupException("تحتوي النسخة على جلسة قراءة غير صالحة.")
            }
            SessionEntity(
                id = value.getLong("id").also {
                    if (it < 0L) throw BackupException("معرّف جلسة القراءة غير صالح.")
                },
                startedAt = startedAt,
                endedAt = endedAt,
                startPage = startPage,
                endPage = endPage,
                pagesRead = pagesRead,
            )
        }
    }

    private fun encodeKhatmas(values: List<KhatmaEntity>): JSONArray = JSONArray().apply {
        values.forEach { value ->
            put(
                JSONObject()
                    .put("id", value.id)
                    .put("completedAt", value.completedAt)
                    .put("startedAt", value.startedAt)
                    .put("durationDays", value.durationDays)
                    .put("pagesRead", value.pagesRead)
            )
        }
    }

    private fun decodeKhatmas(values: JSONArray, pageCount: Int): List<KhatmaEntity> {
        if (values.length() > MAX_KHATMAS) throw BackupException("عدد الختمات في النسخة كبير جداً.")
        return List(values.length()) { index ->
            val value = values.getJSONObject(index)
            val completedAt = value.getLong("completedAt")
            val startedAt = value.getLong("startedAt")
            val durationDays = value.getInt("durationDays")
            val pagesRead = value.getInt("pagesRead")
            if (completedAt < 0L || startedAt < 0L ||
                durationDays < 0 || pagesRead !in 0..pageCount
            ) {
                throw BackupException("تحتوي النسخة على سجل ختمة غير صالح.")
            }
            KhatmaEntity(
                id = value.getLong("id").also {
                    if (it < 0L) throw BackupException("معرّف الختمة غير صالح.")
                },
                completedAt = completedAt,
                startedAt = startedAt,
                durationDays = durationDays,
                pagesRead = pagesRead,
            )
        }
    }

    private fun intArray(values: Set<Int>): JSONArray = JSONArray().apply {
        values.sorted().forEach(::put)
    }

    private fun stringArray(values: Set<String>): JSONArray = JSONArray().apply {
        values.sorted().forEach(::put)
    }

    private fun intSet(values: JSONArray, maxPage: Int): Set<Int> {
        if (values.length() > maxPage) throw BackupException("عدد الصفحات في النسخة غير صالح.")
        return buildSet {
            repeat(values.length()) {
                val page = values.getInt(it)
                if (page !in 1..maxPage) throw BackupException("تحتوي النسخة على رقم صفحة غير صالح.")
                add(page)
            }
        }
    }

    private fun stringSet(values: JSONArray, maxItems: Int): Set<String> {
        if (values.length() > maxItems) throw BackupException("تحتوي النسخة على بيانات أكثر من الحد المتوقع.")
        return buildSet {
            repeat(values.length()) {
                val item = values.getString(it)
                if (item.length > 100) throw BackupException("تحتوي النسخة على نص غير صالح.")
                add(item)
            }
        }
    }

    private fun validateBookmarks(values: Set<String>) {
        if (values.any { !verseKeyPattern.matches(it) }) {
            throw BackupException("تحتوي النسخة على علامة مرجعية غير صالحة.")
        }
    }

    private fun safeText(root: JSONObject, key: String, fallback: String): String =
        root.optString(key, fallback).takeIf { it.isNotBlank() && it.length <= 32 } ?: fallback

    private fun gunzip(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(data)).use { input ->
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_EXPANDED_BYTES) {
                    throw BackupException("محتوى النسخة الاحتياطية أكبر من الحد الآمن.")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }
}
