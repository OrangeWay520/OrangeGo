package com.orangeway.go.core

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date

/**
 * LocalSend 设备身份证书管理：生成并持久化一枚 RSA-2048 自签 X509 证书（CN=LocalSend User，
 * 含 clientAuth + serverAuth EKU），对齐官方 `crypto/cert.rs::generate_self_signed`。
 *
 * 同一枚证书同时用作：
 *  - mTLS 客户端身份（向 LocalSend HTTPS 服务端出示，官方服务端仅校验证书自洽+有效期，不校验链）；
 *  - 设备 fingerprint（证书 DER 的 SHA-256 大写十六进制），暴露于 /info 与组播通告。
 *
 * 证书持久化到应用私有目录（PKCS12），保证 fingerprint 跨进程/重启稳定。
 */
object LsCert {
    private const val TAG = "OrangeGO.LsCert"
    private const val STORE_FILE = "ls_identity.p12"
    private const val STORE_ALIAS = "ls"
    private const val STORE_PASS = "orangego-ls"

    @Volatile private var initialized = false
    private var privateKey: PrivateKey? = null
    private var certificate: X509Certificate? = null
    private var fingerprint: String = ""

    /** 初始化（幂等）：加载或生成并持久化证书。必须在 IO 线程调用。 */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val file = File(context.filesDir, STORE_FILE)
            if (file.exists()) {
                runCatching { loadFromStore(file) }.onSuccess {
                    initialized = true
                    Log.i(TAG, "证书已加载 fingerprint=${fingerprint.take(12)}…")
                    return@synchronized
                }.onFailure { Log.w(TAG, "加载证书失败，将重新生成", it) }
            }
            generateAndStore(file)
            initialized = true
            Log.i(TAG, "证书已生成并持久化 fingerprint=${fingerprint.take(12)}…")
        }
    }

    private fun loadFromStore(file: File) {
        val ks = KeyStore.getInstance("PKCS12")
        file.inputStream().use { ks.load(it, STORE_PASS.toCharArray()) }
        privateKey = ks.getKey(STORE_ALIAS, STORE_PASS.toCharArray()) as PrivateKey
        certificate = ks.getCertificate(STORE_ALIAS) as X509Certificate
        fingerprint = sha256HexUpper(certificate!!.encoded)
    }

    private fun generateAndStore(file: File) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val pair: KeyPair = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86_400_000L)
        val notAfter = Date(now + 2000L * 365L * 86_400_000L)
        val cn = X500Name("CN=LocalSend User")
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
        val builder = JcaX509v3CertificateBuilder(
            cn, BigInteger.valueOf(now), notBefore, notAfter, cn, pair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage, true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        builder.addExtension(
            Extension.extendedKeyUsage, false,
            ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_clientAuth, KeyPurposeId.id_kp_serverAuth))
        )
        val holder = builder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(holder)

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(STORE_ALIAS, pair.private, STORE_PASS.toCharArray(), arrayOf(cert))
        file.parentFile?.mkdirs()
        file.outputStream().use { ks.store(it, STORE_PASS.toCharArray()) }

        privateKey = pair.private
        certificate = cert
        fingerprint = sha256HexUpper(cert.encoded)
    }

    /** 设备 fingerprint（证书 DER SHA-256 大写十六进制）。init 后可用。 */
    fun fingerprint(): String = fingerprint

    /** mTLS 客户端私钥。 */
    fun privateKey(): PrivateKey = privateKey!!

    /** mTLS 客户端证书。 */
    fun certificate(): X509Certificate = certificate!!

    /** 证书 DER 字节的 SHA-256 大写十六进制（无冒号），对齐官方 fingerprint_from_cert_der。 */
    fun sha256HexUpper(der: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(der).joinToString("") { "%02X".format(it) }
    }
}