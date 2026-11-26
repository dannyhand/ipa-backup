package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import us.dannyhand.ipabackup.certs.RootProfile
import us.dannyhand.ipabackup.plist.Plist
import java.io.File
import java.security.interfaces.RSAPublicKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RootProfileTest {
    private val roots = File("src/main/assets/roots").listFiles()!!.sorted().map { RootProfile.Root(it.nameWithoutExtension, it.readBytes()) }

    @Test fun bundledRootsAreValidRsaCas() {
        assertEquals(15, roots.size)
        val now = java.util.Date()
        for (r in roots) {
            r.cert.checkValidity(now)
            assertTrue(r.name, r.cert.publicKey is RSAPublicKey)
            assertTrue(r.name, r.cert.basicConstraints >= 0) // a CA
            assertEquals(r.name, r.cert.subjectX500Principal, r.cert.issuerX500Principal) // self-signed root
        }
        assertTrue(roots.any { it.commonName == "ISRG Root X1" })
    }

    @Test fun profileParsesAndCarriesEachCertificate() {
        val profile = Plist.parse(RootProfile.build(roots)) as Map<*, *>
        assertEquals("Configuration", profile["PayloadType"])
        val payloads = profile["PayloadContent"] as List<*>
        assertEquals(roots.size, payloads.size)
        for ((p, r) in payloads.zip(roots)) {
            p as Map<*, *>
            assertEquals("com.apple.security.root", p["PayloadType"])
            assertTrue((p["PayloadContent"] as ByteArray).contentEquals(r.der))
        }
        // Stable UUIDs: building again gives the same profile, so reinstalling replaces it.
        assertTrue(RootProfile.build(roots).contentEquals(RootProfile.build(roots)))
    }
}
