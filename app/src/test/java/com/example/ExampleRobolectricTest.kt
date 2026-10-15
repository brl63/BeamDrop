package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.helper.AppExtractorHelper
import com.example.helper.QrCodeHelper
import com.example.model.InstalledAppInfo
import com.example.model.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BeamDrop", appName)
    }

    @Test
    fun `test formatBytes helper`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("500 B", formatBytes(500))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("10.0 MB", formatBytes(10 * 1024 * 1024))
    }

    @Test
    fun `test clean apk filename`() {
        val app = InstalledAppInfo(
            appName = "My Cool Game!",
            packageName = "com.cool.game",
            versionName = "2.1.0",
            apkPath = "/data/app/base.apk",
            sizeBytes = 15000000L
        )
        val cleanName = AppExtractorHelper.getCleanApkFileName(app)
        assertEquals("My_Cool_Game__v2.1.0.apk", cleanName)
    }

    @Test
    fun `test qr code handshake parsing`() {
        // URI format
        val uriPayload = QrCodeHelper.buildHandshakePayload("192.168.1.88", 8080, "Pixel 8")
        val peer1 = QrCodeHelper.parseHandshakeContent(uriPayload)
        assertNotNull(peer1)
        assertEquals("192.168.1.88", peer1?.ip)
        assertEquals(8080, peer1?.port)
        assertEquals("Pixel 8", peer1?.name)

        // Raw IP:Port format
        val peer2 = QrCodeHelper.parseHandshakeContent("192.168.43.1:9090")
        assertNotNull(peer2)
        assertEquals("192.168.43.1", peer2?.ip)
        assertEquals(9090, peer2?.port)

        // JSON format
        val jsonPayload = """{"ip":"10.0.0.5","port":8080,"name":"Galaxy S24"}"""
        val peer3 = QrCodeHelper.parseHandshakeContent(jsonPayload)
        assertNotNull(peer3)
        assertEquals("10.0.0.5", peer3?.ip)
        assertEquals(8080, peer3?.port)
        assertEquals("Galaxy S24", peer3?.name)
    }
}
