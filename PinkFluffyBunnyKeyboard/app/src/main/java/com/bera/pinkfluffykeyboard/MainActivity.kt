package com.bera.pinkfluffykeyboard

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 28, 24, 28)
            setBackgroundColor(Color.rgb(255, 232, 239))
        }
        val title = TextView(this).apply {
            text = "🐰 Pink Fluffy\nBunny Keyboard 🌸"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(142, 63, 91))
        }
        val desc = TextView(this).apply {
            text = "\nA soft pink, floral keyboard theme inspired by your screenshot.\n\n1. Enable the keyboard\n2. Select it as your current keyboard\n3. Open any app and start typing!\n"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(111, 71, 84))
        }
        val enable = Button(this).apply {
            text = "1 · Enable keyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        val choose = Button(this).apply {
            text = "2 · Choose keyboard"
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        root.addView(title)
        root.addView(desc)
        root.addView(enable, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        root.addView(choose, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })
        setContentView(root)
    }
}
