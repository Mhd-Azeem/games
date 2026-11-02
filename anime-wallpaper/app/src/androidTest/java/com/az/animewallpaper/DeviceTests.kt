package com.az.animewallpaper

import android.graphics.*
import android.net.Uri
import android.view.*
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.az.animewallpaper.storage.ProjectStore
import java.io.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceTests {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun imageProjectAndAppliedSnapshotSurviveDeletion() {
        val store = ProjectStore(context)
        val p = store.starter(0)
        p.points[0].physics.intensity = 1.4f
        store.save(p)
        assertEquals(1.4f, store.load(p.id).points[0].physics.intensity, .001f)
        store.setActive(p)
        store.delete(p)
        val active = store.active()!!
        assertTrue(File(active.image).exists())
        assertEquals(1.4f, active.points[0].physics.intensity, .001f)
    }

    @Test
    fun galleryImportRetainsOriginalAndHandlesLargeImage() {
        val source = File(context.cacheDir, "large.png")
        val b = Bitmap.createBitmap(3000, 2400, Bitmap.Config.ARGB_8888)
        source.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.recycle()
        val store = ProjectStore(context)
        val p = store.importImage(Uri.fromFile(source))
        val decoded = store.decode(p.image, 1024)
        assertTrue(decoded.width <= 1024)
        assertTrue(decoded.height <= 1024)
        decoded.recycle()
        store.delete(p)
        assertTrue(source.exists())
        source.delete()
    }

    @Test
    fun rejectsEmptyImageWithoutLeavingProject() {
        val store = ProjectStore(context)
        val before = store.list().size
        val empty = File(context.cacheDir, "empty-image").apply { writeBytes(byteArrayOf()) }
        try {
            store.importImage(Uri.fromFile(empty))
            fail("Empty image accepted")
        } catch (_: Exception) {}
        assertEquals(before, store.list().size)
        empty.delete()
    }

    @Test
    fun archiveRoundTripAndTraversalDefense() {
        val store = ProjectStore(context)
        val p = store.starter(2)
        val bytes = ByteArrayOutputStream()
        store.export(p, bytes)
        val copy = store.importProject(ByteArrayInputStream(bytes.toByteArray()))
        assertNotEquals(p.id, copy.id)
        assertTrue(File(copy.image).exists())
        val invalid = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(invalid).use {
            it.putNextEntry(java.util.zip.ZipEntry("../outside"))
            it.write(1)
            it.closeEntry()
        }
        try {
            store.importProject(ByteArrayInputStream(invalid.toByteArray()))
            fail("Traversal archive accepted")
        } catch (_: IllegalArgumentException) {}
        store.delete(p)
        store.delete(copy)
    }

    private fun find(view: View, label: String): Button? {
        if (view is Button && view.text.toString() == label) return view
        if (view is ViewGroup)
            for (i in 0 until view.childCount) find(view.getChildAt(i), label)?.let {
                return it
            }
        return null
    }

    @Test
    fun navigationAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(find(activity.window.decorView, "Settings"))
                find(activity.window.decorView, "Settings")!!.performClick()
                assertNotNull(find(activity.window.decorView, "Diagnostics"))
            }
            scenario.recreate()
            scenario.onActivity { assertNotNull(it.window.decorView) }
        }
    }

    @Test
    fun starterEditorAndManualPreviewRender() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                find(a.window.decorView, "Browse starter wallpapers")!!.performClick()
                find(a.window.decorView, "Create: Anime • moonlit silhouette")!!.performClick()
            }
            var ready = false
            repeat(40) {
                if (!ready) {
                    Thread.sleep(250)
                    scenario.onActivity { a -> ready = find(a.window.decorView, "Preview") != null }
                }
            }
            assertTrue("Editor did not open", ready)
            Thread.sleep(1000)
            fun screenshot(name: String) {
                val bitmap =
                    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                assertNotNull(bitmap)
                File(context.getExternalFilesDir(null), name).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            screenshot("editor.png")
            scenario.onActivity { a ->
                find(a.window.decorView, "Preview")!!.performClick()
                find(a.window.decorView, "Manual mode")!!.performClick()
                assertNotNull(find(a.window.decorView, "Test shake"))
                find(a.window.decorView, "Test shake")!!.performClick()
            }
            Thread.sleep(1000)
            screenshot("preview.png")
            scenario.recreate()
            scenario.onActivity { a -> assertNotNull(find(a.window.decorView, "Save")) }
        }
    }

    @Test
    fun wallpaperServiceIsRegistered() {
        val info =
            context.packageManager.getServiceInfo(
                android.content.ComponentName(context, MotionWallpaperService::class.java),
                0,
            )
        assertEquals("android.permission.BIND_WALLPAPER", info.permission)
        assertTrue(info.exported)
    }
}
