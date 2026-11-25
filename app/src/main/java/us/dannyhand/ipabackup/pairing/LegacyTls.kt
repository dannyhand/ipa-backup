package us.dannyhand.ipabackup.pairing

import org.bouncycastle.tls.AlertDescription
import org.bouncycastle.tls.AlertLevel
import org.bouncycastle.tls.CertificateRequest
import org.bouncycastle.tls.CipherSuite
import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.HashAlgorithm
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.SignatureAlgorithm
import org.bouncycastle.tls.SignatureAndHashAlgorithm
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsClientProtocol
import org.bouncycastle.tls.TlsCredentials
import org.bouncycastle.tls.TlsServerCertificate
import org.bouncycastle.tls.TlsUtils
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import us.dannyhand.ipabackup.service.ByteConnection
import us.dannyhand.ipabackup.service.StreamConnection
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/**
 * TLS client for lockdownd sessions, in pure Java/Kotlin via BouncyCastle so it can
 * still speak TLS 1.0 with CBC/RSA suites that Android's own TLS stack refuses.
 *
 * In practice an iPod touch 4G on iOS 6.1.6 negotiated TLS 1.2 with TLS_RSA_WITH_AES_128_CBC_SHA256 (0x003C);
 * TLS 1.0 and the older suites are kept for devices that can't. A JVM test runs a TLS-1.0-only OpenSSL server
 * that requires the client certificate, so the legacy path stays verified.
 *
 * Like libimobiledevice, it presents the host certificate as the client cert and does not
 * verify the device's certificate (the device presents the cert we generated at pairing).
 */
object LegacyTls {

    private val CIPHER_SUITES = intArrayOf(
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,
    )

    fun connect(
        input: InputStream,
        output: OutputStream,
        record: PairRecord,
        log: (String) -> Unit = {},
    ): ByteConnection {
        val crypto = BcTlsCrypto(SecureRandom())
        val hostCert = org.bouncycastle.tls.Certificate(arrayOf(crypto.createCertificate(record.hostCertificateDer())))
        val hostKey = record.hostKey()

        val client = object : DefaultTlsClient(crypto) {
            override fun getSupportedVersions(): Array<ProtocolVersion> =
                ProtocolVersion.TLSv12.downTo(ProtocolVersion.TLSv10)

            override fun getSupportedCipherSuites(): IntArray =
                TlsUtils.getSupportedCipherSuites(crypto, CIPHER_SUITES)

            // Old servers may not implement RFC 5746; don't abort over it.
            override fun notifySecureRenegotiation(secureRenegotiation: Boolean) {}

            override fun notifyServerVersion(serverVersion: ProtocolVersion) {
                super.notifyServerVersion(serverVersion)
                log("TLS version: $serverVersion")
            }

            override fun notifySelectedCipherSuite(selectedCipherSuite: Int) {
                super.notifySelectedCipherSuite(selectedCipherSuite)
                log("TLS cipher: 0x%04x".format(selectedCipherSuite))
            }

            override fun notifyAlertRaised(alertLevel: Short, alertDescription: Short, message: String?, cause: Throwable?) {
                log("TLS alert raised: ${AlertLevel.getText(alertLevel)} ${AlertDescription.getText(alertDescription)} ${message ?: ""} ${cause?.message ?: ""}")
            }

            override fun notifyAlertReceived(alertLevel: Short, alertDescription: Short) {
                log("TLS alert from device: ${AlertLevel.getText(alertLevel)} ${AlertDescription.getText(alertDescription)}")
            }

            override fun getAuthentication(): TlsAuthentication = object : TlsAuthentication {
                override fun notifyServerCertificate(serverCertificate: TlsServerCertificate) {}

                override fun getClientCredentials(certificateRequest: CertificateRequest): TlsCredentials {
                    val sigAlg = if (TlsUtils.isTLSv12(context)) {
                        val offered = certificateRequest.supportedSignatureAlgorithms
                        val sha256 = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.rsa)
                        val sha1 = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha1, SignatureAlgorithm.rsa)
                        if (offered == null || offered.contains(sha256)) sha256 else sha1
                    } else null // TLS 1.0/1.1 use the fixed MD5+SHA1 signature
                    return BcDefaultTlsCredentialedSigner(TlsCryptoParameters(context), crypto, hostKey, hostCert, sigAlg)
                }
            }
        }

        val protocol = TlsClientProtocol(input, output)
        protocol.connect(client)
        return StreamConnection(protocol.inputStream, protocol.outputStream) { runCatching { protocol.close() } }
    }
}
