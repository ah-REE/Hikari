package com.hikari.app

import android.content.Context

/** Keeps the first-run experience off devices that already had Hikari installed. */
object OnboardingStore {
    private const val preferencesName = "hikari-onboarding"
    private const val completedKey = "completed"

    fun shouldShow(context: Context): Boolean {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        if (preferences.contains(completedKey)) return !preferences.getBoolean(completedKey, true)

        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val updatingAnExistingInstall = packageInfo.lastUpdateTime > packageInfo.firstInstallTime
        val restoredExistingData = listOf("hikari-native", "hikari-library").any { name ->
            appContext.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty()
        }
        val completed = updatingAnExistingInstall || restoredExistingData
        preferences.edit().putBoolean(completedKey, completed).apply()
        return !completed
    }

    fun complete(context: Context) {
        context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit().putBoolean(completedKey, true).apply()
    }
}
