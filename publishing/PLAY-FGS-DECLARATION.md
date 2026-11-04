# إقرار خدمات المقدمة في Google Play

المكان في Play Console:

Monitor and improve › App content › Foreground service permissions

مطلوب منذ أن صار التطبيق يستخدم خدمتين في المقدمة (من الإصدار 0.9.0):
تشغيل التلاوة في الخلفية، وتنزيل التلاوة. بدونه يرفض المتجر أي إصدار
جديد برسالة:

"You must let us know whether your app uses any Foreground Service permissions."

لكل نوع: وصف للميزة، وأثر إيقافها على المستخدم، ورابط فيديو يُظهر
المستخدم وهو يبدأها، وحالة الاستخدام من القائمة. الفيديوهان في
`docs/videos/` فيُنشران على موقع المشروع (GitHub Pages) ويعملان في المتصفح
دون تسجيل دخول. سُجّلا من المحاكي بـ `adb shell screenrecord` ثم ضُغطا بـ
ffmpeg إلى عرض 720. إن رفض المراجع رابطاً مباشراً، فالبديل YouTube غير
مدرج (Unlisted) أو Google Drive برابط مفتوح لمن معه الرابط.

النصوص بالإنجليزية لأن المراجعين يقرؤونها.

---

## 1) Media playback

`FOREGROUND_SERVICE_MEDIA_PLAYBACK` — `RecitationService`

**Use case:** Media playback

**Video:** https://zahmee.github.io/quran/videos/natlu-media-playback.mp4

**Feature description**

Natlu plays Quran recitation audio. The user starts it themselves: they
long-press an ayah and choose "Play from here", "This ayah only" or
"Repeat and memorise", or tap the listen button in the page header.
Playback continues in the background and on the lock screen, with a media
notification and controls, until the user pauses or stops it or the chosen
passage and its repetitions end.

**User impact if the system defers or interrupts the task**

The recitation the user is listening to would cut out mid-ayah as soon as
they turn off the screen or leave the app. Memorisation sessions, where an
ayah or passage repeats a set number of times, would be broken and lose
their count.

---

## 2) Data sync

`FOREGROUND_SERVICE_DATA_SYNC` — `RecitationDownloadService`

**Use case:** Network transfer: upload or download

**Video:** https://zahmee.github.io/quran/videos/natlu-recitation-download.mp4

**Feature description**

The user downloads recitation audio for offline listening. In Settings ›
Recitation they tap "Download this surah", "Download this juz", "Download
the whole mushaf", or pick surahs in "Choose surahs to download" and tap
Download. Each download fetches the full-surah audio file and its ayah
timing table. Downloads run only after this explicit request, show a
progress notification, and can be deleted from the same tab.

**User impact if the system defers or interrupts the task**

A single surah can be over 100 MB and the whole mushaf several GB. If the
transfer stopped when the user left the app or turned off the screen, it
would restart or stall, waste mobile data, and leave the user without the
offline recitation they asked for.
