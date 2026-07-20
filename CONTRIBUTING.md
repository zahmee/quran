# المساهمة في قرآن القارئ

شكرًا لاهتمامك. نرحب بإصلاحات الأخطاء وتحسينات تجربة القراءة والتوثيق والاختبارات.

## قبل البدء

- ابحث في Issues لتتأكد من عدم وجود بلاغ مماثل.
- افتح Issue قبل التغييرات الكبيرة أو التي تغيّر بنية البيانات.
- لا ترفع مفاتيح التوقيع أو `keystore.properties` أو `local.properties` أو ملفات APK/AAB.
- لا تضف نصوصًا قرآنية أو صور مصحف من مصدر غير موثق أو من غير حق توزيع واضح.

## إعداد بيئة العمل

1. Fork للمستودع واستنسخ نسختك.
2. افتح مجلد `mushaf_app/` في Android Studio باستخدام JDK 17 وAndroid SDK 36.
3. أنشئ فرعًا محددًا للعمل:

   ```bash
   git switch -c fix/short-description
   ```

## معايير التغيير

- حافظ على اتجاه RTL واختبر النصوص العربية الطويلة.
- استخدم Material 3 ومكونات Compose الدلالية وأضف `contentDescription` للعناصر التفاعلية.
- افصل حالة الواجهة عن طبقة التخزين كلما كان ذلك عمليًا.
- تجنب إضافة أذونات أو اتصالات شبكية من غير حاجة موثقة.
- أضف أو حدّث التوثيق عند تغيير سلوك يراه المستخدم.

## التحقق قبل Pull Request

من داخل `mushaf_app/`:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

ثم تأكد من أن:

- البناء وLint ينجحان.
- لقطات الشاشة مرفقة عند تغيير الواجهة.
- الـ Pull Request يعالج موضوعًا واحدًا ويشرح سبب التغيير.

## English summary

Keep pull requests focused, preserve the app's offline/privacy guarantees, test RTL behavior, and run
`assembleDebug` plus `lintDebug`. Never commit signing credentials or redistribute third-party Mushaf
content without confirming the source terms. By contributing, you agree that your contribution is
provided under the repository's MIT License.
