# إشعارات المشروع والمحتوى الخارجي

شفرة **نتلو** والأصول الأصلية الخاصة بالمشروع متاحة بموجب رخصة
[MIT](LICENSE). تسمح الرخصة بالاستخدام والنسخ والتعديل والدمج والنشر والتوزيع، مع
الالتزام بنصها.

## محتوى لا تغطيه رخصة MIT

لا تنقل رخصة MIT إلى المستخدم أي حقوق في المحتوى الخارجي الآتي:

- صور صفحات المصحف داخل `mushaf_app/app/src/main/assets/pages/`.
- نص القرآن الكريم وبيانات إحداثيات الآيات ومشتقاتها.
- أي علامات أو مواد طرف ثالث تظهر في لقطات الشاشة أو ملفات النشر.

### التفسير الميسر والميسر في غريب القرآن

تتضمن قاعدة البيانات المرفقة
`mushaf_app/app/src/main/assets/databases/quran_content_v2.db`
نصوص **التفسير الميسر** (الإصدار 3.0) و**الميسر في غريب القرآن الكريم**
(الطبعة الثانية)، وكلاهما صادر عن مجمع الملك فهد لطباعة المصحف الشريف.
تصف [منصة مطوري برمجيات القرآن الكريم](https://qurancomplex.gov.sa/en/techquran/dev/)
هاتين المادتين بأنهما مقدمتان للمطورين والباحثين ودور النشر لاستخدامهما في
محتوى التطبيقات والبحوث.

هذه النصوص وقاعدة البيانات المشتقة منها محتوى خارجي لا تغطيه رخصة MIT الخاصة
بشفرة المشروع. يذكر التطبيق المصدر داخل الواجهة، ولا يمنح هذا المستودع حقوقاً
إضافية لإعادة نشر المواد الخام أو إعادة ترخيصها. على من يعيد توزيع قاعدة البيانات
أو مشتقاتها مراجعة شروط المصدر السارية، والتواصل مع المجمع عبر
`info@qurancomplex.gov.sa` عند الحاجة إلى توضيح إضافي.

### تفسير السعدي

تتضمن قاعدة البيانات نفسها
`mushaf_app/app/src/main/assets/databases/quran_content_v2.db`
**تفسير السعدي** («تيسير الكريم الرحمن في تفسير كلام المنان» للشيخ عبد الرحمن بن ناصر السعدي)
كما تنشره [موسوعة القرآن الكريم](https://quranenc.com) عبر واجهتها البرمجية، الإصدار 1.0.0
(آخر تحديث للمصدر 2026-07-27، ونُسخ في 2026-11-05 بالأداة
`mushaf_app/tools/fetch_quranenc_saadi.py`).

تتيح الموسوعة تنزيل المحتوى وإعادة نشره بشروط، منها: ذكر الناشر والمصدر (QuranEnc.com)، وعدم
التعديل أو الإضافة أو الحذف، وذكر رقم الإصدار، والتحديث إلى آخر إصدار يصدر منها، وعدم عرض إعلانات
غير لائقة معه. لذلك يُعرض النص كما ورد، ويذكر التطبيق المصدر ورقم الإصدار تحت التفسير وفي كل نسخ أو
مشاركة. وعند صدور إصدار أحدث يُعاد تشغيل أداة النسخ ثم أداة بناء قاعدة البيانات. هذا النص محتوى خارجي
لا تغطيه رخصة MIT الخاصة بشفرة المشروع.

تعتمد صفحات التطبيق على نسخة من مصحف المدينة المنورة الصادر عن
مجمع الملك فهد لطباعة المصحف الشريف. على من يعيد توزيع تلك المواد التحقق من
شروط المصدر والحصول على الحقوق اللازمة. لا يقدّم هذا الملف إذنًا بإعادة توزيعها.

### التلاوات الصوتية

لا يتضمن المستودع أي ملف صوتي. يجلب التطبيق التلاوات وقائمة القرّاء وجداول مواضع
الآيات من واجهة [MP3Quran](https://www.mp3quran.net/ar/api) حين يشغّلها المستخدم أو
ينزّلها، ويحفظها على جهازه فقط. تنص صفحة الخصوصية في الموقع على السماح لأي زائر أو
مطوّر بنسخ أي مادة واستخدام أي رابط. حقوق التلاوات لأصحابها، ولا تمنح رخصة MIT أي
حق فيها.

## Third-party content

The MIT License covers the project source code and original project assets. It does not grant
rights to the bundled Mushaf page images, Qur'anic text, ayah-coordinate datasets, or third-party
marks shown in publishing materials. Verify the original source terms before redistributing those
materials.

The bundled `quran_content_v2.db` contains **Tafsir Muyassar** version 3.0 and
**Muyassar of Ghareeb Al-Qur'an**, second edition, sourced from the King Fahd Glorious Qur'an
Printing Complex developer platform. The platform describes these resources as provided for use
by developers, researchers, and publishing houses in application content and research.

These texts and the derived database are third-party content and are not covered by the project's
MIT License. This repository grants no additional right to republish the raw materials or relicense
them. Redistributors must review the current source terms and contact the Complex when further
permission clarification is required.

The same database contains **Tafsir al-Sa'di** (*Taysir al-Karim al-Rahman*, by Abd al-Rahman ibn
Nasir al-Sa'di) as published by [QuranEnc](https://quranenc.com) through its API, version 1.0.0
(source last updated 2026-07-27, snapshot taken 2026-11-05 with
`mushaf_app/tools/fetch_quranenc_saadi.py`). QuranEnc allows its content to be downloaded and
republished provided the publisher and source (QuranEnc.com) are credited, the content is not
modified, added to or cut, the version number is stated, the text is kept up to date with the
latest version, and no inappropriate advertising is shown with it. The app shows the text as
published and cites the source and version under it and in every copy or share. This text is
third-party content and is not covered by the project's MIT License.

The repository contains no audio. The app fetches recitations, the reciter list and the ayah timing
tables from the [MP3Quran API](https://www.mp3quran.net/eng/api) when the user plays or downloads
them, and keeps them on the user's device only. MP3Quran's privacy page states that any visitor or
developer may copy any material and use any link. The recitations belong to their owners; the MIT
License grants no rights in them.
