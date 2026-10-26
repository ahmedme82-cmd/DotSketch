package com.dotsketch.core

import android.content.Context

class PreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("dotsketch_settings", Context.MODE_PRIVATE)

    fun load(): UserSettings {
        val d = UserSettings()
        return UserSettings(
            threshold = prefs.getInt("threshold", d.threshold),
            invert = prefs.getBoolean("invert", d.invert),
            denoise = prefs.getBoolean("denoise", d.denoise),
            minContourAreaPx = prefs.getFloat("minArea", d.minContourAreaPx),
            spacingMm = prefs.getFloat("spacingMm", d.spacingMm),
            diameterMm = prefs.getFloat("diameterMm", d.diameterMm),
            paperSize = enumOr(prefs.getString("paper", null), d.paperSize),
            orientation = enumOr(prefs.getString("orientation", null), d.orientation),
            marginMm = prefs.getFloat("marginMm", d.marginMm),
            center = prefs.getBoolean("center", d.center),
            fitToPage = prefs.getBoolean("fit", d.fitToPage),
            userScale = prefs.getFloat("userScale", d.userScale)
        )
    }

    fun save(s: UserSettings) {
        prefs.edit()
            .putInt("threshold", s.threshold)
            .putBoolean("invert", s.invert)
            .putBoolean("denoise", s.denoise)
            .putFloat("minArea", s.minContourAreaPx)
            .putFloat("spacingMm", s.spacingMm)
            .putFloat("diameterMm", s.diameterMm)
            .putString("paper", s.paperSize.name)
            .putString("orientation", s.orientation.name)
            .putFloat("marginMm", s.marginMm)
            .putBoolean("center", s.center)
            .putBoolean("fit", s.fitToPage)
            .putFloat("userScale", s.userScale)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
