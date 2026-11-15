package com.example.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipInstallerExtractorTest {

    private fun buildSampleZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun inspectZipStream_detectsAllPlatformsAndFiltersAccurately() {
        val zipBytes = buildSampleZip(
            mapOf(
                "Facuedu.uy-Mennseger-Setup-1.0.0.exe" to "MZ_WIN".toByteArray(),
                "nested/Facuedu.uy-Mennseger-1.0.0-universal.dmg" to "DMG_BIN".toByteArray(),
                "nested/Facuedu.uy-Mennseger-1.0.0-universal.pkg" to "PKG_BIN".toByteArray(),
                "linux/Facuedu.uy-Mennseger-1.0.0-x86_64.AppImage" to "ELF_APPIMAGE".toByteArray(),
                "linux/Facuedu.uy-Mennseger-1.0.0-amd64.deb" to "DEB_PKG".toByteArray(),
                "Facuedu.uy-Mennseger-Android.apk" to "APK_ZIP".toByteArray(),
                "Facuedu.uy-Mennseger-iOS.ipa" to "IPA_ZIP".toByteArray(),
                "README.txt" to "ignored".toByteArray(),
                "../../evil.exe" to "blocked".toByteArray()
            )
        )

        val allScan = ZipInstallerExtractor.inspectZipStream(
            archiveName = "multiplatform.zip",
            inputStream = ByteArrayInputStream(zipBytes),
            filter = InstallerPlatformFilter.ALL
        )
        assertEquals(7, allScan.allInstallersInZip.size)
        assertEquals(7, allScan.matchingInstallers.size)

        val winScan = ZipInstallerExtractor.inspectZipStream(
            archiveName = "win.zip",
            inputStream = ByteArrayInputStream(zipBytes),
            filter = InstallerPlatformFilter.WINDOWS
        )
        assertEquals(1, winScan.matchingInstallers.size)
        assertEquals("Facuedu.uy-Mennseger-Setup-1.0.0.exe", winScan.matchingInstallers.first().fileName)

        val macScan = ZipInstallerExtractor.inspectZipStream(
            archiveName = "mac.zip",
            inputStream = ByteArrayInputStream(zipBytes),
            filter = InstallerPlatformFilter.MACOS
        )
        assertEquals(2, macScan.matchingInstallers.size)
        assertTrue(macScan.matchingInstallers.any { it.extension == "dmg" })
        assertTrue(macScan.matchingInstallers.any { it.extension == "pkg" })

        val linuxScan = ZipInstallerExtractor.inspectZipStream(
            archiveName = "linux.zip",
            inputStream = ByteArrayInputStream(zipBytes),
            filter = InstallerPlatformFilter.LINUX_UBUNTU
        )
        assertEquals(2, linuxScan.matchingInstallers.size)
        assertTrue(linuxScan.matchingInstallers.any { it.extension == "appimage" })
        assertTrue(linuxScan.matchingInstallers.any { it.extension == "deb" })
    }

    @Test
    fun extractEntryFromStream_writesExactInstallerBytes() {
        val expectedPayload = ByteArray(1024) { idx -> (idx % 251).toByte() }
        val zipBytes = buildSampleZip(
            mapOf("dist/Facuedu.uy-Mennseger-Setup-1.0.0.exe" to expectedPayload)
        )

        val output = ByteArrayOutputStream()
        val written = ZipInstallerExtractor.extractEntryFromStream(
            inputStream = ByteArrayInputStream(zipBytes),
            targetEntryPath = "dist/Facuedu.uy-Mennseger-Setup-1.0.0.exe",
            outputStream = output
        )

        assertEquals(expectedPayload.size.toLong(), written)
        assertEquals(expectedPayload.toList(), output.toByteArray().toList())
    }
}
