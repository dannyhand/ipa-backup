package us.dannyhand.ipabackup

import org.bouncycastle.asn1.pkcs.RSAPublicKey
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import us.dannyhand.ipabackup.pairing.LegacyTls
import us.dannyhand.ipabackup.pairing.PairRecord
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.security.SecureRandom

/**
 * Simulates an iOS 6 lockdownd TLS endpoint with `openssl s_server` restricted to TLS 1.0,
 * presenting the generated device cert and requiring (and verifying) our host client cert.
 */
class LegacyTlsTest {

    @Test
    fun pairRecordAndTls10Handshake() {
        // Fake "device" key, given to us as a PKCS#1 PEM like DevicePublicKey.
        val dev = RSAKeyPairGenerator().apply {
            init(RSAKeyGenerationParameters(BigInteger.valueOf(65537), SecureRandom(), 1024, 80))
        }.generateKeyPair()
        val pub = dev.public as RSAKeyParameters
        val devPubPem = PairRecord.pem("RSA PUBLIC KEY", RSAPublicKey(pub.modulus, pub.exponent).encoded)

        val record = PairRecord.generate(devPubPem, "TEST-BUID")

        val dir = createTempDir()
        File(dir, "dev.crt").writeBytes(record.deviceCertificate)
        File(dir, "dev.key").writeBytes(PairRecord.pem("PRIVATE KEY", PrivateKeyInfoFactory.createPrivateKeyInfo(dev.private).encoded))
        File(dir, "root.crt").writeBytes(record.rootCertificate)
        File(dir, "host.crt").writeBytes(record.hostCertificate)

        // Informational: OpenSSL treats empty-subject certs as self-signed (same as libimobiledevice's).
        val verify = ProcessBuilder("openssl", "verify", "-auth_level", "0", "-CAfile", "root.crt", "host.crt")
            .directory(dir).redirectErrorStream(true).start()
        val verifyOut = verify.inputStream.bufferedReader().readText()
        println(verifyOut)
        verify.waitFor()

        val port = 40000 + (Math.random() * 10000).toInt()
        val server = ProcessBuilder(
            "openssl", "s_server", "-accept", port.toString(), "-naccept", "1", "-rev",
            "-tls1", "-cipher", "AES128-SHA:AES256-SHA:@SECLEVEL=0",
            "-cert", "dev.crt", "-key", "dev.key",
            "-Verify", "1", "-CAfile", "root.crt",
        ).directory(dir).redirectErrorStream(true).start()
        val serverLog = StringBuilder()
        Thread { server.inputStream.bufferedReader().forEachLine { serverLog.appendLine(it) } }.start()

        var sock: Socket? = null
        for (i in 0 until 50) {
            sock = runCatching { Socket("127.0.0.1", port) }.getOrNull()
            if (sock != null) break
            Thread.sleep(100)
        }
        assumeTrue("openssl s_server didn't start", sock != null)

        val logs = mutableListOf<String>()
        val conn = LegacyTls.connect(sock!!.getInputStream(), sock.getOutputStream(), record) { logs += it; println(it) }
        conn.write("hello lockdown\n".toByteArray())
        val reply = String(conn.readFully(15))
        println("reply: $reply")
        conn.close()
        sock.close()
        server.waitFor()
        println(serverLog)

        assertEquals("nwodkcol olleh\n", reply)
        assert(logs.any { it.contains("TLS 1.0") }) { "expected TLS 1.0, got $logs" }
        assert(serverLog.contains("CLIENT CERTIFICATE") || serverLog.contains("depth=0")) { "server never got a client cert" }
    }
}
