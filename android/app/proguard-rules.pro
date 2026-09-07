# ── OkHttp / Okio ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── BouncyCastle（RSA-2048 自签证书生成，反射加载算法） ──
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── Coil（图片加载，含 SVG 解码器反射注册） ──
-keep class coil.** { *; }
-dontwarn coil.**

# ── hCaptcha Compose SDK ──
-keep class com.hcaptcha.** { *; }
-dontwarn com.hcaptcha.**

# ── Kotlin Coroutines / Serialization ──
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── 保留 Compose 中的反射访问点 ──
-keep class androidx.compose.** { *; }

# ── 保留 ViewModel（反射实例化） ──
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ── 保留 DataStore 生成的 Proto 类（如有） ──
-keep class androidx.datastore.** { *; }
