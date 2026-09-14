# لایو اسکریپت — نسخه‌ی ۲ (نیتیو، اصلاح‌شده)

این یک پروژه‌ی کامل Android Studio (Kotlin) است، بازنویسی‌شده از روی APK قبلی، برای رفع باگ‌ها
و اضافه‌کردن تشخیص گفتار و ایجنت‌های آفلاین. **این محیطی که من الان توش کار می‌کنم SDK اندروید
و NDK نداره، پس نمی‌تونم این پروژه رو خودم build و تست کنم** — کد رو با دقت و مطابق APIهای مستندِ
هر کتابخونه نوشتم، ولی قبل از استفاده‌ی واقعی حتماً توی Android Studio باز و build/تست کنید.

## چی درست شد نسبت به نسخه‌ی قبلی؟

| مشکل قبلی | راه‌حل در این نسخه |
|---|---|
| دکمه‌ی «ذخیره جلسه» فقط toast می‌زد و هیچی ذخیره نمی‌کرد | دیتابیس Room واقعی (`data/`) + صفحه‌ی تاریخچه (`HistoryActivity`) |
| فقط `SpeechRecognizer` داخلی اندروید (دقت پایین) | پایپ‌لاین هیبریدی: **Vosk** برای caption زنده + **Whisper.cpp** برای پردازش نهایی دقیق‌تر هر جمله (`stt/HybridSpeechController.kt`) |
| ایجنت‌ها فقط سمت سرور، بدون حالت آفلاین | `agents/OfflineAgentRunner.kt` با یک LLM کوچیک روی گوشی (MediaPipe LLM Inference)، به‌علاوه‌ی سوییچ خودکار آنلاین/آفلاین بر اساس اتصال اینترنت (`ConnectivityObserver`) |
| `debuggable="true"` در نسخه‌ی release | در `app/build.gradle` فقط برای build type دیباگ فعاله |
| بدون تاریخچه‌ی جلسات | لیست کامل جلسات ذخیره‌شده با قابلیت حذف |

## معماری

```
MainActivity ──uses──▶ HybridSpeechController ──uses──▶ VoskSpeechEngine (real-time)
                                               └────────▶ WhisperTranscriber (JNI, دقت بالا، پس از هر جمله)
                                               └────────▶ AndroidFallbackEngine (اگر مدل‌ها نصب نبودن)

MainActivity ──uses──▶ RemoteAgentRunner  (POST به {serverUrl}/api/agents/run-all — همون API قبلی)
             ──uses──▶ OfflineAgentRunner (LLM محلی، وقتی آفلاین یا سوییچ اجباری فعاله)

MainActivity ──uses──▶ SessionRepository ──▶ Room (`sessions` table) ──▶ HistoryActivity
```

## مدل‌های آفلاین — نصب و راه‌اندازی (این بخش حیاتیه)

هیچ‌کدوم از مدل‌ها داخل کد من نیستن (حجم‌شون از چند ده مگابایت تا بیش از ۱ گیگابایت هست و
نمی‌شه توی پیام/کد جا داد). سه مدل جدا لازم داری:

### ۱. Vosk (تشخیص گفتار زنده، فارسی)
```
1. از https://alphacephei.com/vosk/models یک مدل فارسی دانلود کن
   (پیشنهاد شروع: vosk-model-small-fa-0.4 — سبک و سریع)
2. آنزیپ کن و کل پوشه رو با نام "model-fa" بریز توی:
   app/src/main/assets/model-fa/
```

### ۲. Whisper.cpp (پردازش نهایی دقیق‌تر هر جمله)
```
# سورس whisper.cpp رو به‌صورت submodule اضافه کن:
git submodule add https://github.com/ggerganov/whisper.cpp app/src/main/cpp/whisper.cpp

# یک مدل ggml کوانتیزه دانلود کن (هرچی بزرگ‌تر، دقیق‌تر ولی کندتر):
#   ggml-tiny.bin  (~75MB،  سریع، دقت متوسط)
#   ggml-base.bin  (~140MB، تعادل خوب)
#   ggml-small.bin (~460MB، دقیق‌تر، پیشنهاد پیش‌فرض کد)
# از https://huggingface.co/ggerganov/whisper.cpp دانلود کن و بذار توی:
app/src/main/assets/whisper/ggml-small.bin
# (یا مسیر رو توی WhisperTranscriber.loadModelBlocking عوض کن)
```
بعد از اضافه‌کردن submodule، Gradle sync کن — `CMakeLists.txt` خودش whisper.cpp رو پیدا و build می‌کنه.

### ۳. مدل زبانی کوچیک برای ایجنت‌های آفلاین (MediaPipe LLM Inference)
```
از Google AI Edge / Kaggle Models یک مدل .task دانلود کن، مثلاً:
  - Gemma 3 1B-it (int4)   → سریع‌تر، حجم کمتر (~۵۰۰ مگابایت)
  - Gemma 2 2B-it (int4)   → کیفیت بهتر، کندتر (~۱.۳ گیگابایت)
لینک: https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference

چون این فایل‌ها خیلی بزرگن، توی APK باندل نکن — روی دستگاه در مسیر زیر کپی/دانلودش کن:
  <external files dir>/models/gemma-offline.task
(مثلاً با adb push, یا یک Activity دانلودر جدا که خودت اضافه می‌کنی)
```

⚠️ **نکته‌ی مهم درباره‌ی کیفیت**: یک مدل ۱ تا ۲ میلیاردی روی گوشی، در قیاس با هر مدل بزرگ
سمت سرور (که ظاهراً چندمرحله‌ای/چند-round هم کار می‌کنه، طبق فیلد `rounds` در پاسخ API)
خروجی کوتاه‌تر و کم‌نیانس‌تری می‌ده. این یک محدودیت واقعی سخت‌افزاریه، نه باگ.
پشتیبانی این مدل‌ها از فارسی رو حتماً خودتون قبل از production تست کنید.

## چیزهایی که هنوز باید خودتون تکمیل کنید

- **آیکون واقعی اپ** — الان یه آیکون ساده‌ی وکتور جایگزین گذاشتم؛ عوضش کنید.
- **دانلودر مدل داخل اپ** — الان باید مدل‌ها رو دستی (adb push یا asset) بذارید؛ برای تجربه‌ی
  کاربری بهتر، یک صفحه‌ی «دانلود مدل‌ها» با progress bar اضافه کنید (خصوصاً برای مدل ۱+ گیگابایتی LLM).
- **تست واقعی روی دستگاه** — من نمی‌تونم build/run کنم، پس مطمئن بشید:
  - نسخه‌های کتابخونه‌ها (Vosk، MediaPipe tasks-genai، whisper.cpp API) با آخرین release سازگارن
    (این کتابخونه‌ها گاهی API عوض می‌کنن).
  - مصرف باتری/حرارت حین اجرای هم‌زمان Vosk + Whisper رو تست کنید — روی گوشی‌های ضعیف‌تر ممکنه کند باشه.
- **صفحه‌ی تنظیمات برای انتخاب سایز مدل Whisper/LLM** (الان هاردکد شده روی small / gemma-offline.task).

## اجرا

```
Android Studio → Open → این پوشه → Sync Gradle → مطمئن بشید NDK نصبه (برای build native whisper) → Run
```
