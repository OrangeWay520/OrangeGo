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
    // 图片集成发送：true=多张图片合并到一个消息气泡；false=每张图片单独一个气泡（默认）
    private val KEY_INTEGRATE_IMAGES = booleanPreferencesKey("media_integrate_images")
    // 收藏夹（白名单）：JSON 数组 [{"id":"og_xxx","name":"PC","ip":"192.168.1.1"}, ...]
    private val KEY_FAVORITES_JSON = stringPreferencesKey("favorites_json")
    // 自动保存收藏夹设备的文件（默认开启）
    private val KEY_AUTO_SAVE_WHITELIST = booleanPreferencesKey("receive_auto_save_whitelist")

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
    // 图片集成发送（默认 false：多张图片各自独立气泡）
    val integrateImages: Flow<Boolean> = context.dataStore.data.map { it[KEY_INTEGRATE_IMAGES] ?: false }
    // 收藏夹 JSON（空字符串=无收藏）
    val favoritesJson: Flow<String> = context.dataStore.data.map { it[KEY_FAVORITES_JSON].orEmpty() }
    // 自动保存收藏夹设备的文件（默认开启）
    val autoSaveWhitelist: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SAVE_WHITELIST] ?: true }

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
    suspend fun saveIntegrateImages(v: Boolean) = context.dataStore.edit { it[KEY_INTEGRATE_IMAGES] = v }
    suspend fun saveFavoritesJson(v: String) = context.dataStore.edit { it[KEY_FAVORITES_JSON] = v }
    suspend fun saveAutoSaveWhitelist(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_SAVE_WHITELIST] = v }
}