package com.bera.pinkfluffykeyboard

import android.inputmethodservice.InputMethodService
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent

class PinkKeyboardService : InputMethodService() {
    private val textPink = Color.rgb(122, 48, 104)
    private var symbols = false

    override fun onCreateInputView(): View = buildKeyboard()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bmp(id: Int) = BitmapFactory.decodeResource(resources, id)
    private val imgNormal by lazy { bmp(R.drawable.key_normal) }
    private val imgWide by lazy { bmp(R.drawable.key_wide) }
    private val imgSpace by lazy { bmp(R.drawable.key_space) }
    private val imgBoard by lazy { bmp(R.drawable.board_bg) }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(7), dp(5), dp(8))
            background = FurDrawable(imgBoard, dp(16).toFloat(), true)
        }
        val suggestion = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("🐰", "I", "The", "I'm", "🌸").forEach { s ->
            val t = TextView(this).apply {
                text = s
                textSize = if (s == "🐰" || s == "🌸") 20f else 15f
                gravity = Gravity.CENTER
                setTextColor(textPink)
                setPadding(dp(8), dp(5), dp(8), dp(5))
            }
            suggestion.addView(t, LinearLayout.LayoutParams(0, dp(38), 1f))
        }
        root.addView(suggestion)

        val rows = if (symbols) listOf(
            listOf("1","2","3","4","5","6","7","8","9","0"),
            listOf("@","#","$","%","&","-","+","(",")","/"),
            listOf("=<>","*","\"","'",":",";","!","?","⌫")
        ) else listOf(
            listOf("q","w","e","r","t","y","u","i","o","p"),
            listOf("a","s","d","f","g","h","j","k","l"),
            listOf("🌸","z","x","c","v","b","n","m","⌫")
        )
        rows.forEachIndexed { index, chars ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val sidePad = if (index == 1) dp(11) else 0
            row.setPadding(sidePad, dp(2), sidePad, dp(3))
            chars.forEach { label ->
                val weight = if (label == "⌫" || label == "🌸" || label == "=<>") 1.25f else 1f
                row.addView(makeKey(label), LinearLayout.LayoutParams(0, dp(46), weight).apply {
                    setMargins(dp(2), dp(1), dp(2), dp(1))
                })
            }
            root.addView(row)
        }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("123", "☺", "🌸", "English", "↵").forEach { label ->
            val weight = when (label) { "🌸" -> 3.8f; "English" -> 1.3f; else -> 1f }
            bottom.addView(makeKey(label, true), LinearLayout.LayoutParams(0, dp(46), weight).apply {
                setMargins(dp(2), dp(1), dp(2), dp(1))
            })
        }
        root.addView(bottom)
        return root
    }

    private fun makeKey(label: String, wide: Boolean = false): TextView = TextView(this).apply {
        text = if (label == "🌸" && wide) "" else label
        gravity = Gravity.CENTER
        textSize = when (label) {
            "🌸" -> 23f
            "English" -> 11f
            "⌫", "↵", "123", "☺" -> 16f
            else -> 21f
        }
        setTextColor(textPink)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = FurDrawable(
            when {
                label == "🌸" && wide -> imgSpace
                label == "⌫" || label == "↵" || label == "🌸" || label == "English" -> imgWide
                else -> imgNormal
            }
        )
        setOnClickListener { handleKey(label) }
    }

    private fun handleKey(label: String) {
        val ic = currentInputConnection ?: return
        when (label) {
            "⌫" -> ic.deleteSurroundingText(1, 0)
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED)
                    ic.performEditorAction(action)
                else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }
            "🌸" -> ic.commitText(" ", 1)
            "123" -> { symbols = !symbols; setInputView(buildKeyboard()) }
            "☺" -> ic.commitText("😊", 1)
            "English" -> ic.commitText(" ", 1)
            else -> ic.commitText(label, 1)
        }
    }
}
