package com.bera.pinkfluffykeyboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PinkKeyboardService : InputMethodService() {

    private data class Key(val normal: String, val shifted: String = normal)
    private enum class Shift { OFF, ONCE, LOCK }
    private enum class Lang { EN, HI, HG }

    private val textPink = Color.rgb(122, 48, 104)
    private var symbols = false
    private var emoji = false
    private var emojiCat = 1
    private var lang = Lang.EN
    private var shift = Shift.OFF
    private var lastShiftTap = 0L
    private var pickingLang = false
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var lastComposeTime = 0L

    // English word suggestions: letters typed so far in the current word
    private val wordBuf = StringBuilder()
    private var lastKeyTime = 0L
    @Volatile private var words: List<String> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    // Hinglish: roman letters typed so far for the current word
    private val composing = StringBuilder()

    private val letterKeys = mutableListOf<Pair<TextView, Key>>()
    private val stripViews = mutableListOf<TextView>()
    private var shiftKeyView: TextView? = null

    // ---------------------------------------------------------------- layouts

    private fun latin(s: String) = s.map { Key(it.toString(), it.toString().uppercase()) }

    private val enRows = listOf(latin("qwertyuiop"), latin("asdfghjkl"), latin("zxcvbnm"))

    // Hindi InScript style layout (same idea as Gboard Hindi)
    private val hiRows = listOf(
        listOf(
            Key("ौ", "औ"), Key("ै", "ऐ"), Key("ा", "आ"), Key("ी", "ई"), Key("ू", "ऊ"),
            Key("ब", "भ"), Key("ह", "ङ"), Key("ग", "घ"), Key("द", "ध"), Key("ज", "झ"), Key("ड", "ढ")
        ),
        listOf(
            Key("ो", "ओ"), Key("े", "ए"), Key("्", "अ"), Key("ि", "इ"), Key("ु", "उ"),
            Key("प", "फ"), Key("र", "ऋ"), Key("क", "ख"), Key("त", "थ"), Key("च", "छ"), Key("ट", "ठ")
        ),
        listOf(
            Key("ॉ", "ऑ"), Key("ं", "ँ"), Key("म", "ण"), Key("न", "ञ"), Key("व", "त्र"),
            Key("ल", "ज्ञ"), Key("स", "श"), Key("य", "़"), Key("ष", "क्ष")
        )
    )

    private val symRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("~", "*", "\"", "'", ":", ";", "!", "?", "⌫")
    )

    // ---------------------------------------------------------------- emoji data

    private val emojiCats = listOf(
        "🕘" to "",
        "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 😐 😑 😶 😏 😒 🙄 😬 😮 😯 😲 😳 🥺 😢 😭 😤 😠 😡 🤬 😱 😨 😰 😓 😴 🤤 😷 🤒 🤕 🤢 🤮 🥵 🥶 😎 🤓 🥳",
        "👍" to "👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 👋 🤝 🙏 👏 🙌 👐 🤲 💪 🙋 🤷 🤦 💃 🕺 👀 👅 👄 👶 👦 👧 👨 👩 👴 👵 👮 👷 💂 🎅 👸 🤴",
        "🐰" to "🐰 🐇 🐶 🐱 🐭 🐹 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦉 🦋 🐛 🐝 🐞 🐢 🐍 🐙 🐠 🐬 🐳 🌸 🌹 🌺 🌻 🌼 🌷 🌱 🌿 🍀 🌈 ☀️ 🌙 ⭐ 🌟 ☁️ ⚡ 🔥 ❄️ 💧",
        "🍔" to "🍎 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🌽 🥕 🍞 🧀 🍳 🥞 🍔 🍟 🍕 🌭 🌮 🌯 🍝 🍜 🍛 🍚 🍣 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🥤 🍺 🍷",
        "⚽" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏏 🏸 🎱 🏓 🥊 🎯 🎮 🎲 🎵 🎶 🎤 🎧 🎸 🎹 🥁 🎬 🎨 🏆 🥇 🎉 🎊 🎁 🎈 🎀",
        "🚗" to "🚗 🚕 🚌 🚓 🚑 🚒 🚚 🚜 🏍️ 🚲 🛵 ✈️ 🚀 🚁 🚂 🚢 ⛵ 🏠 🏢 🏫 🏥 🏪 ⛪ 🕌 🗺️ 🏖️ 🏔️ 🌋 🌅 🌃 🌉",
        "❤️" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 ✅ ❌ ❓ ❗ ✨ 💫 💥 💢 💤 ➕ ➖ ✔️ ☑️ 🔔 🔒 🔑 📱 💻 📷 💡 💰 🕐 📞 ✉️"
    )

    // ---------------------------------------------------------------- Hinglish engine

    private val dict = mapOf(
        "main" to "मैं", "mai" to "मैं", "mein" to "में", "hai" to "है", "hain" to "हैं",
        "hoon" to "हूँ", "hun" to "हूँ", "hu" to "हूँ", "tha" to "था", "thi" to "थी", "the" to "थे",
        "kya" to "क्या", "kyun" to "क्यों", "kyon" to "क्यों", "kyu" to "क्यों",
        "kaise" to "कैसे", "kaisa" to "कैसा", "kaisi" to "कैसी", "kahan" to "कहाँ", "kaun" to "कौन",
        "kuch" to "कुछ", "koi" to "कोई", "kisi" to "किसी", "kitna" to "कितना", "kitni" to "कितनी",
        "ki" to "की", "ka" to "का", "ke" to "के", "ko" to "को", "se" to "से", "par" to "पर", "pe" to "पे",
        "bhi" to "भी", "hi" to "ही", "to" to "तो", "toh" to "तो",
        "nahi" to "नहीं", "nahin" to "नहीं", "nai" to "नहीं", "haan" to "हाँ", "ha" to "हाँ",
        "aur" to "और", "ya" to "या", "lekin" to "लेकिन", "agar" to "अगर", "jab" to "जब",
        "abhi" to "अभी", "phir" to "फिर", "fir" to "फिर", "bahut" to "बहुत", "bohot" to "बहुत",
        "thoda" to "थोड़ा", "zyada" to "ज़्यादा", "jyada" to "ज़्यादा",
        "accha" to "अच्छा", "achha" to "अच्छा", "acha" to "अच्छा", "theek" to "ठीक", "thik" to "ठीक",
        "sahi" to "सही", "galat" to "गलत",
        "mera" to "मेरा", "meri" to "मेरी", "mere" to "मेरे", "tera" to "तेरा", "teri" to "तेरी",
        "tumhara" to "तुम्हारा", "tumhari" to "तुम्हारी", "tumhare" to "तुम्हारे",
        "hamara" to "हमारा", "hamari" to "हमारी", "aapka" to "आपका", "aapki" to "आपकी", "aapke" to "आपके",
        "apna" to "अपना", "apni" to "अपनी", "apne" to "अपने",
        "tu" to "तू", "tujhe" to "तुझे", "mujhe" to "मुझे", "hame" to "हमें", "humein" to "हमें",
        "wo" to "वो", "woh" to "वो", "vo" to "वो", "ye" to "ये", "yeh" to "ये", "yah" to "यह",
        "wahan" to "वहाँ", "yahan" to "यहाँ", "kahi" to "कहीं", "kabhi" to "कभी",
        "yaar" to "यार", "bhai" to "भाई", "bhaiya" to "भैया", "behen" to "बहन", "didi" to "दीदी",
        "mummy" to "मम्मी", "papa" to "पापा", "beta" to "बेटा", "beti" to "बेटी",
        "ladka" to "लड़का", "ladki" to "लड़की", "ghar" to "घर", "paani" to "पानी", "pani" to "पानी",
        "pyar" to "प्यार", "ishq" to "इश्क़", "zindagi" to "ज़िंदगी", "kitab" to "किताब",
        "namaste" to "नमस्ते", "namaskar" to "नमस्कार", "shukriya" to "शुक्रिया",
        "dhanyavad" to "धन्यवाद", "kripya" to "कृपया", "sorry" to "सॉरी", "please" to "प्लीज़",
        "thanks" to "थैंक्स", "hello" to "हैलो", "good" to "गुड", "morning" to "मॉर्निंग",
        "night" to "नाइट", "phone" to "फ़ोन", "mobile" to "मोबाइल", "photo" to "फ़ोटो",
        "video" to "वीडियो", "school" to "स्कूल", "college" to "कॉलेज", "india" to "इंडिया",
        "gaya" to "गया", "gayi" to "गई", "gaye" to "गए", "aaya" to "आया", "aayi" to "आई", "aaye" to "आए",
        "hua" to "हुआ", "hui" to "हुई", "hue" to "हुए", "wala" to "वाला", "wali" to "वाली",
        "sab" to "सब", "sabhi" to "सभी", "log" to "लोग", "dil" to "दिल", "kaam" to "काम",
        "subah" to "सुबह", "raat" to "रात", "aaj" to "आज", "kal" to "कल", "chai" to "चाय"
    )

    private val consTokens = listOf(
        "ksh" to "क्ष", "cchh" to "च्छ", "cch" to "च्छ", "chh" to "छ",
        "Th" to "ठ", "Dh" to "ढ", "Sh" to "ष",
        "kh" to "ख", "gh" to "घ", "ch" to "च", "jh" to "झ", "th" to "थ", "dh" to "ध",
        "ph" to "फ", "bh" to "भ", "sh" to "श", "gy" to "ज्ञ",
        "k" to "क", "g" to "ग", "c" to "क", "j" to "ज", "T" to "ट", "D" to "ड", "N" to "ण", "S" to "ष",
        "t" to "त", "d" to "द", "n" to "न", "p" to "प", "f" to "फ़", "b" to "ब", "m" to "म",
        "y" to "य", "r" to "र", "l" to "ल", "v" to "व", "w" to "व", "s" to "स", "h" to "ह",
        "z" to "ज़", "q" to "क़", "x" to "क्स"
    )

    private val vowelKeys = listOf("aa", "ai", "au", "ee", "ii", "oo", "uu", "a", "i", "u", "e", "o")
    private val vowelIndep = mapOf(
        "aa" to "आ", "ai" to "ऐ", "au" to "औ", "ee" to "ई", "ii" to "ई", "oo" to "ऊ", "uu" to "ऊ",
        "a" to "अ", "i" to "इ", "u" to "उ", "e" to "ए", "o" to "ओ"
    )
    private val vowelMatra = mapOf(
        "aa" to "ा", "ai" to "ै", "au" to "ौ", "ee" to "ी", "ii" to "ी", "oo" to "ू", "uu" to "ू",
        "a" to "", "i" to "ि", "u" to "ु", "e" to "े", "o" to "ो"
    )

    private fun translit(src: String, longA: Boolean): String {
        val out = StringBuilder()
        var i = 0
        var prevCons = false
        while (i < src.length) {
            val v = vowelKeys.firstOrNull { src.startsWith(it, i) }
            if (v != null) {
                val atEnd = i + v.length == src.length
                if (prevCons) {
                    val key = when {
                        v == "a" && (longA || atEnd) -> "aa"
                        v == "i" && atEnd -> "ee"
                        else -> v
                    }
                    out.append(vowelMatra[key] ?: "")
                } else {
                    out.append(vowelIndep[v] ?: "")
                }
                prevCons = false
                i += v.length
                continue
            }
            val c = consTokens.firstOrNull { src.startsWith(it.first, i) }
            if (c != null) {
                if (prevCons) out.append("\u094D")
                out.append(c.second)
                prevCons = true
                i += c.first.length
                continue
            }
            out.append(src[i])
            prevCons = false
            i++
        }
        return out.toString()
    }

    private fun candidates(roman: String): List<String> {
        val hasUpper = roman.any { it.isUpperCase() }
        val base = if (roman.length > 1 && roman.all { it.isUpperCase() }) roman.lowercase() else roman
        val clean = base.map { if (it.isUpperCase() && it !in "TDNS") it.lowercase() else it.toString() }
            .joinToString("")
        val list = LinkedHashSet<String>()
        if (!hasUpper) dict[roman.lowercase()]?.let { list.add(it) }
        list.add(translit(clean, false))
        if (!hasUpper && roman.length >= 2) {
            val low = roman.lowercase()
            dict.entries.filter { it.key.startsWith(low) && it.key != low }
                .take(2).forEach { list.add(it.value) }
        }
        list.add(translit(clean, true))
        val out = LinkedHashSet<String>(list.take(4))
        out.add(roman)
        return out.toList()
    }

    // ---------------------------------------------------------------- English word suggestions

    private fun osa(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, d[i - 2][j - 2] + 1)
                }
                d[i][j] = v
            }
        }
        return d[a.length][b.length]
    }

    private fun matchCase(typed: String, sug: String): String {
        var r = sug
        if (typed.length > 1 && typed.all { it.isUpperCase() }) {
            r = sug.uppercase()
        } else if (typed[0].isUpperCase()) {
            r = sug.replaceFirstChar { it.uppercase() }
        }
        if (r == "i") r = "I"
        if (r.startsWith("i'")) r = "I" + r.substring(1)
        return r
    }

    // completions first (common words first), then close spellings for typos
    private fun suggestWords(typed: String): List<String> {
        val w = words
        if (w.isEmpty() || typed.isEmpty()) return emptyList()
        val t = typed.lowercase()
        val out = ArrayList<String>()
        for (x in w) {
            if (x.length > t.length && x.startsWith(t)) {
                out.add(x)
                if (out.size >= 5) break
            }
        }
        if (out.size < 3 && t.length >= 3) {
            val maxD = if (t.length >= 5) 2 else 1
            val found = ArrayList<Triple<Int, Int, String>>()
            val limit = minOf(w.size, 40000)
            for (idx in 0 until limit) {
                val x = w[idx]
                if (x == t || x[0] != t[0]) continue
                if (Math.abs(x.length - t.length) > 2) continue
                val d = osa(t, x.substring(0, minOf(x.length, t.length + 1)))
                val d2 = osa(t, x)
                val best = minOf(d, d2)
                if (best <= maxD && x !in out) found.add(Triple(best, idx, x))
            }
            found.sortWith(compareBy<Triple<Int, Int, String>>({ it.first }, { it.second }))
            for (f in found) {
                if (out.size >= 5) break
                if (f.third !in out) out.add(f.third)
            }
        }
        return out.map { matchCase(typed, it) }
    }

    private fun trackWord(s: String) {
        lastKeyTime = SystemClock.uptimeMillis()
        if (lang != Lang.EN) {
            wordBuf.setLength(0)
            return
        }
        val c = s[0]
        if (s.length == 1 && (c in 'a'..'z' || c in 'A'..'Z')) {
            wordBuf.append(s)
        } else if (s == "'" && wordBuf.isNotEmpty()) {
            wordBuf.append(s)
        } else {
            wordBuf.setLength(0)
        }
    }

    // ---------------------------------------------------------------- lifecycle

    private fun prefs() = getSharedPreferences("kb", MODE_PRIVATE)

    override fun onCreate() {
        super.onCreate()
        lang = runCatching { Lang.valueOf(prefs().getString("lang", "EN") ?: "EN") }.getOrDefault(Lang.EN)
        Thread {
            try {
                words = assets.open("words.txt").bufferedReader().readLines()
            } catch (e: Exception) {
            }
        }.start()
    }

    override fun onCreateInputView(): View = buildKeyboard()

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composing.setLength(0)
        wordBuf.setLength(0)
        pickingLang = false
        if (symbols || emoji) {
            symbols = false
            emoji = false
            setInputView(buildKeyboard())
        }
        shift = Shift.OFF
        autoShift()
        updateStrip()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finishComposing()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // user moved the cursor somewhere else -> stop composing this word
        if (composing.isNotEmpty() &&
            SystemClock.uptimeMillis() - lastComposeTime > 250 &&
            (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            composing.setLength(0)
            currentInputConnection?.finishComposingText()
            updateStrip()
        }
        if (wordBuf.isNotEmpty() && SystemClock.uptimeMillis() - lastKeyTime > 250) {
            wordBuf.setLength(0)
            updateStrip()
        }
    }

    // ---------------------------------------------------------------- UI building

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bmp(id: Int) = BitmapFactory.decodeResource(resources, id)
    private val imgNormal by lazy { bmp(R.drawable.key_normal) }
    private val imgWide by lazy { bmp(R.drawable.key_wide) }
    private val imgSpace by lazy { bmp(R.drawable.key_space) }
    private val imgAccent by lazy { bmp(R.drawable.key_accent) }
    private val imgBoard by lazy { bmp(R.drawable.board_bg) }

    private fun newRow(sidePad: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(sidePad, dp(2), sidePad, dp(3))
    }

    private fun keyParams(weight: Float) = LinearLayout.LayoutParams(0, dp(46), weight).apply {
        setMargins(dp(2), dp(1), dp(2), dp(1))
    }

    private fun buildKeyboard(): View {
        letterKeys.clear()
        stripViews.clear()
        shiftKeyView = null
        pickingLang = false

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(7), dp(5), dp(8))
            background = FurDrawable(imgBoard, dp(16).toFloat(), true)
        }

        if (emoji) {
            buildEmojiPanel(root)
            return root
        }

        // suggestion strip
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        repeat(5) {
            val t = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextColor(textPink)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(4), dp(5), dp(4), dp(5))
            }
            strip.addView(t, LinearLayout.LayoutParams(0, dp(38), 1f))
            stripViews.add(t)
        }
        root.addView(strip)

        if (symbols) {
            symRows.forEach { chars ->
                val row = newRow(0)
                chars.forEach { label ->
                    if (label == "⌫") {
                        row.addView(makeBackspace(), keyParams(1.25f))
                    } else {
                        row.addView(makeKey(label, imgNormal, 21f) { typeText(label) }, keyParams(1f))
                    }
                }
                root.addView(row)
            }
        } else {
            val layout = if (lang == Lang.HI) hiRows else enRows
            layout.forEachIndexed { index, keys ->
                val row = newRow(if (lang != Lang.HI && index == 1) dp(11) else 0)
                if (index == 2) row.addView(makeShift(), keyParams(1.25f))
                keys.forEach { k -> row.addView(makeLetter(k), keyParams(1f)) }
                if (index == 2) row.addView(makeBackspace(), keyParams(1.25f))
                root.addView(row)
            }
        }

        // bottom row
        val bottom = newRow(0)
        val modeLabel = if (symbols) (if (lang == Lang.HI) "अआ" else "ABC") else "123"
        bottom.addView(makeKey(modeLabel, imgNormal, 16f) {
            finishComposing()
            symbols = !symbols
            setInputView(buildKeyboard())
            autoShift()
        }, keyParams(1f))
        bottom.addView(makeKey("☺", imgNormal, 16f) {
            finishComposing()
            emoji = true
            emojiCat = if (recents().isEmpty()) 1 else 0
            setInputView(buildKeyboard())
        }, keyParams(1f))
        bottom.addView(makeKey(",", imgNormal, 21f) { typeText(",") }, keyParams(1f))
        val spaceLabel = when (lang) {
            Lang.EN -> "English"
            Lang.HI -> "हिन्दी"
            Lang.HG -> "Hinglish"
        }
        val space = makeKey(spaceLabel, imgSpace, 11f, AudioManager.FX_KEYPRESS_SPACEBAR) { typeText(" ") }
        space.setOnLongClickListener {
            feedback()
            pickingLang = true
            updateStrip()
            true
        }
        bottom.addView(space, keyParams(4.2f))
        val period = if (lang == Lang.HI && !symbols) "।" else "."
        bottom.addView(makeKey(period, imgNormal, 21f) { typeText(period) }, keyParams(1f))
        bottom.addView(makeKey("↵", imgWide, 16f) { handleEnter() }, keyParams(1.2f))
        root.addView(bottom)

        refreshLetters()
        updateStrip()
        return root
    }

    private fun buildEmojiPanel(root: LinearLayout) {
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        emojiCats.forEachIndexed { idx, c ->
            val t = TextView(this).apply {
                text = c.first
                textSize = 18f
                gravity = Gravity.CENTER
                alpha = if (idx == emojiCat) 1f else 0.45f
                setOnClickListener {
                    emojiCat = idx
                    setInputView(buildKeyboard())
                }
            }
            tabs.addView(t, LinearLayout.LayoutParams(0, dp(38), 1f))
        }
        root.addView(tabs)

        val items = if (emojiCat == 0) recents()
        else emojiCats[emojiCat].second.split(" ").filter { it.isNotEmpty() }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.chunked(8).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0 until 8) {
                val e = chunk.getOrNull(j)
                val tv = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 24f
                    if (e != null) {
                        text = e
                        setOnClickListener {
                            feedback()
                            typeEmoji(e)
                        }
                    }
                }
                row.addView(tv, LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            grid.addView(row)
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(grid)
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)))

        val bottom = newRow(0)
        bottom.addView(makeKey("ABC", imgNormal, 16f) {
            emoji = false
            setInputView(buildKeyboard())
            autoShift()
        }, keyParams(1.3f))
        bottom.addView(makeKey("", imgSpace, 16f) { typeText(" ") }, keyParams(4f))
        bottom.addView(makeBackspace(), keyParams(1.3f))
        bottom.addView(makeKey("↵", imgWide, 16f) { handleEnter() }, keyParams(1.3f))
        root.addView(bottom)
    }

    private fun makeKey(
        label: String, bg: Bitmap, size: Float,
        fx: Int = AudioManager.FX_KEYPRESS_STANDARD, onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = size
            setTextColor(textPink)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = FurDrawable(bg)
            setOnClickListener {
                feedback(fx)
                onClick()
            }
        }

    private fun makeLetter(k: Key): TextView {
        val tv = makeKey(k.normal, imgNormal, if (lang == Lang.HI) 19f else 21f) {
            onLetter(if (shift != Shift.OFF) k.shifted else k.normal)
        }
        letterKeys.add(Pair(tv, k))
        return tv
    }

    private fun makeShift(): TextView {
        val tv = makeKey("⇧", imgWide, 18f) { onShiftTap() }
        shiftKeyView = tv
        return tv
    }

    private fun makeBackspace(): TextView {
        val tv = makeKey("⌫", imgWide, 16f) { }
        val repeat = object : Runnable {
            override fun run() {
                deleteOne()
                handler.postDelayed(this, 50)
            }
        }
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    feedback(AudioManager.FX_KEYPRESS_DELETE)
                    deleteOne()
                    handler.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(repeat)
            }
            true
        }
        return tv
    }

    // ---------------------------------------------------------------- shift

    private fun onShiftTap() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == Shift.LOCK -> Shift.OFF
            shift == Shift.ONCE && now - lastShiftTap < 400 -> Shift.LOCK
            shift == Shift.ONCE -> Shift.OFF
            else -> Shift.ONCE
        }
        lastShiftTap = now
        refreshLetters()
    }

    private fun refreshLetters() {
        val up = shift != Shift.OFF
        letterKeys.forEach { (tv, k) -> tv.text = if (up) k.shifted else k.normal }
        shiftKeyView?.let { tv ->
            tv.text = when (shift) {
                Shift.OFF -> "⇧"
                Shift.ONCE -> "⬆"
                Shift.LOCK -> "⇪"
            }
            tv.background = FurDrawable(if (shift == Shift.OFF) imgWide else imgAccent)
        }
    }

    // capitalise automatically at the start of a sentence (English only)
    private fun autoShift() {
        if (lang != Lang.EN || symbols || emoji || shift == Shift.LOCK) {
            refreshLetters()
            return
        }
        val ic = currentInputConnection
        val type = currentInputEditorInfo?.inputType ?: 0
        val mode = if (ic != null && type != 0) ic.getCursorCapsMode(type) else 0
        shift = if (mode != 0) Shift.ONCE else Shift.OFF
        refreshLetters()
    }

    private fun afterKey() {
        if (shift == Shift.ONCE) shift = Shift.OFF
        autoShift()
    }

    // ---------------------------------------------------------------- strip

    private fun defaultStrip(): List<String> =
        if (lang == Lang.EN) listOf("🐰", "I", "The", "I'm", "🌸")
        else listOf("🐰", "मैं", "है", "क्या", "🌸")

    private fun updateStrip() {
        if (pickingLang) {
            val names = listOf("English" to Lang.EN, "हिन्दी" to Lang.HI, "Hinglish" to Lang.HG)
            stripViews.forEachIndexed { i, t ->
                t.textSize = 15f
                val n = names.getOrNull(i)
                when {
                    n != null -> {
                        t.text = if (n.second == lang) "✓ " + n.first else n.first
                        t.setOnClickListener {
                            feedback()
                            selectLang(n.second)
                        }
                    }
                    i == 3 -> {
                        t.text = "✕"
                        t.setOnClickListener {
                            pickingLang = false
                            updateStrip()
                        }
                    }
                    else -> {
                        t.text = ""
                        t.setOnClickListener(null)
                    }
                }
            }
            return
        }
        val items = when {
            composing.isNotEmpty() -> candidates(composing.toString())
            lang == Lang.EN && wordBuf.isNotEmpty() -> {
                suggestWords(wordBuf.toString())
            }
            else -> defaultStrip()
        }
        stripViews.forEachIndexed { i, t ->
            val s = items.getOrNull(i) ?: ""
            t.text = s
            t.textSize = if (s == "🐰" || s == "🌸") 20f else 16f
            t.setOnClickListener(if (s.isEmpty()) null else View.OnClickListener {
                feedback()
                onStripTap(s)
            })
        }
    }

    private fun selectLang(l: Lang) {
        finishComposing()
        wordBuf.setLength(0)
        pickingLang = false
        lang = l
        shift = Shift.OFF
        prefs().edit().putString("lang", lang.name).apply()
        setInputView(buildKeyboard())
        autoShift()
    }

    private fun feedback(fx: Int = AudioManager.FX_KEYPRESS_STANDARD) {
        try {
            audio.playSoundEffect(fx)
        } catch (e: Exception) {
        }
        vibrate()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(18)
            }
        } catch (e: Exception) {
        }
    }

    private fun closePicker() {
        if (pickingLang) {
            pickingLang = false
            updateStrip()
        }
    }

    private fun onStripTap(s: String) {
        if (composing.isNotEmpty()) {
            val ic = currentInputConnection ?: return
            ic.commitText("$s ", 1)
            composing.setLength(0)
            afterKey()
            updateStrip()
        } else if (lang == Lang.EN && wordBuf.isNotEmpty() && s != "🐰" && s != "🌸" &&
            suggestWords(wordBuf.toString()).contains(s)
        ) {
            val ic = currentInputConnection ?: return
            ic.deleteSurroundingText(wordBuf.length, 0)
            ic.commitText("$s ", 1)
            wordBuf.setLength(0)
            lastKeyTime = SystemClock.uptimeMillis()
            afterKey()
            updateStrip()
        } else {
            typeText(if (s == "🐰" || s == "🌸") s else "$s ")
        }
    }

    // ---------------------------------------------------------------- typing

    private fun hinglishActive(): Boolean {
        if (lang != Lang.HG) return false
        val t = currentInputEditorInfo?.inputType ?: 0
        if (t and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val v = t and InputType.TYPE_MASK_VARIATION
        return v != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            v != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            v != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
            v != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            v != InputType.TYPE_TEXT_VARIATION_URI
    }

    private fun onLetter(s: String) {
        closePicker()
        val c = s[0]
        val ascii = s.length == 1 && (c in 'a'..'z' || c in 'A'..'Z')
        if (ascii && hinglishActive()) {
            composing.append(s)
            showComposing()
            afterKey()
        } else {
            typeText(s)
        }
    }

    private fun showComposing() {
        val ic = currentInputConnection ?: return
        val c = candidates(composing.toString())
        lastComposeTime = SystemClock.uptimeMillis()
        ic.setComposingText(c[0], 1)
        updateStrip()
    }

    private fun finishComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            composing.setLength(0)
            updateStrip()
        }
    }

    private fun typeText(s: String) {
        closePicker()
        finishComposing()
        val ic = currentInputConnection ?: return
        ic.commitText(s, 1)
        trackWord(s)
        afterKey()
    }

    private fun typeEmoji(e: String) {
        finishComposing()
        val ic = currentInputConnection ?: return
        ic.commitText(e, 1)
        wordBuf.setLength(0)
        addRecent(e)
    }

    private fun deleteOne() {
        closePicker()
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            if (composing.isEmpty()) {
                currentInputConnection?.commitText("", 1)
                updateStrip()
            } else {
                showComposing()
            }
            return
        }
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        if (wordBuf.isNotEmpty()) {
            wordBuf.setLength(wordBuf.length - 1)
            lastKeyTime = SystemClock.uptimeMillis()
            updateStrip()
        }
    }

    private fun handleEnter() {
        closePicker()
        finishComposing()
        wordBuf.setLength(0)
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        afterKey()
    }

    // ---------------------------------------------------------------- recent emoji

    private fun recents(): List<String> =
        (prefs().getString("recent", "") ?: "").split(" ").filter { it.isNotEmpty() }

    private fun addRecent(e: String) {
        val l = (listOf(e) + recents().filter { it != e }).take(24)
        prefs().edit().putString("recent", l.joinToString(" ")).apply()
    }
}
