package com.orangeway.go.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "orangego_prefs")

/** 轻量设置存储（DataStore）：设备Id/别名/收件目录/主题/接收偏好。 */
class SettingsRepo(private val context: Context) {

    private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
    private val KEY_SAVE_DIR = stringPreferencesKey("save_dir")
    private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
    private val KEY_LANGUAGE = stringPreferencesKey("language")
    private val KEY_AUTO_SAVE = booleanPreferencesKey("receive_auto_save")
    private val KEY_SAVE_HISTORY = booleanPreferencesKey("receive_save_history")
    private val KEY_SAVE_GALLERY = booleanPreferencesKey("receive_save_gallery")
    private val KEY_PIN_ENABLED = booleanPreferencesKey("receive_pin_enabled")
    private val KEY_PIN_CODE = stringPreferencesKey("receive_pin_code")
    private val KEY_AUTO_ACCEPT_TEXT = booleanPreferencesKey("receive_auto_accept_text")
    // 传输记录自动清理：保留最近 N 天的记录（0=不自动清理）
    private val KEY_AUTO_CLEANUP_DAYS = intPreferencesKey("auto_cleanup_days")

    val deviceId: Flow<String> = context.dataStore.data.map { it[KEY_DEVICE_ID].orEmpty() }
    val deviceName: Flow<String> = context.dataStore.data.map { it[KEY_DEVICE_NAME].orEmpty() }
    val saveDir: Flow<String> = context.dataStore.data.map { it[KEY_SAVE_DIR].orEmpty() }
    // 主题：0=跟随系统，1=浅色，2=深色
    val themeMode: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: 0 }
    // 界面语言（ISO 代码，"system" 表示跟随系统）
    val language: Flow<String> = context.dataStore.data.map { it[KEY_LANGUAGE] ?: "system" }
    // 接收偏好
    val autoSave: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SAVE] ?: false }
    val saveHistory: Flow<Boolean> = context.dataStore.data.map { it[KEY_SAVE_HISTORY] ?: true }
    val saveToGallery: Flow<Boolean> = context.dataStore.data.map { it[KEY_SAVE_GALLERY] ?: true }
    val pinEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PIN_ENABLED] ?: false }
    val pinCode: Flow<String> = context.dataStore.data.map { it[KEY_PIN_CODE].orEmpty() }
    // 默认自动接收文本消息（关闭则不自动显示接收文本气泡）
    val autoAcceptText: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_ACCEPT_TEXT] ?: true }
    // 传输记录自动清理保留天数（0=不自动清理）
    val autoCleanupDays: Flow<Int> = context.dataStore.data.map { it[KEY_AUTO_CLEANUP_DAYS] ?: 0 }

    suspend fun saveDeviceId(id: String) = context.dataStore.edit { it[KEY_DEVICE_ID] = id }
    suspend fun saveDeviceName(name: String) = context.dataStore.edit { it[KEY_DEVICE_NAME] = name }
    suspend fun saveSaveDir(dir: String) = context.dataStore.edit { it[KEY_SAVE_DIR] = dir }
    suspend fun saveThemeMode(mode: Int) = context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    suspend fun saveLanguage(code: String) = context.dataStore.edit { it[KEY_LANGUAGE] = code }
    suspend fun saveAutoSave(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_SAVE] = v }
    suspend fun saveSaveHistory(v: Boolean) = context.dataStore.edit { it[KEY_SAVE_HISTORY] = v }
    suspend fun saveSaveToGallery(v: Boolean) = context.dataStore.edit { it[KEY_SAVE_GALLERY] = v }
    suspend fun savePinEnabled(v: Boolean) = context.dataStore.edit { it[KEY_PIN_ENABLED] = v }
    suspend fun savePinCode(code: String) = context.dataStore.edit { it[KEY_PIN_CODE] = code }
    suspend fun saveAutoAcceptText(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_ACCEPT_TEXT] = v }
    suspend fun saveAutoCleanupDays(v: Int) = context.dataStore.edit { it[KEY_AUTO_CLEANUP_DAYS] = v }
}