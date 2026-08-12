package com.sworupplayz.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private val preferences by lazy {
        getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KeyboardPreferences.KEY_DARK, false)
        setTheme(if (dark) R.style.Theme_Keyboard_Dark else R.style.Theme_Keyboard_Light)
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }

        content.addView(heading(getString(R.string.setup_heading), 26f))
        content.addView(body(getString(R.string.setup_description)))
        content.addView(actionButton(getString(R.string.enable_keyboard)) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        content.addView(actionButton(getString(R.string.choose_keyboard)) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        content.addView(heading(getString(R.string.preferences_heading), 20f).apply {
            setPadding(0, dp(28), 0, dp(8))
        })
        content.addView(preferenceSwitch(
            label = getString(R.string.sound_setting),
            key = KeyboardPreferences.KEY_SOUND,
            defaultValue = false
        ))
        content.addView(preferenceSwitch(
            label = getString(R.string.vibration_setting),
            key = KeyboardPreferences.KEY_VIBRATION,
            defaultValue = false
        ))
        content.addView(preferenceSwitch(
            label = getString(R.string.dark_setting),
            key = KeyboardPreferences.KEY_DARK,
            defaultValue = false,
            recreateOnChange = true
        ))
        content.addView(preferenceSwitch(
            label = getString(R.string.suggestions_setting),
            key = KeyboardPreferences.KEY_SUGGESTIONS,
            defaultValue = true
        ))
        content.addView(preferenceSwitch(
            label = getString(R.string.learning_setting),
            key = KeyboardPreferences.KEY_LEARNING,
            defaultValue = true
        ))
        content.addView(body(getString(R.string.privacy_note)).apply {
            setPadding(0, dp(28), 0, 0)
            alpha = 0.75f
        })

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun heading(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(12), 0, dp(18))
    }

    private fun actionButton(label: String, onClick: (View) -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setOnClickListener { view -> onClick(view) }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply { setMargins(0, dp(6), 0, dp(6)) }
    }

    @Suppress("DEPRECATION")
    private fun preferenceSwitch(
        label: String,
        key: String,
        defaultValue: Boolean,
        recreateOnChange: Boolean = false
    ) = Switch(this).apply {
        text = label
        textSize = 17f
        gravity = Gravity.CENTER_VERTICAL
        isChecked = preferences.getBoolean(key, defaultValue)
        setPadding(dp(4), dp(6), dp(4), dp(6))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        )
        setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean(key, checked).apply()
            if (recreateOnChange) recreate()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
