package com.livingroomhq.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AndroidManifestSecurityTest {

    @Test
    fun `manifest disables cleartext traffic globally`() {
        // Try locating AndroidManifest.xml from current directory or relative root
        var manifestFile = File("src/main/AndroidManifest.xml")
        if (!manifestFile.exists()) {
            manifestFile = File("app/src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml should exist", manifestFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        val applicationNodes = doc.getElementsByTagName("application")
        assertTrue("Manifest should have an application tag", applicationNodes.length > 0)

        val applicationElement = applicationNodes.item(0) as org.w3c.dom.Element
        val androidNs = "http://schemas.android.com/apk/res/android"
        val usesCleartext = applicationElement.getAttributeNS(androidNs, "usesCleartextTraffic")

        assertEquals("android:usesCleartextTraffic should be set to false", "false", usesCleartext)
    }
}
