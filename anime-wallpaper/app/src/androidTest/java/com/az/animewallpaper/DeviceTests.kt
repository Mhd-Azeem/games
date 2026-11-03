package com.az.animewallpaper

import android.graphics.*
import android.net.Uri
import android.view.*
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.az.animewallpaper.editor.EditorOverlay
import com.az.animewallpaper.models.MotionPoint
import com.az.animewallpaper.models.Physics
import com.az.animewallpaper.rendering.ImageFrame
import com.az.animewallpaper.storage.ProjectStore
import com.az.animewallpaper.storage.RecentProjects
import java.io.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceTests {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledPortraitModelExecutesOffline() {
        val store=ProjectStore(context);val project=store.starter(0)
        val bitmap=store.decode(project.image,512)
        try {
            val mask=com.az.animewallpaper.spatial.SpatialAnalyzer(context).portrait(bitmap)
            if(mask!=null) {
                assertEquals(128*128,mask.size)
                assertTrue(mask.all { it.isFinite() && it in 0f..1f })
            }
        } finally { bitmap.recycle();store.delete(project) }
    }

    @Test
    fun oneTapSpatialAnalyzesSavesAndToggles() {
        val store = ProjectStore(context)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                find(a.window.decorView, "Browse starter wallpapers")!!.performClick()
                find(a.window.decorView, "Create: Anime • moonlit silhouette")!!.performClick()
            }
            var ready = false
            repeat(40) {
                if (!ready) {
                    Thread.sleep(250)
                    scenario.onActivity { a -> ready = find(a.window.decorView, "Spatial") != null }
                }
            }
            assertTrue(ready)
            scenario.onActivity { a -> find(a.window.decorView, "Spatial")!!.performClick() }
            var enabled = false
            repeat(120) {
                if (!enabled) {
                    Thread.sleep(250)
                    scenario.onActivity { a ->
                        enabled = find(a.window.decorView, "Spatial on") != null
                    }
                }
            }
            assertTrue("One-tap analysis did not finish", enabled)
            var id = ""
            scenario.onActivity { a ->
                val p = overlay(a.window.decorView)!!.project
                id = p.id
                assertTrue(p.spatial.enabled)
                assertTrue(p.spatial.kind in listOf("subject", "scene"))
                assertTrue(p.spatial.depth.all { it.isFinite() && it in 0f..1f })
                find(a.window.decorView, "Spatial on")!!.performClick()
                assertTrue(overlay(a.window.decorView)!!.project.spatial.strong)
                find(a.window.decorView, "Spatial strong")!!.performClick()
                assertFalse(overlay(a.window.decorView)!!.project.spatial.enabled)
                find(a.window.decorView, "Spatial")!!.performClick()
                assertTrue(overlay(a.window.decorView)!!.project.spatial.enabled)
            }
            Thread.sleep(1000)
            assertTrue(store.load(id).spatial.enabled)
            store.setActive(store.load(id))
            assertArrayEquals(store.load(id).spatial.depth, store.active()!!.spatial.depth, .001f)
            val screenshot =
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val dir = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
            File(dir, "spatial-auto.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        }
    }

    @Test
    fun spatialRepairRemovesSubjectAndPreservesBackground() {
        val b = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.BLUE)
        Canvas(b).drawRect(30f, 25f, 50f, 75f, Paint().apply { color = Color.RED })
        val mask =
            FloatArray(128 * 128) { i -> if (i % 128 in 48..80 && i / 128 in 30..97) 1f else 0f }
        val (background, foreground) = com.az.animewallpaper.spatial.SpatialLayers.build(b, mask)
        assertEquals(Color.BLUE, background.getPixel(0, 0))
        // A high-contrast background line inside the old dilation halo must stay exact.
        val detail = Color.GREEN
        b.setPixel(27, 50, detail)
        val (detailedBackground, detailedForeground) = com.az.animewallpaper.spatial.SpatialLayers.build(b, mask)
        assertEquals("Spatial damaged visible background beside the subject", detail, detailedBackground.getPixel(27, 50))
        detailedBackground.recycle()
        detailedForeground.recycle()
        assertTrue(Color.blue(background.getPixel(40, 50)) > 200)
        assertTrue(Color.alpha(foreground.getPixel(40, 50)) > 240)
        assertEquals(0, Color.alpha(foreground.getPixel(0, 0)))
        // Dark antialiased fringe just beyond the coarse mask must not survive in backing.
        b.setPixel(29, 50, Color.BLACK)
        val (cleaned, cutout) = com.az.animewallpaper.spatial.SpatialLayers.build(b, mask)
        assertNotEquals("Original subject fringe remained behind the moving cutout", Color.BLACK, cleaned.getPixel(29, 50))
        cleaned.recycle(); cutout.recycle()
        b.recycle()
        background.recycle()
        foreground.recycle()
    }

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
        Thread.sleep(2000)
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
        instrumentation.uiAutomation.executeShellCommand("input keyevent 3").close()
        Thread.sleep(2000)
        var visible = false
        repeat(3) {
            if (!visible) {
                val captured = instrumentation.uiAutomation.takeScreenshot()
                val home = captured?.copy(Bitmap.Config.ARGB_8888, false)
                captured?.recycle()
                if (home != null) {
                    val positions = listOf(.3f to .45f, .5f to .45f, .7f to .45f)
                    visible =
                        positions.all { (x, y) ->
                            val color =
                                home.getPixel((home.width * x).toInt(), (home.height * y).toInt())
                            Color.red(color) + Color.green(color) + Color.blue(color) > 30
                        }
                    File(context.getExternalFilesDir(null), "applied-home.png").outputStream().use {
                        home.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    home.recycle()
                }
                if (!visible) Thread.sleep(1000)
            }
        }
        assertTrue("Applied wallpaper remained black after settling", visible)
        store.delete(project)
    }

    private fun overlay(view: View): EditorOverlay? {
        if (view is EditorOverlay) return view
        if (view is ViewGroup)
            for (i in 0 until view.childCount) overlay(view.getChildAt(i))?.let {
                return it
            }
        return null
    }

    private fun description(view: View, text: String): View? {
        if (view.contentDescription?.toString() == text) return view
        if (view is ViewGroup)
            for (i in 0 until view.childCount) description(view.getChildAt(i), text)?.let {
                return it
            }
        return null
    }

    private fun hasText(view: View, text: String): Boolean {
        if (view is TextView && view.text.toString() == text) return true
        if (view is ViewGroup)
            for (i in 0 until view.childCount) if (hasText(view.getChildAt(i), text)) return true
        return false
    }

    @Test
    fun multiSelectionDragDeleteUndoAndFocus() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                find(a.window.decorView, "Browse starter wallpapers")!!.performClick()
                find(a.window.decorView, "Create: Anime • moonlit silhouette")!!.performClick()
            }
            var ready = false
            repeat(40) {
                if (!ready) {
                    Thread.sleep(250)
                    scenario.onActivity { a -> ready = overlay(a.window.decorView)?.pixels != null }
                }
            }
            assertTrue(ready)
            var ids = emptyList<String>()
            scenario.onActivity { a ->
                val o = overlay(a.window.decorView)!!
                val first = MotionPoint(x = .28f, y = .42f, physics = Physics(intensity = .25f))
                val second = MotionPoint(x = .68f, y = .42f, physics = Physics(intensity = 1.25f))
                val untouched = MotionPoint(x = .5f, y = .72f)
                ids = listOf(first.id, second.id, untouched.id)
                o.project.points.clear()
                o.project.points.addAll(listOf(first, second, untouched))
                o.changed()
                find(a.window.decorView, "Multi-select")!!.performClick()
                val frame =
                    ImageFrame(o.width.toFloat(), o.height.toFloat(), o.imageW, o.imageH, o.project)
                fun event(action: Int, x: Float, y: Float) {
                    val v = frame.screen(x, y)
                    val now = android.os.SystemClock.uptimeMillis()
                    val e = MotionEvent.obtain(now, now, action, v.first, v.second, 0)
                    o.dispatchTouchEvent(e)
                    e.recycle()
                }
                fun tap(x: Float, y: Float) {
                    event(MotionEvent.ACTION_DOWN, x, y)
                    event(MotionEvent.ACTION_UP, x, y)
                }
                tap(.28f, .42f)
                tap(.68f, .42f)
                assertEquals(setOf(first.id, second.id), o.selectedPoints)
                event(MotionEvent.ACTION_DOWN, .28f, .42f)
                event(MotionEvent.ACTION_MOVE, .38f, .52f)
                event(MotionEvent.ACTION_UP, .38f, .52f)
                assertEquals(.38f, first.x, .002f)
                assertEquals(.78f, second.x, .002f)
                assertEquals(.72f, untouched.y, 0f)
                assertEquals(.25f, first.physics.intensity, 0f)
                assertEquals(1.25f, second.physics.intensity, 0f)
                tap(second.x, second.y)
                assertEquals(setOf(first.id), o.selectedPoints)
                tap(second.x, second.y)
            }
            Thread.sleep(400)
            val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "multi-selection.png").outputStream().use {
                shot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            shot.recycle()
            scenario.recreate()
            scenario.onActivity { a ->
                val o = overlay(a.window.decorView)!!
                assertEquals(ids.take(2).toSet(), o.selectedPoints)
                find(a.window.decorView, "Focus canvas")!!.performClick()
                assertTrue(find(a.window.decorView, "Show tools")!!.isShown)
                assertFalse(find(a.window.decorView, "Multi-select")!!.isShown)
                find(a.window.decorView, "Show tools")!!.performClick()
                find(a.window.decorView, "Remove points")!!.performClick()
                assertEquals(listOf(ids[2]), o.project.points.map { it.id })
                find(a.window.decorView, "Undo")!!.performClick()
                assertEquals(ids, overlay(a.window.decorView)!!.project.points.map { it.id })
            }
        }
    }

    @Test
    fun removingFromRecentsKeepsSavedProject() {
        val store = ProjectStore(context)
        val recent = RecentProjects(context)
        val p = store.starter(2)
        p.name = "Recent removal test"
        store.save(p)
        recent.recordOpen(p.id)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                description(a.window.decorView, "Remove ${p.name} from Recents")!!.performClick()
                assertFalse(hasText(a.window.decorView, p.name))
                assertFalse(RecentProjects(context).visible(listOf(p)).isNotEmpty())
                assertTrue(File(store.load(p.id).image).exists())
                find(a.window.decorView, "My Wallpapers")!!.performClick()
                assertTrue(hasText(a.window.decorView, p.name))
            }
        }
        recent.recordOpen(p.id)
        assertEquals(p.id, recent.visible(listOf(p)).single().id)
        store.delete(p)
    }

    @Test
    fun gridAndDockKeepAffectedAreaVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                find(a.window.decorView, "Browse starter wallpapers")!!.performClick()
                find(a.window.decorView, "Create: Anime • moonlit silhouette")!!.performClick()
            }
            var ready = false
            repeat(40) {
                if (!ready) {
                    Thread.sleep(250)
                    scenario.onActivity { a -> ready = overlay(a.window.decorView)?.pixels != null }
                }
            }
            assertTrue(ready)
            scenario.onActivity { a ->
                val buttons =
                    listOf("Undo", "Redo", "Save", "Preview", "Spatial", "Focus canvas").map {
                        find(a.window.decorView, it)!!
                    }
                assertTrue(
                    buttons.all {
                        kotlin.math.abs(it.width - buttons[0].width) <= 1 &&
                            it.height == buttons[0].height
                    }
                )
                assertEquals(buttons[0].top, buttons[1].top)
                val gap = buttons[1].left - (buttons[0].left + buttons[0].width)
                assertTrue(gap > 0 && gap <= 8 * a.resources.displayMetrics.density)
                val o = overlay(a.window.decorView)!!
                o.project.zoom = 3f
                o.project.offsetY = -.4f
                o.project.offsetX = .2f
                val point = MotionPoint(name = "Lower area", x = .5f, y = .94f)
                o.project.points.add(point)
                o.selectedPoint = point.id
                o.changed()
                find(a.window.decorView, "Physics")!!.performClick()
            }
            Thread.sleep(500)
            instrumentation.waitForIdleSync()
            var originalCanvasHeight = 0
            scenario.onActivity { a ->
                val o = overlay(a.window.decorView)!!
                val dock = description(a.window.decorView, "Editor settings panel")!!
                val imageRect = Rect()
                val dockRect = Rect()
                o.getGlobalVisibleRect(imageRect)
                dock.getGlobalVisibleRect(dockRect)
                assertTrue("Settings cover the preview", imageRect.bottom <= dockRect.top)
                assertTrue(o.fitPreview)
                val f =
                    ImageFrame(
                        o.width.toFloat(),
                        o.height.toFloat(),
                        o.imageW,
                        o.imageH,
                        o.project,
                        true,
                    )
                val lower = f.screen(.5f, .94f)
                assertTrue(lower.second in 0f..o.height.toFloat())
                assertTrue(lower.first in 0f..o.width.toFloat())
                originalCanvasHeight = o.height
                val header =
                    listOf("Smaller panel", "Test motion", "Done").map {
                        find(a.window.decorView, it)!!
                    }
                assertTrue(header.all { kotlin.math.abs(it.width - header[0].width) <= 1 })
                fun seek(v: View): SeekBar? {
                    if (v is SeekBar) return v
                    if (v is ViewGroup)
                        for (k in 0 until v.childCount) seek(v.getChildAt(k))?.let {
                            return it
                        }
                    return null
                }
                val slider = seek(dock)!!
                val down = android.os.SystemClock.uptimeMillis()
                val x = slider.width * .9f
                val y = slider.height / 2f
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { i, action ->
                    val event = MotionEvent.obtain(down, down + i * 40L, action, x, y, 0)
                    slider.dispatchTouchEvent(event)
                    event.recycle()
                }
            }
            Thread.sleep(400)
            val shot = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "physics-visible-area.png").outputStream().use {
                shot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            shot.recycle()
            scenario.onActivity { a ->
                val o = overlay(a.window.decorView)!!
                assertTrue(o.project.points.last().physics.intensity > 1.5f)
                find(a.window.decorView, "Test motion")!!.performClick()
                find(a.window.decorView, "Smaller panel")!!.performClick()
            }
            Thread.sleep(250)
            scenario.onActivity { a ->
                val o = overlay(a.window.decorView)!!
                assertTrue(o.height > originalCanvasHeight)
                find(a.window.decorView, "Done")!!.performClick()
                assertFalse(o.fitPreview)
                assertEquals(3f, o.project.zoom, 0f)
                assertEquals(-.4f, o.project.offsetY, 0f)
                assertTrue(find(a.window.decorView, "Undo")!!.isShown)
            }
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
