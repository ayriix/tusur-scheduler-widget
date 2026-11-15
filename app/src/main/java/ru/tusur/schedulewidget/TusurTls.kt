package ru.tusur.schedulewidget

import android.content.Context
import android.os.Build
import android.util.Log
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android 6 fallback for the official GlobalSign Root R46 / AlphaSSL CA 2025 chain used by
 * timetable.tusur.ru. Android 7+ uses the host-scoped Network Security Config.
 *
 * The platform trust store is always tried first. The supplemental CA anchors are
 * enabled only for the exact timetable host; HTTPS hostname verification stays on.
 */
internal object TusurTls {
    const val HOST = "timetable.tusur.ru"
    private const val TAG = "TusurTls"

    @Volatile
    private var cachedLegacySocketFactory: SSLSocketFactory? = null

    fun isSupportedHost(host: String): Boolean = host.equals(HOST, ignoreCase = true)

    fun needsLegacyFallback(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.N

    fun legacySocketFactory(context: Context, host: String): SSLSocketFactory {
        require(isSupportedHost(host)) { "GlobalSign R46 fallback is restricted to $HOST" }
        check(needsLegacyFallback()) { "Legacy TLS fallback must only be used on Android 6 (API 23)" }
        cachedLegacySocketFactory?.let { return it }

        return synchronized(this) {
            cachedLegacySocketFactory?.let { return@synchronized it }
            val platform = platformTrustManager()
            val supplemental = globalSignR46TrustManager(context.applicationContext)
            val combined = SupplementalTrustManager(platform, supplemental)
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(combined), SecureRandom())
            }.socketFactory.also { cachedLegacySocketFactory = it }
        }
    }

    fun isCertificateValidationError(error: Throwable): Boolean = causes(error).any { cause ->
        cause is CertificateException ||
            cause.javaClass.simpleName.contains("CertPathValidator", ignoreCase = true) ||
            cause.javaClass.simpleName.contains("CertPathBuilder", ignoreCase = true)
    }

    /** Only transient network failures are retried; certificate errors are permanent. */
    fun shouldRetry(error: Throwable): Boolean {
        if (isCertificateValidationError(error)) return false
        return causes(error).any { cause ->
            cause is SocketTimeoutException ||
                cause is UnknownHostException ||
                cause is ConnectException ||
                cause is NoRouteToHostException ||
                cause is SocketException
        }
    }

    fun userFacingError(error: Throwable): String {
        val causes = causes(error)
        return when {
            isCertificateValidationError(error) ->
                "Не удалось проверить SSL-сертификат сайта расписания. Проверьте дату и время на телефоне; если ошибка повторится, отправьте сообщение через обратную связь."
            causes.any { it is SocketTimeoutException } ->
                "Сайт расписания не ответил вовремя. Возможно, мобильная сеть ограничивает скорость или доступ к сайту. Попробуйте Wi-Fi или повторите позже."
            causes.any { it is UnknownHostException } ->
                "Не удалось найти адрес сайта расписания. Проверьте DNS и подключение к интернету."
            causes.any { it is ConnectException || it is NoRouteToHostException } ->
                "Не удалось подключиться к сайту расписания. Возможно, доступ ограничен мобильной сетью. Попробуйте Wi-Fi или повторите позже."
            else -> error.message?.takeIf { it.isNotBlank() }
                ?: "Не удалось загрузить расписание. Проверьте подключение к интернету."
        }
    }

    private fun causes(error: Throwable): List<Throwable> {
        val result = mutableListOf<Throwable>()
        var current: Throwable? = error
        while (current != null && result.size < 16 && result.none { it === current }) {
            result += current
            current = current.cause
        }
        return result
    }

    private fun platformTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: throw IllegalStateException("Android не предоставил системный X509TrustManager")
    }

    private fun globalSignR46TrustManager(context: Context): X509TrustManager {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val root = context.resources.openRawResource(R.raw.globalsign_root_r46).use { input ->
            certificateFactory.generateCertificate(input) as X509Certificate
        }
        val alphaSslIntermediate = context.resources
            .openRawResource(R.raw.globalsign_gcc_r46_alphassl_ca_2025).use { input ->
                certificateFactory.generateCertificate(input) as X509Certificate
            }

        // Verify the bundled public certificates before adding them as trust anchors.
        root.checkValidity()
        require(root.basicConstraints >= 0) { "GlobalSign Root R46 is not a CA certificate" }
        require(root.subjectX500Principal == root.issuerX500Principal) {
            "GlobalSign Root R46 is not self-signed"
        }
        require(sha1Thumbprint(root) == "53A2B04BCA6BD645E6398A8EC40DD2BF77C3A290") {
            "Bundled GlobalSign Root R46 fingerprint does not match the official certificate"
        }

        alphaSslIntermediate.checkValidity()
        require(alphaSslIntermediate.basicConstraints >= 0) {
            "GlobalSign AlphaSSL R46 intermediate is not a CA certificate"
        }
        require(alphaSslIntermediate.issuerX500Principal == root.subjectX500Principal) {
            "GlobalSign AlphaSSL R46 intermediate has an unexpected issuer"
        }
        alphaSslIntermediate.verify(root.publicKey)
        require(sha1Thumbprint(alphaSslIntermediate) == "E7AE6D3BB265B204B7EA3D732EDEC0799AB22488") {
            "Bundled GlobalSign AlphaSSL R46 fingerprint does not match the official certificate"
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("globalsign-root-r46", root)
            // Also trust the currently published intermediate. This covers servers
            // that omit it from their TLS handshake; usage remains host-scoped.
            setCertificateEntry("globalsign-gcc-r46-alphassl-ca-2025", alphaSslIntermediate)
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: throw IllegalStateException("Не удалось создать TLS trust manager для GlobalSign R46")
    }

    private fun sha1Thumbprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-1").digest(certificate.encoded)
            .joinToString("") { byte -> "%02X".format(byte) }

    private class SupplementalTrustManager(
        private val platform: X509TrustManager,
        private val supplemental: X509TrustManager
    ) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            try {
                platform.checkServerTrusted(chain, authType)
                return
            } catch (platformError: CertificateException) {
                try {
                    supplemental.checkServerTrusted(chain, authType)
                    Log.w(TAG, "Android system trust store did not recognize the timetable chain; the supplemental GlobalSign R46 anchors validated it.")
                } catch (supplementalError: CertificateException) {
                    supplementalError.addSuppressed(platformError)
                    val peerChain = chain.joinToString("; ") { certificate ->
                        "${certificate.subjectX500Principal.name} <- ${certificate.issuerX500Principal.name}"
                    }
                    Log.e(TAG, "Both system and supplemental GlobalSign certificate validation failed. Peer chain: $peerChain", supplementalError)
                    throw supplementalError
                }
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
            // Client-certificate authentication is not used; do not broaden client trust.
            platform.checkClientTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            (platform.acceptedIssuers.asList() + supplemental.acceptedIssuers.asList())
                .distinctBy { "${it.subjectX500Principal.name}|${it.serialNumber}" }
                .toTypedArray()
    }
}
