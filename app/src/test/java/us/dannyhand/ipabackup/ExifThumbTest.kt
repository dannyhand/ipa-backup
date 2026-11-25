package us.dannyhand.ipabackup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import us.dannyhand.ipabackup.afc.AfcClient
import us.dannyhand.ipabackup.util.ExifThumb
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class ExifThumbTest {
    private val thumb = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + Random(5).nextBytes(300) + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    /** JPEG with APP1 Exif: IFD0 (orientation) -> IFD1 (thumbnail offset/length), thumbnail bytes inside the TIFF block. */
    private fun jpeg(order: ByteOrder, orientation: Int, withThumb: Boolean = true): ByteArray {
        val tiff = ByteBuffer.allocate(1000).order(order)
        tiff.put(if (order == ByteOrder.LITTLE_ENDIAN) "II".toByteArray() else "MM".toByteArray())
        tiff.putShort(42).putInt(8)
        // IFD0 at 8: one entry (orientation, SHORT), next IFD at 26
        tiff.putShort(1)
        tiff.putShort(0x0112).putShort(3).putInt(1).putShort(orientation.toShort()).putShort(0)
        tiff.putInt(if (withThumb) 26 else 0)
        // IFD1 at 26: offset + length (LONG), thumb data at 56
        tiff.putShort(2)
        tiff.putShort(0x0201).putShort(4).putInt(1).putInt(56)
        tiff.putShort(0x0202).putShort(4).putInt(1).putInt(thumb.size)
        tiff.putInt(0)
        tiff.position(56)
        tiff.put(thumb)
        val tiffBytes = tiff.array().copyOf(56 + thumb.size)

        val app1 = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiffBytes
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        // An APP0 segment first, like many JPEGs
        out.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0, 6, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte()))
        out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((app1.size + 2) shr 8).toByte(), ((app1.size + 2) and 0xFF).toByte()))
        out.write(app1)
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 2)) // start of scan
        out.write(Random(9).nextBytes(5000))
        return out.toByteArray()
    }

    @Test
    fun littleEndian() {
        val r = ExifThumb.parse(jpeg(ByteOrder.LITTLE_ENDIAN, 6))
        assertArrayEquals(thumb, r.thumbnail)
        assertEquals(6, r.orientation)
        assertEquals(90, ExifThumb.rotationDegrees(r.orientation))
    }

    @Test
    fun bigEndianLikeApple() {
        val r = ExifThumb.parse(jpeg(ByteOrder.BIG_ENDIAN, 3))
        assertArrayEquals(thumb, r.thumbnail)
        assertEquals(180, ExifThumb.rotationDegrees(r.orientation))
    }

    @Test
    fun noThumbnailOrNotJpeg() {
        val r = ExifThumb.parse(jpeg(ByteOrder.BIG_ENDIAN, 8, withThumb = false))
        assertNull(r.thumbnail)
        assertEquals(8, r.orientation)
        assertNull(ExifThumb.parse(Random(1).nextBytes(200)).thumbnail)
        assertNull(ExifThumb.parse(ByteArray(0)).thumbnail)
        // Truncated header (only first 40 bytes read) must not crash.
        assertNull(ExifThumb.parse(jpeg(ByteOrder.BIG_ENDIAN, 1).copyOf(40)).thumbnail)
    }

    @Test
    fun readHeadReadsOnlyThePrefix() {
        val file = jpeg(ByteOrder.BIG_ENDIAN, 1) + Random(2).nextBytes(AfcClient.READ_CHUNK * 3)
        val afc = AfcClient(FakeAfcDevice(mapOf("/DCIM/100APPLE/IMG_0001.JPG" to file)))
        val head = afc.readHead("/DCIM/100APPLE/IMG_0001.JPG", 2000)
        assertEquals(2000, head.size)
        assertArrayEquals(thumb, ExifThumb.parse(head).thumbnail)
        assertEquals(file.size, afc.readHead("/DCIM/100APPLE/IMG_0001.JPG", Int.MAX_VALUE).size)
    }
}
