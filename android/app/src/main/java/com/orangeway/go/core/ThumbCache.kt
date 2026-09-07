package com.orangeway.go.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 缩略图磁盘缓存：把图片/视频首帧缩略图按「内容指纹」持久化到应用缓存目录，
 * 使得源文件被删除或移动后，传输记录里的缩略图依然存在，直到该传输记录被删除/撤回/清空。
 *
 * key = sha256(name|size|mtime)，与源文件路径解耦；文件移动仅路径变化时 key 不变，可重复命中。
 * 所有读/写均在 IO 线程执行（内部无 Compose 依赖）。
 */
object ThumbCache {

    private const val DIR = "thumbnails"

    private fun dir(context: Context): File =
        File(context.cacheDir, DIR).apply { mkdirs() }

    /** 内容指纹 key（十六进制，前 24 位足够区分，避免乱码文件名）。 */
    fun keyOf(name: String, size: Long, mtime: Long): String {
        val raw = "$name|$size|$mtime".toByteArray()
        val md = MessageDigest.getInstance("SHA-256").digest(raw)
        return md.joinToString("") { "%02x".format(Locale.US, it) }.take(24)
    }

    private fun file(context: Context, key: String): File = File(dir(context), "$key.png")

    /** 命中返回缩略图文件，否则 null。 */
    fun resolveFile(context: Context, key: String): File? =
        file(context, key).takeIf { it.isFile }

    /** 取缓存位图（IO 线程）。 */
    suspend fun load(context: Context, key: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(file(context, key).absolutePath) }.getOrNull()
    }

    /** 写缓存位图为 PNG（IO 线程），返回命中的 key 文件。 */
    suspend fun put(context: Context, key: String, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(context, key)
            FileOutputStream(f).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) }
            f
        }.getOrNull()
    }

    /** 批量删除指定 key 的缓存文件（IO 线程）。空 key 忽略。 */
    suspend fun deleteKeys(context: Context, keys: Collection<String>) = withContext(Dispatchers.IO) {
        for (k in keys) {
            if (k.isBlank()) continue
            try { file(context, k).delete() } catch (_: Exception) { }
        }
    }

    /** 清空整个缩略图缓存目录（IO 线程）。 */
    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            dir(context).listFiles()?.forEach { if (it.isFile) it.delete() }
        }
    }

    /** 缓存总字节数（供设置界面显示）。 */
    fun totalSizeBytes(context: Context): Long =
        runCatching { dir(context).listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)
}

/** 从 RAW/DNG 字节流提取内嵌 JPEG 预览（扫描最大 JPEG 段 FF D8…FF D9）；无嵌入 JPEG 或过大返回 null。 */
fun extractEmbeddedJpeg(bytes: ByteArray): ByteArray? {
    if (bytes.size > 100 * 1024 * 1024) return null
    var bestStart = -1
    var bestLen = 0
    var i = 0
    while (i < bytes.size - 1) {
        if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD8.toByte()) {
            var j = i + 2
            while (j < bytes.size - 1) {
                if (bytes[j] == 0xFF.toByte() && bytes[j + 1] == 0xD9.toByte()) { j += 2; break }
                j++
            }
            if (j > bytes.size) j = bytes.size
            val len = j - i
            if (len > bestLen) { bestStart = i; bestLen = len }
            i = j
        } else i++
    }
    return if (bestStart < 0 || bestLen < 2048) null else bytes.copyOfRange(bestStart, bestStart + bestLen)
}

/** 从 Uri 读取并提取内嵌 JPEG（IO 线程）；失败返回 null。 */
suspend fun extractEmbeddedJpeg(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { extractEmbeddedJpeg(it.readBytes()) }
    }.getOrNull()
}

/** 从文件路径提取内嵌 JPEG；失败返回 null。 */
fun extractEmbeddedJpeg(path: String): ByteArray? = runCatching {
    val f = File(path)
    if (!f.isFile) null else extractEmbeddedJpeg(f.readBytes())
}.getOrNull()