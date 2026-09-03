# النشر إلى Google Play من الطرفية

يستخدم المشروع إضافة [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)
(‏`com.github.triplet.play`) لرفع حزمة AAB موقّعة إلى Google Play عبر **Google Play Developer API**
مباشرة، بلا فتح متصفح ولا رفع يدوي.

> **الحالة الآن:** الإعداد جاهز في المستودع، لكن النشر **معطّل** حتى تضيف ملف الاعتماد.
> ما دام `mushaf_app/play-service-account.json` غير موجود فإن `enabled` يساوي `false`،
> ومهام النشر لا تفعل شيئًا. لا يمكن أن يُنشر شيء بالخطأ.

## ١. الإعداد لمرة واحدة

### أ. في Google Cloud Console

1. اختر مشروع Google Cloud المرتبط بحساب Play (أو أنشئ واحدًا).
2. فعّل **Google Play Android Developer API**.
3. أنشئ **Service Account**، ثم من قائمة النقاط الثلاث: `Manage keys` ← `Add key` ← **JSON**،
   ونزّل الملف.

### ب. في Google Play Console

1. **Users and permissions** ← `Invite new user`.
2. الصق بريد الـservice account (تجده داخل ملف JSON في حقل `client_email`).
3. حدّد تطبيق «قرآن القارئ» فقط، وامنحه الصلاحيات التي تحتاجها:
   - `Release apps to testing tracks` — يكفي للعمل على مسار `internal`.
   - `Release to production` — أضِفها لاحقًا فقط حين تريد النشر للعامة.
   - `Manage store presence` — إن أردت تحديث نص المتجر واللقطات من الطرفية أيضًا.

### ج. في المشروع

ضع ملف JSON هنا:

```
mushaf_app/play-service-account.json
```

الملف مستبعد في `.gitignore` تمامًا كما هي حال `keystore.properties` و`release.jks`،
فلا يدخل المستودع أبدًا. **لا ترفعه ولا تشاركه.**

## ٢. الاستخدام

من مجلد `mushaf_app`:

| الأمر | ما يفعله |
| --- | --- |
| `python tools/check_play_auth.py` | يتحقق أن المفتاح والصلاحيات يعملان — بلا رفع أي شيء |
| `./gradlew tasks --group publishing` | يعرض كل مهام النشر المتاحة |
| `./gradlew :app:bundleRelease` | يبني AAB موقّعًا فقط، بلا رفع |
| `./gradlew :app:publishReleaseBundle` | يبني AAB ويرفعه إلى المسار المضبوط |
| `./gradlew :app:promoteReleaseArtifact --from-track internal --promote-track production` | يرقّي إصدارًا مرفوعًا من مسار إلى آخر |

## ٣. الإعدادات الحالية

كتلة `play { }` في [`app/build.gradle.kts`](../mushaf_app/app/build.gradle.kts):

- `track = "internal"` — الاختبار الداخلي، لا الإنتاج.
- `releaseStatus = DRAFT` — يصل الرفع إلى Play **كمسودة**، ولا يستلمه أي مختبِر
  حتى تعتمده يدويًا من الكونسول. هذا هو صمّام الأمان.
- `defaultToAppBundles = true` — AAB وليس APK، كما يطلب Play للتطبيقات الجديدة والتحديثات.

حين تصبح جاهزًا للنشر الفعلي غيّر `releaseStatus` إلى `ReleaseStatus.COMPLETED`،
و`track` إلى `"production"` عند الحاجة. للطرح التدريجي استخدم
`ReleaseStatus.IN_PROGRESS` مع `userFraction.set(0.1)` مثلًا.

## ٤. ملاحظات الإصدار

تُقرأ من:

```
mushaf_app/app/src/main/play/release-notes/<اللغة>/<المسار>.txt
```

الموجود حاليًا `ar/default.txt` ويُستخدم لكل المسارات. حدّثه مع كل إصدار.

- الحدّ الأقصى **٥٠٠ حرف**.
- اسم مجلد اللغة يجب أن يطابق لغة موجودة في صفحة المتجر (`ar` للعربية).
  لو أضفت لغة أخرى للمتجر، أضِف لها مجلدًا هنا أيضًا.
- لتخصيص ملاحظات لمسار بعينه أنشئ `ar/production.txt` بجانب `default.txt`.

## ٥. قبل كل نشر

1. ارفع `versionCode` و`versionName` في `app/build.gradle.kts` — Play يرفض تكرار `versionCode`.
2. حدّث `CHANGELOG.md` و`release-notes/ar/default.txt`.
3. تأكد من وجود `keystore.properties` و`release.jks` حتى تُوقَّع الحزمة.
4. راجع القائمة في [`README.md`](README.md) الخاصة بأصول المتجر.

## ٦. تثبيت نسخة الإضافة

الإضافة مثبّتة على **3.13.0** في `gradle/libs.versions.toml`، وهي آخر نسخة تعمل مع
Gradle 8.x المستخدم في المشروع. النسخ 4.x تشترط Gradle 9.1 فأعلى، فلا ترفعها قبل
ترقية الـwrapper و‏AGP معًا واختبار البناء.

## ٧. ما لا تغطيه الـAPI

يبقى في الكونسول يدويًا: استمارات **App content** (أمان البيانات، الجمهور المستهدف،
الإعلانات، تصنيف المحتوى)، وأي إقرارات سياسات جديدة، وأول رفع لتطبيق جديد كليًا.
هذه لا تتكرر مع كل تحديث.
