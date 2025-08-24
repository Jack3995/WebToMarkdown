package com.jack3995.webtomarkdown.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.jack3995.webtomarkdown.screens.FileNameOption
import com.jack3995.webtomarkdown.screens.SaveLocationOption

/**
 * Менеджер настроек приложения.
 * 
 * Отвечает за сохранение и загрузку пользовательских настроек
 * между сессиями приложения с использованием SharedPreferences.
 */
class SettingsManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME, 
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFERENCES_NAME = "WebToMarkdownSettings"
        private const val KEY_SAVE_LOCATION_OPTION = "save_location_option"
        private const val KEY_CUSTOM_FOLDER_PATH = "custom_folder_path"
        private const val KEY_FILE_NAME_OPTION = "file_name_option"
        private const val KEY_DOWNLOAD_IMAGES = "download_images"
    }
    
    /**
     * Загружает настройки места сохранения файла
     */
    fun getSaveLocationOption(): SaveLocationOption {
        val optionName = sharedPreferences.getString(KEY_SAVE_LOCATION_OPTION, null)
        return if (optionName != null) {
            try {
                SaveLocationOption.valueOf(optionName)
            } catch (_: IllegalArgumentException) {
                SaveLocationOption.ASK_EVERY_TIME
            }
        } else {
            SaveLocationOption.ASK_EVERY_TIME
        }
    }
    
    /**
     * Сохраняет путь к пользовательской папке
     */
    fun saveCustomFolderPath(path: String?) {
        sharedPreferences.edit {
            putString(KEY_CUSTOM_FOLDER_PATH, path)
        }
    }
    
    /**
     * Загружает путь к пользовательской папке
     */
    fun getCustomFolderPath(): String? {
        return sharedPreferences.getString(KEY_CUSTOM_FOLDER_PATH, null)
    }
    
    /**
     * Загружает настройки формирования имени файла
     */
    fun getFileNameOption(): FileNameOption {
        val optionName = sharedPreferences.getString(KEY_FILE_NAME_OPTION, null)
        return if (optionName != null) {
            try {
                FileNameOption.valueOf(optionName)
            } catch (_: IllegalArgumentException) {
                FileNameOption.DEFAULT_NAME
            }
        } else {
            FileNameOption.DEFAULT_NAME
        }
    }
    
    /**
     * Загружает настройки скачивания изображений
     */
    fun getDownloadImages(): Boolean {
        return sharedPreferences.getBoolean(KEY_DOWNLOAD_IMAGES, true)
    }
    
    /**
     * Сохраняет все настройки одновременно
     */
    fun saveAllSettings(
        saveLocationOption: SaveLocationOption,
        customFolderPath: String?,
        fileNameOption: FileNameOption,
        downloadImages: Boolean
    ) {
        sharedPreferences.edit {
            putString(KEY_SAVE_LOCATION_OPTION, saveLocationOption.name)
            putString(KEY_CUSTOM_FOLDER_PATH, customFolderPath)
            putString(KEY_FILE_NAME_OPTION, fileNameOption.name)
            putBoolean(KEY_DOWNLOAD_IMAGES, downloadImages)
        }
    }
}
