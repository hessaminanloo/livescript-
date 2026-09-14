package ai.z.livescript.agents

/**
 * Lightweight prompt templates for the on-device model. These are intentionally
 * simpler/shorter than what the server-side agents likely do (bigger cloud model,
 * probably multi-round debate/refinement — hence "rounds" in the API response) —
 * a 1-4B on-device model needs tight, single-pass instructions to stay coherent.
 */
object AgentPrompts {

    fun buildPrompt(type: AgentType, transcript: String, topic: String): String {
        val header = "موضوع جلسه: $topic\nمتن رونوشت جلسه:\n\"\"\"\n$transcript\n\"\"\"\n\n"
        val instruction = when (type) {
            AgentType.SUMMARY -> "یک خلاصه کوتاه و دقیق (حداکثر ۵ جمله) از این جلسه به فارسی بنویس."
            AgentType.DISCUSSION -> "مهم‌ترین نکات بحث‌شده در این جلسه را به صورت فهرست بنویس."
            AgentType.ACTIONS -> "اقدامات و کارهای مشخصی که باید انجام شود را به صورت فهرست بنویس. اگر مسئول یا ددلاینی ذکر شده، بیاور."
            AgentType.QUESTIONS -> "سوالات مهمی که در جلسه مطرح شده یا باید پیگیری شود را فهرست کن."
            AgentType.TRANSLATION -> "این متن را به انگلیسی روان ترجمه کن."
            AgentType.SENTIMENT -> "لحن و احساس کلی جلسه را (مثبت/منفی/خنثی/نگران/هیجان‌زده و...) در یک پاراگراف کوتاه توصیف کن."
            AgentType.RISKS -> "ریسک‌ها و نگرانی‌های احتمالی که در این جلسه مطرح یا قابل استنباط است را فهرست کن."
            AgentType.PRIORITIES -> "بر اساس محتوای جلسه، اولویت‌های اصلی را به ترتیب اهمیت فهرست کن."
            AgentType.IDEAS -> "ایده‌های جدید یا پیشنهادهایی که در جلسه مطرح شده را فهرست کن."
            AgentType.TIMELINE -> "هر گونه زمان‌بندی، تاریخ یا ددلاین ذکرشده در جلسه را استخراج و فهرست کن."
            AgentType.DEVILS_ADVOCATE -> "نقش وکیل شیطان را بازی کن: مهم‌ترین نقاط ضعف یا مفروضات نادیده‌گرفته‌شده در تصمیمات این جلسه را با لحنی انتقادی اما سازنده بیان کن."
        }
        return header + instruction
    }
}
