# مقاطع شرح نتلو

مقاطع قصيرة طولية (1080×1920) مبنية بـ [HyperFrames](https://hyperframes.heygen.com) من تسجيلات شاشة حقيقية
للتطبيق. نسخها المضغوطة للموقع في `docs/features/media/`.

| المجلد | المقطع | صفحة الشرح |
|---|---|---|
| `page-side/` | صفحة يمين أم يسار؟ (15 ث) | `docs/features/page-side.html` |
| `full-screen/` | ملء الشاشة: توسيع مساحة القراءة (11.6 ث) | `docs/features/fullscreen.html` |
| `bookmarks-hide/` | إخفاء العلامات غير المستعملة (20.6 ث)، صُوّر على `QuranTest054` في 0.21.0 | `docs/features/bookmarks.html#hide` |
| الباقي (15 مقطعًا) | نافذة الآية، العلامات، الملاحظات، التفسير، البحث، عن السورة، الاستماع، التكرار، تنزيل التلاوة، المشاركة، الإحصائيات، النسخ الاحتياطي، المزامنة، المظهر، مكتبة التفاسير | `docs/features/<الاسم>.html` |

## المقاطع المولّدة

`page-side/` و`full-screen/` كُتبا يدويًا. أما البقية فتبنيها أداة واحدة من وصف لكل مقطع:

- `tools/specs.py`: لكل مقطع التسجيلات وأوقاتها، وحركة الكاميرا، والعناوين، واللمسات، والإطارات الذهبية، والأجزاء المغطّاة (اسم الحساب في النسخ الاحتياطي). يكتب `specs/<الاسم>.json`.
- `tools/build_clip.py`: يحوّل الوصف إلى مشروع HyperFrames في `<الاسم>/` بنفس شكل المقطعين الأولين، وينسخ الخط من `page-side/`.

```bash
python tools/specs.py && python tools/build_clip.py specs/*.json
cd ayah && npx hyperframes check && npx hyperframes render
```

صُوّرت هذه المقاطع (عدا `bookmarks-hide`) على `Quran_API_36`، وهو محذوف الآن؛ وللتصوير الجديد استعمل `QuranTest054` (1080×2400 أيضًا)، من نسخة الإصدار القادم (فيها العلامات الأربع والملاحظات)، على الكهف صفحة 294
والآية 13. الصور الثابتة (`*_last.png` و`notes_list.png`) محفوظة في git، والتسجيلات ليست فيه.

## التسجيلات الخام ليست في git

ملفات `assets/app/*.mp4` تسجيلات من المحاكي (نحو 44 ميغابايت)، فهي مستثناة من المستودع. لإعادة التصدير
على جهاز آخر سجّلها من جديد بالخطوات أدناه وضعها بالأسماء نفسها. والصور الثابتة محفوظة، ومنها
`page-side/assets/app/settings-indicator.png`. الخطوات أدناه للمقطعين الأولين؛ أما المقاطع المولّدة فأوقات
تسجيلاتها مكتوبة في `tools/specs.py`.

### الإعداد

- المحاكي `Quran_API_36` بدقته الحقيقية: `adb shell wm size reset` (وأعدها بعد التصوير بـ `adb shell wm size 900x2000`).
- في الإعدادات ← الواجهة: «إشارة اتجاه الصفحة» و«عرض رقم الصفحة في الزر» مفعّلان، وشريطا التقدم ظاهران.
- البحث عن `18:1` ثم فتح النتيجة: الكهف، صفحة 293.

### التسجيلات

ثم حوّل كل تسجيل إلى 30 إطارًا ثابتة ليكون التقديم دقيقًا:

```bash
ffmpeg -i raw.mp4 -vf "fps=30,format=yuv420p" -c:v libx264 -preset slow -crf 17 -g 10 -an -movflags +faststart out.mp4
```

| الملف | ما يُسجَّل (`adb shell screenrecord --bit-rate 12000000`) |
|---|---|
| `page-side/assets/app/turn-header.mp4` | الرأس ظاهر على 293، ثم تقليب إلى 294 عند الثانية 2 تقريبًا، ثم إلى 295 عند 5.7. |
| `page-side/assets/app/turn-full.mp4` | ملء الشاشة (زر التوسيع) على 293، ثم 294 عند الثانية 2، ثم 295 عند 5.7. |
| `full-screen/assets/app/hide-header.mp4` | الرأس ظاهر على 294؛ لمس زر التوسيع عند 1.9؛ سحب الزر العائم من (40,175) إلى (40,1150) بين 4.2 و5.4؛ لمسه عند 7.7 فيعود الرأس. |

التوقيتات مكتوبة في `compositions/film.html` لكل مقطع (`data-media-start` وأوقات الكاميرا)؛ إن اختلفت
التسجيلات الجديدة فعدّلها هناك.

### التصدير

```bash
cd page-side && npx hyperframes check && npx hyperframes render
ffmpeg -i renders/<الملف>.mp4 -vf "scale=720:1280:flags=lanczos,format=yuv420p" -c:v libx264 -preset slow -crf 26 -movflags +faststart -an ../../../docs/features/media/page-side.mp4
```
