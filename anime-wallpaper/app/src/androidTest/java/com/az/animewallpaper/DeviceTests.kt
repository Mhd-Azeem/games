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
            it.putNextEntry(java.util.zip.ZipEntry("aa/outside"))
            it.write(1)
            it.closeEntry()
        }
        val unsafe = invalid.toByteArray()
        val from = "aa/outside".toByteArray()
        val to = "../outside".toByteArray()
        for (offset in 0..unsafe.size - from.size) {
            if (from.indices.all { unsafe[offset + it] == from[it] }) to.copyInto(unsafe, offset)
        }
        try {
            store.importProject(ByteArrayInputStream(unsafe))
            fail("Traversal archive accepted")
        } catch (_: IllegalArgumentException) {
            // Application-level path validation on older Android versions.
        } catch (_: java.util.zip.ZipException) {
            // Android 14+ rejects traversal before nextEntry reaches our validator.
        }
        assertFalse(File(context.cacheDir, "outside").exists())
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
    fun wallpaperAppliesThroughAndroidPicker() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = ProjectStore(context)
        val project = store.starter(0)
        store.setActive(project)
        context.startActivity(
            android.content
                .Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(
                    android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    android.content.ComponentName(context, MotionWallpaperService::class.java),
                )
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        fun click(text: String): Boolean {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByText(text)
            val target =
                nodes.firstOrNull { it.text?.toString()?.equals(text, true) == true }
                    ?: return false
            var node: android.view.accessibility.AccessibilityNodeInfo? = target
            while (node != null) {
                if (node.isClickable)
                    return node.performAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
                    )
                node = node.parent
            }
            return false
        }
        var clicked = false
        repeat(60) {
            if (!clicked) {
                Thread.sleep(200)
                clicked = click("Set wallpaper")
            }
        }
        val shot = instrumentation.uiAutomation.takeScreenshot()
        if (shot != null) {
            File(context.getExternalFilesDir(null), "native-picker.png").outputStream().use {
                shot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            shot.recycle()
        }
        assertTrue("Native wallpaper confirmation button was not found", clicked)
        val manager = android.app.WallpaperManager.getInstance(context)
        var applied = false
        repeat(60) {
            if (!applied) {
                click("Home screen")
                Thread.sleep(200)
                applied = manager.wallpaperInfo?.packageName == context.packageName
            }
        }
        assertTrue("Android did not activate the live wallpaper service", applied)
        store.delete(project)
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
