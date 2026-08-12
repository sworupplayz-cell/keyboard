package com.sworupplayz.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** A deliberately small settings surface backed only by local SharedPreferences. */
class SettingsActivity : Activity() {
    private val preferences by lazy {
        getSharedPreferences(KeyboardPreferences.FILE_NAME, Context.MODE_PRIVATE)
    }
    private val settingsRepository by lazy {
        KeyboardSettingsRepository(SharedPreferencesSettingsStorage(preferences))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val appearance = settingsRepository.load().appearance
        setTheme(if (appearance.isDark(systemUsesDarkTheme())) {
            R.style.Theme_Keyboard_Dark
        } else {
            R.style.Theme_Keyboard_Light
        })
        super.onCreate(savedInstanceState)

        val settings = settingsRepository.load()
        if (settingsRepository.savedDefaultMode() == null) {
            settingsRepository.setDefaultMode(settings.defaultMode)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(32))
        }

        content.addView(heading(getString(R.string.settings_title), 26f))
        content.addView(body(getString(R.string.setup_description)))
        content.addView(actionButton(getString(R.string.enable_keyboard)) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        content.addView(actionButton(getString(R.string.choose_keyboard)) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        content.addView(sectionHeading(getString(R.string.section_general)))
        content.addView(choiceSetting(
            label = getString(R.string.default_mode_setting),
            description = getString(R.string.default_mode_description),
            choices = listOf(
                DefaultKeyboardMode.ENGLISH to getString(R.string.mode_english),
                DefaultKeyboardMode.NEPALI to getString(R.string.mode_nepali),
                DefaultKeyboardMode.ROMAN to getString(R.string.mode_roman)
            ),
            selected = settings.defaultMode,
            onSelected = settingsRepository::setDefaultMode
        ))
        content.addView(choiceSetting(
            label = getString(R.string.appearance_setting),
            description = getString(R.string.appearance_description),
            choices = listOf(
                KeyboardAppearance.SYSTEM to getString(R.string.appearance_system),
                KeyboardAppearance.LIGHT to getString(R.string.appearance_light),
                KeyboardAppearance.DARK to getString(R.string.appearance_dark)
            ),
            selected = settings.appearance,
            onSelected = {
                settingsRepository.setAppearance(it)
                recreate()
            }
        ))

        content.addView(sectionHeading(getString(R.string.section_typing)))
        content.addView(preferenceSwitch(
            getString(R.string.suggestions_setting),
            getString(R.string.suggestions_description),
            settings.suggestions,
            settingsRepository::setSuggestions
        ))
        content.addView(preferenceSwitch(
            getString(R.string.learning_setting),
            getString(R.string.learning_description),
            settings.learnedWords,
            settingsRepository::setLearnedWords
        ))
        content.addView(preferenceSwitch(
            getString(R.string.number_row_setting),
            getString(R.string.number_row_description),
            settings.numberRow,
            settingsRepository::setNumberRow
        ))

        content.addView(sectionHeading(getString(R.string.section_feedback)))
        content.addView(preferenceSwitch(
            getString(R.string.vibration_setting),
            getString(R.string.vibration_description),
            settings.keyVibration,
            settingsRepository::setKeyVibration
        ))
        content.addView(preferenceSwitch(
            getString(R.string.sound_setting),
            getString(R.string.sound_description),
            settings.keySound,
            settingsRepository::setKeySound
        ))

        content.addView(sectionHeading(getString(R.string.section_size)))
        content.addView(choiceSetting(
            label = getString(R.string.keyboard_height_setting),
            description = getString(R.string.keyboard_height_description),
            choices = listOf(
                KeyboardHeight.SMALL to getString(R.string.height_small),
                KeyboardHeight.NORMAL to getString(R.string.height_normal),
                KeyboardHeight.LARGE to getString(R.string.height_large)
            ),
            selected = settings.height,
            onSelected = settingsRepository::setHeight
        ))

        content.addView(sectionHeading(getString(R.string.section_data)))
        content.addView(body(getString(R.string.clear_learned_description)).apply {
            setPadding(0, 0, 0, dp(4))
        })
        content.addView(actionButton(getString(R.string.clear_learned_words)) {
            confirmClearLearnedWords()
        })
        content.addView(body(getString(R.string.clear_recent_emoji_description)).apply {
            setPadding(0, dp(12), 0, dp(4))
        })
        content.addView(actionButton(getString(R.string.clear_recent_emoji)) {
            confirmClearRecentEmoji()
        })

        content.addView(sectionHeading(getString(R.string.section_about)))
        content.addView(body(getString(R.string.app_version, appVersion())).apply {
            setPadding(0, 0, 0, dp(8))
        })
        content.addView(body(getString(R.string.about_description)).apply {
            setPadding(0, 0, 0, dp(8))
        })
        content.addView(body(getString(R.string.privacy_note)).apply {
            setPadding(0, 0, 0, 0)
            alpha = 0.78f
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
    }

    private fun confirmClearLearnedWords() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_learned_words)
            .setMessage(R.string.clear_learned_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clear_action) { _, _ ->
                settingsRepository.clearLearnedWords()
                Toast.makeText(this, R.string.learned_words_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun heading(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun sectionHeading(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(getColor(R.color.accent))
        setPadding(0, dp(26), 0, dp(8))
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setLineSpacing(0f, 1.12f)
        setPadding(0, dp(10), 0, dp(16))
    }

    private fun actionButton(label: String, onClick: (View) -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setOnClickListener { view -> onClick(view) }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        ).apply { setMargins(0, dp(4), 0, dp(4)) }
    }

    private fun preferenceSwitch(
        label: String,
        description: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): View {
        @Suppress("DEPRECATION")
        val toggle = Switch(this).apply {
            isChecked = checked
            contentDescription = label
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(5), dp(2), dp(5))
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@SettingsActivity).apply {
                    text = label
                    textSize = 17f
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = description
                    textSize = 13f
                    alpha = 0.72f
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle)
            setOnClickListener { toggle.isChecked = !toggle.isChecked }
        }
    }

    private fun <T> choiceSetting(
        label: String,
        choices: List<Pair<T, String>>,
        selected: T,
        description: String? = null,
        onSelected: (T) -> Unit
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@SettingsActivity).apply {
            text = label
            textSize = 17f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(2), dp(4), 0, 0)
        })
        if (description != null) addView(TextView(this@SettingsActivity).apply {
            text = description
            textSize = 13f
            alpha = 0.72f
            setPadding(dp(2), dp(2), 0, dp(2))
        })
        val group = RadioGroup(this@SettingsActivity).apply {
            orientation = RadioGroup.VERTICAL
        }
        val valuesById = mutableMapOf<Int, T>()
        choices.forEach { (value, title) ->
            val id = View.generateViewId()
            valuesById[id] = value
            group.addView(RadioButton(this@SettingsActivity).apply {
                this.id = id
                text = title
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                isChecked = value == selected
                minHeight = dp(42)
            })
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            valuesById[checkedId]?.let(onSelected)
        }
        addView(group)
    }

    private fun systemUsesDarkTheme(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    @Suppress("DEPRECATION")
    private fun appVersion(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
