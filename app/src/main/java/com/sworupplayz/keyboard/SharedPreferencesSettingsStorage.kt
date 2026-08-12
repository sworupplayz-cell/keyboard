package com.sworupplayz.keyboard

import android.content.SharedPreferences

class SharedPreferencesSettingsStorage(
    private val preferences: SharedPreferences
) : SettingsStorage {
    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(keys: Set<String>) {
        preferences.edit().apply {
            keys.forEach(::remove)
        }.apply()
    }
}
