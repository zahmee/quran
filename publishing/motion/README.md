# مقاطع شرح نتلو

مقاطع قصيرة طولية (1080×1920) مبنية بـ [HyperFrames](https://hyperframes.heygen.com) من تسجيلات شاشة حقيقية
للتطبيق. نسخها المضغوطة للموقع في `docs/features/media/`.

| المجلد | المقطع | صفحة الشرح |
|---|---|---|
| `page-side/` | صفحة يمين أم يسار؟ (15 ث) | `docs/features/page-side.html` |
| `full-screen/` | ملء الشاشة: توسيع مساحة القراءة (11.6 ث) | `docs/features/fullscreen.html` |

## التسجيلات الخام ليست في git

ملفات `assets/app/*.mp4` تسجيلات من المحاكي (نحو 44 ميغابايت)، فهي مستثناة من المستودع. لإعادة التصدير
على جهاز آخر سجّلها من جديد بالخطوات أدناه وضعها بالأسماء نفسها. اللقطة الوحيدة المحفوظة هي
`page-side/assets/app/settings-indicator.png`.

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
