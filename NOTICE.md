# إشعارات المشروع والمحتوى الخارجي

شفرة **نتلو** والأصول الأصلية الخاصة بالمشروع متاحة بموجب رخصة
[MIT](LICENSE). تسمح الرخصة بالاستخدام والنسخ والتعديل والدمج والنشر والتوزيع، مع
الالتزام بنصها.

## محتوى لا تغطيه رخصة MIT

لا تنقل رخصة MIT إلى المستخدم أي حقوق في المحتوى الخارجي الآتي:

- صور صفحات المصحف داخل `mushaf_app/app/src/main/assets/pages/`.
- نص القرآن الكريم وبيانات إحداثيات الآيات ومشتقاتها.
- أي علامات أو مواد طرف ثالث تظهر في لقطات الشاشة أو ملفات النشر.

تعرض شاشة «المصادر والتراخيص» في التطبيق مصدر كل ذلك، ومعه الخطوط والمكتبات والخدمات المستخدمة، ويُفتح رابطها
من نهاية كل تفسير ومن صفحة «حول التطبيق». والنص القرآني مأخوذ من واجهة Quran.com البرمجية (الإصدار الرابع).

### التفسير الميسر والميسر في غريب القرآن

تتضمن قاعدة البيانات المرفقة
`mushaf_app/app/src/main/assets/databases/quran_content_v4.db`
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

تتضمن قاعدة البيانات نفسها **تفسير السعدي** («تيسير الكريم الرحمن في تفسير كلام المنان» للشيخ عبد
الرحمن بن ناصر السعدي) مأخوذاً من قاعدة بيانات
[مركز تفسير للدراسات القرآنية](https://tafsir.net)
([tafsircenter/tafsir-mcp-data](https://huggingface.co/datasets/tafsircenter/tafsir-mcp-data)، الملف
`quran.db`، جدول `tafsir_saadi`)، برخصة
[المشاع الإبداعي CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

**ما غُيّر في نص المركز**، وتنفّذه الأداة `mushaf_app/tools/saadi_center.py`:

- جُمعت صفوف الآيات في مقاطع كما يقرؤها الكتاب، وحُذف ما كرره التقسيم على الآيات، وعبارة «لم يفسر
  الشيخ» حيث تقع الآية ضمن شرح لعدة آيات.
- حُذف نص الآيات من الصفوف، ويُعرض بدله نص المصحف المعتمد في التطبيق.
- صُححت أخطاء نسخ واقتباسات قرآنية، وحُذفت علامات عمل (ككك، ممم)، بقائمة صريحة في الأداة نفسها.
- أُظهر من حواشي المحقق مصادر الأحاديث والتصحيحات فقط، ولم تُعرض فروق المخطوطات.

**تكملات من مصدر ثانٍ:** أربعة مواضع من الكتاب لا توجد في نسخة المركز (البقرة ١٠١ و١٤٣ و١٨١، والنساء
١١٧، وعددها ٢٥٥ كلمة) أُخذت بنصها من
[موسوعة القرآن الكريم](https://quranenc.com)، الإصدار 1.0.0 (نُسخ في 2026-11-05 بالأداة
`mushaf_app/tools/fetch_quranenc_saadi.py`)، وتُعرض في التطبيق موسومة بأنها تكملة منها. واستُعمل تقسيم
الموسوعة للآيات إلى مقاطع دون نصها.

### أسباب النزول

تتضمن قاعدة البيانات نفسها **أسباب النزول** لـ٢٠٠ آية من قاعدة بيانات مركز تفسير (جدول
`ayah_content_nozool`)، بالرخصة نفسها. لا تذكر وثائق القاعدة الكتاب الذي نُقلت منه. حُذفت الوسوم وأكثر حواشي
المحقق وأُبقي تخريج الأحاديث، وحُذف صفّان منسوبان إلى غير آيتهما هما نسختان من صفّين آخرين (ص ١ نسخة من يس ٧٨،
وأول آل عمران ١٨١ نسخة من آل عمران ١٧٣)، وتنفّذ ذلك الأداة `mushaf_app/tools/nozool_center.py`.

### الكتب التي تُنزَّل عند الطلب

تفسير ابن كثير («تفسير القرآن العظيم») وتفسير الطبري («جامع البيان عن تأويل آي القرآن») وتفسير البغوي
(«معالم التنزيل») وإعراب القرآن الكريم ليست في التطبيق ولا في هذا المستودع؛ تُبنى من قاعدة بيانات مركز تفسير
نفسها بالأدوات `mushaf_app/tools/build_packs.py` و`pack_books.py` و`pack_common.py`، وتُنشر ملفاتٍ في إصدارات
المستودع [zahmee/natlu-data](https://github.com/zahmee/natlu-data) بالرخصة نفسها CC BY 4.0. وما غُيّر فيها:
حُذفت الوسوم وعلامات العمل وأكثر حواشي المحقق (أُبقي تخريج الأحاديث والتصحيحات)، وجُمعت الآيات التي كررت القاعدة
شرحها الواحد تحت كل منها، وأُغلقت حواشٍ لم تُغلق في المصدر، ووُسمت ثمانية مواضع قُطع نصها في المصدر (أربعة في
البغوي وأربعة في الإعراب). قائمة التصحيحات كاملة في `pack_books.py` وفي ملف `CORRECTIONS.md` المنشور مع الكتب.

يذكر التطبيق ذلك كله في شاشة «المصادر والتراخيص»، ويضيف إلى كل نسخ أو مشاركة سطراً بالمصدر والرخصة ووقوع التعديل،
لأن النص المنسوخ يخرج من التطبيق. المشروع غير تجاري، ولا تُعرض
مع هذا المحتوى إعلانات ولا مزايا مدفوعة. هذه النصوص محتوى خارجي لا تغطيه رخصة MIT الخاصة بشفرة المشروع.

### المختصر في تفسير القرآن الكريم

وتتضمن قاعدة البيانات نفسها **المختصر في تفسير القرآن الكريم** من قاعدة البيانات نفسها لمركز تفسير للدراسات
القرآنية (جدول `QuranTafseer`، العمود `Mukhtasarar`)، بالرخصة نفسها
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

**ما غُيّر فيه**، وتنفّذه الأداة `mushaf_app/tools/mukhtasar_center.py`: تُعرض علامات الكلمات المفسَّرة إبرازاً،
وأُصلحت علامات مختلة ومسافة زائدة في مواضع يسيرة بقائمة صريحة في الأداة. ولم تُغيَّر كلماته. ويذكر التطبيق ذلك في شاشة
«المصادر والتراخيص» وفي كل نسخ أو مشاركة.

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
materials. The app's "Sources and licences" screen names all of them, along with the fonts, libraries
and services it uses; it opens from the end of each tafsir and from the About screen. The Qur'anic text
comes from the Quran.com API v4.

The bundled `quran_content_v3.db` contains **Tafsir Muyassar** version 3.0 and
**Muyassar of Ghareeb Al-Qur'an**, second edition, sourced from the King Fahd Glorious Qur'an
Printing Complex developer platform. The platform describes these resources as provided for use
by developers, researchers, and publishing houses in application content and research.

These texts and the derived database are third-party content and are not covered by the project's
MIT License. This repository grants no additional right to republish the raw materials or relicense
them. Redistributors must review the current source terms and contact the Complex when further
permission clarification is required.

The same database contains **Tafsir al-Sa'di** (*Taysir al-Karim al-Rahman*, by Abd al-Rahman ibn
Nasir al-Sa'di) from the database of the
[Tafsir Center for Quranic Studies](https://tafsir.net)
([tafsircenter/tafsir-mcp-data](https://huggingface.co/datasets/tafsircenter/tafsir-mcp-data), file
`quran.db`, table `tafsir_saadi`), licensed under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

**Changes made to the Center's text**, by `mushaf_app/tools/saadi_center.py`: per-ayah rows are
grouped into the book's passages, dropping what the per-ayah split repeated and the sentence "the
Sheikh did not explain this" where the ayah sits inside a multi-ayah explanation; the ayah text in
the rows is replaced by the app's approved mushaf text; copying slips, misquoted Quran and working
marks are corrected from an explicit list in that tool; of the editor's notes only hadith sources
and corrections are shown, not manuscript variants.

**Supplements from a second source:** four passages of the book missing from the Center's copy (2:101,
2:143, 2:181 and 4:117, 255 words) are taken verbatim from [QuranEnc](https://quranenc.com), version
1.0.0 (snapshot taken 2026-11-05 with `mushaf_app/tools/fetch_quranenc_saadi.py`), and are labelled
as such in the app. QuranEnc's division of the ayahs into passages is also used, but not its wording.

The app states all this on its "Sources and licences" screen, and adds a line with the source, the
licence and the fact of the changes to every copy or share, since that text leaves the app. The project
is non-commercial, and no advertising or paid feature accompanies this content. These texts are
third-party content and are not covered by the project's MIT License.

The database also contains **al-Mukhtasar fi Tafsir al-Quran al-Karim** from the same Tafsir Center
database (table `QuranTafseer`, column `Mukhtasarar`), under the same
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) licence. **Changes made**, by
`mushaf_app/tools/mukhtasar_center.py`: the marks around explained words are shown as highlights, and
a few broken marks and one extra space are repaired from an explicit list in that tool. Its words are
unchanged. The app states this on its "Sources and licences" screen and in every copy or share.

The repository contains no audio. The app fetches recitations, the reciter list and the ayah timing
tables from the [MP3Quran API](https://www.mp3quran.net/eng/api) when the user plays or downloads
them, and keeps them on the user's device only. MP3Quran's privacy page states that any visitor or
developer may copy any material and use any link. The recitations belong to their owners; the MIT
License grants no rights in them.
