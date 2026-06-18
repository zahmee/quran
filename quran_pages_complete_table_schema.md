# Quran Pages Complete Table Schema

هذا الملف يشرح الحقول الموجودة في `quran_pages_complete_text_coordinates.json` وملف CSV المرافق.

## الحقول الأساسية لكل آية

| الحقل | النوع | الدلالة | الاستخدام المقترح |
|---|---:|---|---|
| `page` | number | رقم صفحة المصحف داخل صور التطبيق من 1 إلى 604 | تحميل صورة الصفحة وربط الآية بها |
| `verse_key` | string | مفتاح الآية بصيغة `surah:ayah` مثل `2:255` | معرف ثابت وسهل للفهرسة |
| `source_verse_id` | number | معرف الآية في مصدر Quran.com | تتبع المصدر أو الربط مع API لاحقًا |
| `source_page_number` | number | رقم الصفحة في مصدر Quran.com | مقارنة/تحقق مع مصدر البيانات |
| `source_verse_number` | number | رقم الآية داخل السورة من المصدر | تحقق إضافي؛ يساوي غالبًا `ayah_number` |
| `surah_number` | number | رقم السورة | الفهرسة والتنقل |
| `surah_name_ar` | string | اسم السورة بالعربية | العرض في الواجهة |
| `surah_name_en` | string | اسم السورة بالإنجليزية البسيطة | واجهة إنجليزية أو بحث داخلي |
| `surah.name_complex` / CSV: `surah_name_complex` | string | الاسم اللاتيني المشكّل/المفصل من المصدر | عرض إنجليزي أدق |
| `surah.translated_name_ar` / CSV: `surah_translated_name_ar` | string | الاسم العربي المترجم/الوصف من المصدر | العرض أو البحث |
| `surah.revelation_place` / CSV: `revelation_place` | string | مكية أو مدنية (`makkah` / `madinah`) | فلاتر ومعلومات السورة |
| `surah.revelation_order` / CSV: `revelation_order` | number | ترتيب النزول | ميزات تعليمية/ترتيب نزولي |
| `surah.verses_count` / CSV: `surah_verses_count` | number | عدد آيات السورة | إحصاء وتحقق |
| `surah.bismillah_pre` / CSV: `bismillah_pre` | boolean | هل تسبق السورة بسملة مستقلة في بيانات السورة | عرض السور والفواصل |
| `ayah_number` | number | رقم الآية داخل السورة | العرض والتنقل |

## نصوص الآية

| الحقل | النوع | الدلالة | الاستخدام المقترح |
|---|---:|---|---|
| `ayah_text_uthmani` | string | نص الآية بالرسم العثماني | العرض الرسمي/المصحفي |
| `ayah_text_uthmani_simple` | string | نص عثماني مبسط | مطابقة أخف أو عرض بديل |
| `ayah_text_simple` | string | نص إملائي/بسيط | البحث، الفهرسة، التطبيع النصي |

## التقسيمات القرآنية

| الحقل | النوع | الدلالة | الاستخدام المقترح |
|---|---:|---|---|
| `division.juz.number` / CSV: `juz_number` | number | رقم الجزء | التنقل حسب الأجزاء |
| `division.hizb.number` / CSV: `hizb_number` | number | رقم الحزب | التنقل حسب الأحزاب |
| `division.rub_el_hizb.number` / CSV: `rub_el_hizb_number` | number | رقم ربع الحزب من 1 إلى 240 | ختمة، متابعة ورد، تقسيم حفظ |
| `division.ruku.number` / CSV: `ruku_number` | number | رقم الركوع | ميزات تعليمية/تقسيمات قراءة |
| `division.manzil.number` / CSV: `manzil_number` | number | رقم المنزل | تقسيم سباعي للقراءة |

## آيات السجود

| الحقل | النوع | الدلالة | الاستخدام المقترح |
|---|---:|---|---|
| `sajdah.is_sajdah` / CSV: `is_sajdah` | boolean | هل الآية آية سجدة | إظهار أيقونة سجدة أو تنبيه للقارئ |
| `sajdah.number` / CSV: `sajdah_number` | number/null | ترتيب سجدة التلاوة من 1 إلى 14 | فهرس آيات السجود والتنقل بينها |

## إحداثيات الصورة

| الحقل | النوع | الدلالة | الاستخدام المقترح |
|---|---:|---|---|
| `ayah_marker.x` / CSV: `marker_x` | number/null | يسار مربع علامة رقم الآية | رسم/تحديد علامة الآية |
| `ayah_marker.y` / CSV: `marker_y` | number/null | أعلى مربع علامة رقم الآية | رسم/تحديد علامة الآية |
| `ayah_marker.width` / CSV: `marker_width` | number/null | عرض مربع علامة رقم الآية | مساحة الضغط/التحديد |
| `ayah_marker.height` / CSV: `marker_height` | number/null | ارتفاع مربع علامة رقم الآية | مساحة الضغط/التحديد |
| `ayah_marker.center_x` / CSV: `marker_center_x` | number/null | مركز علامة رقم الآية أفقيًا | نقطة نهاية الآية أو anchor |
| `ayah_marker.center_y` / CSV: `marker_center_y` | number/null | مركز علامة رقم الآية رأسيًا | نقطة نهاية الآية أو anchor |
| `ayah_end.x` / CSV: `ayah_end_x` | number/null | نقطة نهاية الآية أفقيًا، مشتقة من مركز العلامة | التحديد والتظليل من بداية إلى نهاية الآية |
| `ayah_end.y` / CSV: `ayah_end_y` | number/null | نقطة نهاية الآية رأسيًا، مشتقة من مركز العلامة | التحديد والتظليل من بداية إلى نهاية الآية |
| `ayah_end.source` / CSV: `ayah_end_source` | string/null | مصدر نقطة النهاية، حاليًا `ayah_marker_center` | تتبع كيفية إنتاج الإحداثي |
| `coordinate_status` | string | حالة موثوقية الإحداثيات | `verified_count_match` مؤكد عدديًا، و `needs_review_count_mismatch` يحتاج مراجعة |

## حقول مستوى الصفحة داخل JSON فقط

| الحقل | الدلالة |
|---|---|
| `pages[].page` | رقم الصفحة |
| `pages[].source_verse_count` | عدد الآيات في بيانات المصدر لهذه الصفحة |
| `pages[].detected_marker_count` | عدد علامات الآيات المكتشفة من الصورة |
| `pages[].coordinate_status` | حالة إحداثيات الصفحة |
| `pages[].first_ayah` | أول آية في الصفحة |
| `pages[].last_ayah` | آخر آية في الصفحة |
| `pages[].juz_numbers` | الأجزاء الموجودة في الصفحة |
| `pages[].hizb_numbers` | الأحزاب الموجودة في الصفحة |
| `pages[].rub_el_hizb_numbers` | أرباع الحزب الموجودة في الصفحة |
| `pages[].has_sajdah` | هل في الصفحة آية سجدة |
| `pages[].sajdah_ayahs` | قائمة آيات السجود الموجودة في الصفحة، إن وجدت |

## ملاحظات مهمة

- الإحداثيات مبنية على صور WebP البيضاء في `mumtaz-1_pages_cropped_webp/white_background`.
- نظام الإحداثيات: البكسل، نقطة الأصل أعلى يسار الصورة، وأبعاد الصورة `1106 × 1789`.
- النصوص والتقسيمات مأخوذة من Quran.com API v4.
- الصفحات ذات `needs_review_count_mismatch` تحتاج مراجعة إحداثيات قبل الاعتماد النهائي على تحديد نهاية الآية.
