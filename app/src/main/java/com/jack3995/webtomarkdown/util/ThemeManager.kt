package com.jack3995.webtomarkdown.util

import android.app.Activity
import android.graphics.Color
import android.view.WindowManager
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.jack3995.webtomarkdown.R
import com.jack3995.webtomarkdown.screens.ThemeOption

/**
 * Менеджер тем приложения
 * Отвечает за управление цветовыми схемами и системными барами
 */
class ThemeManager(private val activity: Activity) {

    /**
     * Настройка системных баров: контент НЕ рисуем под системными панелями
     */
    fun setupSystemBars() {
        // Рисуем с учётом системных панелей (без edge-to-edge)
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)

        // Настраиваем поведение системных баров
        val windowInsetsController = WindowCompat.getInsetsController(
            activity.window, 
            activity.window.decorView
        )
        windowInsetsController.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Убеждаемся, что не установлены флаги рисования под барами
        activity.window.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
    }

    /**
     * Обновление цветов системных баров в зависимости от выбранной темы
     */
    fun updateSystemBarsColors(themeOption: ThemeOption, isSystemInDarkTheme: Boolean) {
        val isDark = when (themeOption) {
            ThemeOption.LIGHT -> false
            ThemeOption.DARK -> true
            ThemeOption.SYSTEM -> isSystemInDarkTheme
        }

        val windowInsetsController = WindowCompat.getInsetsController(
            activity.window, 
            activity.window.decorView
        )
        
        if (isDark) {
            // Тёмная тема — настраиваем только статус-бар
            activity.window.statusBarColor = "#71C5E8".toColorInt()
            windowInsetsController.isAppearanceLightStatusBars = false
        } else {
            // Светлая тема — настраиваем только статус-бар
            activity.window.statusBarColor = "#0B2B6A".toColorInt()
            windowInsetsController.isAppearanceLightStatusBars = true
        }
    }

    /**
     * Получение цвета из ресурсов
     */
    private fun getColorFromResources(colorResId: Int): ComposeColor {
        return ComposeColor(androidx.core.content.ContextCompat.getColor(activity, colorResId))
    }

    /**
     * Создание цветовой схемы Material3 на основе выбранной темы
     */
    fun getColorScheme(themeOption: ThemeOption, isSystemInDarkTheme: Boolean): ColorScheme {
        val isDark = when (themeOption) {
            ThemeOption.LIGHT -> false
            ThemeOption.DARK -> true
            ThemeOption.SYSTEM -> isSystemInDarkTheme
        }
        
        return if (isDark) {
            darkColorScheme(
                primary = getColorFromResources(R.color.dark_primary),
                onPrimary = getColorFromResources(R.color.dark_on_primary),
                primaryContainer = getColorFromResources(R.color.dark_primary_container),
                onPrimaryContainer = getColorFromResources(R.color.dark_on_primary_container),
                secondary = getColorFromResources(R.color.dark_secondary),
                onSecondary = getColorFromResources(R.color.dark_on_secondary),
                tertiary = getColorFromResources(R.color.dark_tertiary),
                onTertiary = getColorFromResources(R.color.dark_on_tertiary),
                background = getColorFromResources(R.color.dark_background),
                onBackground = getColorFromResources(R.color.dark_on_background),
                surface = getColorFromResources(R.color.dark_surface),
                onSurface = getColorFromResources(R.color.dark_on_surface),
                error = getColorFromResources(R.color.dark_error),
                onError = getColorFromResources(R.color.dark_on_error)
            )
        } else {
            lightColorScheme(
                primary = getColorFromResources(R.color.light_primary),
                onPrimary = getColorFromResources(R.color.light_on_primary),
                primaryContainer = getColorFromResources(R.color.light_primary_container),
                onPrimaryContainer = getColorFromResources(R.color.light_on_primary_container),
                secondary = getColorFromResources(R.color.light_secondary),
                onSecondary = getColorFromResources(R.color.light_on_secondary),
                tertiary = getColorFromResources(R.color.light_tertiary),
                onTertiary = getColorFromResources(R.color.light_on_tertiary),
                background = getColorFromResources(R.color.light_background),
                onBackground = getColorFromResources(R.color.light_on_background),
                surface = getColorFromResources(R.color.light_surface),
                onSurface = getColorFromResources(R.color.light_on_surface),
                error = getColorFromResources(R.color.light_error),
                onError = getColorFromResources(R.color.light_on_error)
            )
        }
    }
}
