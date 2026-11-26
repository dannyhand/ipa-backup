package us.dannyhand.ipabackup.certs

import us.dannyhand.ipabackup.plist.Plist
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID

/**
 * Builds a configuration profile (.mobileconfig) that adds trusted root certificates.
 *
 * Why it's needed: iOS 6's built-in roots stopped being updated in 2014. Since then many sites moved
 * to newer roots (Let's Encrypt's ISRG Root X1 from 2015, Google Trust Services, DigiCert G2, Sectigo's
 * USERTrust...) and several old roots expired, so Safari says "The certificate for this server is
 * invalid". A profile with com.apple.security.root payloads is the official way to add roots, and on
 * iOS 6 a root installed that way is trusted right away (the separate "Certificate Trust Settings"
 * switch only arrived in iOS 10.3). It's removable later in Settings › General › Profiles.
 *
 * Only RSA roots are included: iOS 6's support for ECDSA (elliptic curve) roots is uncertain, and one
 * certificate it can't read would make the whole profile fail to install.
 *
 * Old iOS still can't reach sites that only allow the newest TLS ciphers (AES-GCM, ChaCha20, TLS 1.3);
 * no certificate can fix that, and those show a different error.
 */
object RootProfile {
    const val IDENTIFIER = "us.dannyhand.ipabackup.roots"

    class Root(val name: String, val der: ByteArray) {
        val cert: X509Certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        /** Common name, e.g. "ISRG Root X1". */
        val commonName: String = Regex("CN=([^,]+)").find(cert.subjectX500Principal.name)?.groupValues?.get(1) ?: name
        val sha256: String = MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02X".format(it) }
    }

    /** Stable UUIDs, so installing again replaces the same profile instead of adding a second copy. */
    private fun uuid(s: String) = UUID.nameUUIDFromBytes(s.toByteArray()).toString().uppercase()

    fun build(roots: List<Root>): ByteArray {
        val payloads = roots.map { r ->
            mapOf(
                "PayloadType" to "com.apple.security.root",
                "PayloadVersion" to 1,
                "PayloadIdentifier" to "$IDENTIFIER.${r.name}",
                "PayloadUUID" to uuid("$IDENTIFIER.${r.name}"),
                "PayloadDisplayName" to r.commonName,
                "PayloadCertificateFileName" to "${r.name}.cer",
                "PayloadContent" to r.der,
            )
        }
        val profile = linkedMapOf(
            "PayloadType" to "Configuration",
            "PayloadVersion" to 1,
            "PayloadIdentifier" to IDENTIFIER,
            "PayloadUUID" to uuid(IDENTIFIER),
            "PayloadDisplayName" to "Updated root certificates",
            "PayloadOrganization" to "IPA Backup",
            "PayloadDescription" to "Adds ${roots.size} current root certificates (Let's Encrypt, Google, DigiCert, " +
                "Sectigo, Amazon, Microsoft, GlobalSign, GoDaddy and others) so Safari and apps can open sites " +
                "whose certificates chain to roots newer than this iOS version.",
            "PayloadRemovalDisallowed" to false,
            "PayloadContent" to payloads,
        )
        return Plist.toXml(profile).toByteArray(Charsets.UTF_8)
    }
}
