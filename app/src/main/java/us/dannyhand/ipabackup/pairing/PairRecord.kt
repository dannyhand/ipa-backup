package us.dannyhand.ipabackup.pairing

import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.RSAPublicKey
import org.bouncycastle.asn1.x500.RDN
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader
import org.bouncycastle.util.io.pem.PemWriter
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Date
import java.util.UUID

/**
 * Host <-> device pairing credentials, generated the way libimobiledevice does
 * (common/userpref.c): a self-signed root CA, plus host and device certificates
 * signed by it. Empty subject/issuer names, serial 0, SHA1withRSA, 10-year validity.
 * All certs and keys are stored as PEM bytes, which is what lockdownd expects in Pair.
 */
class PairRecord(
    val hostId: String,
    val systemBuid: String,
    val rootCertificate: ByteArray,
    val rootPrivateKey: ByteArray,
    val hostCertificate: ByteArray,
    val hostPrivateKey: ByteArray,
    val deviceCertificate: ByteArray,
    var escrowBag: ByteArray? = null,
) {
    /** The PairRecord dict sent with Pair / ValidatePair (no private keys). */
    fun toPairRequest(): Map<String, Any?> = linkedMapOf(
        "DeviceCertificate" to deviceCertificate,
        "HostCertificate" to hostCertificate,
        "RootCertificate" to rootCertificate,
        "HostID" to hostId,
        "SystemBUID" to systemBuid,
    )

    fun toStorage(): Map<String, Any?> = toPairRequest() + mapOf(
        "HostPrivateKey" to hostPrivateKey,
        "RootPrivateKey" to rootPrivateKey,
        "EscrowBag" to escrowBag,
    )

    fun hostCertificateDer(): ByteArray = pemContent(hostCertificate)
    fun hostKey(): AsymmetricKeyParameter = PrivateKeyFactory.createKey(pemContent(hostPrivateKey))

    companion object {
        private const val RSA_BITS = 2048
        private const val TEN_YEARS_MS = 10L * 365 * 24 * 3600 * 1000

        fun fromStorage(m: Map<String, Any?>) = PairRecord(
            hostId = m["HostID"] as String,
            systemBuid = m["SystemBUID"] as String,
            rootCertificate = m["RootCertificate"] as ByteArray,
            rootPrivateKey = m["RootPrivateKey"] as ByteArray,
            hostCertificate = m["HostCertificate"] as ByteArray,
            hostPrivateKey = m["HostPrivateKey"] as ByteArray,
            deviceCertificate = m["DeviceCertificate"] as ByteArray,
            escrowBag = m["EscrowBag"] as? ByteArray,
        )

        /** [devicePublicKeyPem] is lockdownd's GetValue "DevicePublicKey" (PEM bytes). */
        fun generate(devicePublicKeyPem: ByteArray, systemBuid: String): PairRecord {
            val random = SecureRandom()
            val root = rsaKeyPair(random)
            val host = rsaKeyPair(random)
            val rootSpki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(root.public)
            val hostSpki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(host.public)
            val deviceSpki = parsePublicKey(devicePublicKeyPem)

            val now = System.currentTimeMillis()
            val notBefore = Date(now - 24L * 3600 * 1000) // tolerate a device clock that's a bit behind
            val notAfter = Date(now + TEN_YEARS_MS)
            val emptyName = X500Name(arrayOf<RDN>())
            val extUtils = BcX509ExtensionUtils()

            fun cert(spki: SubjectPublicKeyInfo, isCa: Boolean): ByteArray {
                val b = X509v3CertificateBuilder(emptyName, BigInteger.ZERO, notBefore, notAfter, emptyName, spki)
                b.addExtension(Extension.basicConstraints, true, BasicConstraints(isCa))
                if (!isCa) {
                    b.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
                    b.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(spki))
                }
                val sigAlg = DefaultSignatureAlgorithmIdentifierFinder().find("SHA1withRSA")
                val digAlg = DefaultDigestAlgorithmIdentifierFinder().find(sigAlg)
                val signer = BcRSAContentSignerBuilder(sigAlg, digAlg).build(root.private)
                return pem("CERTIFICATE", b.build(signer).encoded)
            }

            return PairRecord(
                hostId = UUID.randomUUID().toString().uppercase(),
                systemBuid = systemBuid,
                rootCertificate = cert(rootSpki, true),
                rootPrivateKey = pem("PRIVATE KEY", PrivateKeyInfoFactory.createPrivateKeyInfo(root.private).encoded),
                hostCertificate = cert(hostSpki, false),
                hostPrivateKey = pem("PRIVATE KEY", PrivateKeyInfoFactory.createPrivateKeyInfo(host.private).encoded),
                deviceCertificate = cert(deviceSpki, false),
            )
        }

        private fun rsaKeyPair(random: SecureRandom): AsymmetricCipherKeyPair =
            RSAKeyPairGenerator().apply {
                init(RSAKeyGenerationParameters(BigInteger.valueOf(65537), random, RSA_BITS, 80))
            }.generateKeyPair()

        /** Accepts PKCS#1 "RSA PUBLIC KEY" (what iOS sends) or X.509 "PUBLIC KEY". */
        fun parsePublicKey(pem: ByteArray): SubjectPublicKeyInfo {
            val obj = readPem(pem)
            return when (obj.type) {
                "RSA PUBLIC KEY" -> SubjectPublicKeyInfo(
                    AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE),
                    RSAPublicKey.getInstance(obj.content),
                )
                "PUBLIC KEY" -> SubjectPublicKeyInfo.getInstance(obj.content)
                else -> throw IOException("Unexpected device key type ${obj.type}")
            }
        }

        fun pem(type: String, der: ByteArray): ByteArray {
            val sw = StringWriter()
            PemWriter(sw).use { it.writeObject(PemObject(type, der)) }
            return sw.toString().toByteArray(Charsets.US_ASCII)
        }

        fun pemContent(pem: ByteArray): ByteArray = readPem(pem).content

        private fun readPem(pem: ByteArray): PemObject =
            PemReader(StringReader(String(pem, Charsets.US_ASCII))).use { it.readPemObject() }
                ?: throw IOException("Not a PEM object")
    }
}
