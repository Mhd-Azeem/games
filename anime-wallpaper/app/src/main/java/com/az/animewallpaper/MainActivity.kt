package com.az.animewallpaper

import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import com.az.animewallpaper.editor.*
import com.az.animewallpaper.models.*
import com.az.animewallpaper.models.Region
import com.az.animewallpaper.rendering.*
import com.az.animewallpaper.selection.Masks
import com.az.animewallpaper.sensors.*
import com.az.animewallpaper.storage.*
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : Activity() {
    private val bg = 0xff151619.toInt()
    private val surface = 0xff292b30.toInt()
    private val ink = 0xfff0f1f3.toInt()
    private val muted = 0xffaeb3bc.toInt()
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: ProjectStore
    private lateinit var root: LinearLayout
    private var project: Project? = null
    private var overlay: EditorOverlay? = null
    private var gl: GLSurfaceView? = null
    private var renderer: MotionRenderer? = null
    private var sensors: MotionSensors? = null
    private var preview = false
    private var manual = false
    private var dirty = false
    private var refreshing = false
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var sheet: Dialog? = null
    private var clipboard: Physics? = null
    private var exportProject: Project? = null
    private var activeScreen = "Home"
    private val prefs by lazy { getSharedPreferences("studio", MODE_PRIVATE) }
    private val ticker =
        object : Runnable {
            override fun run() {
                gl?.requestRender()
                if (gl != null)
                    handler.postDelayed(
                        this,
                        if (renderer?.moving == true) 1000L / (project?.fps ?: 30) else 150L,
                    )
            }
        }
    private val refresh = Runnable {
        renderer?.pending = project?.let { Codec.decode(Codec.encode(it)) }
        refreshing = false
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun round(color: Int = surface, r: Int = 22) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(r).toFloat()
        }

    private fun text(value: String, size: Float = 16f, color: Int = ink) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun row() =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(ink)
            background = round()
            setPadding(dp(12), dp(4), dp(12), dp(4))
            minimumHeight = dp(46)
            setOnClickListener { action() }
        }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(
            button(label, action),
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(7) },
        )
    }

    private fun toolbar(parent: LinearLayout, items: List<Pair<String, () -> Unit>>) {
        val scroll = HorizontalScrollView(this)
        val line = row()
        items.forEach { (name, action) ->
            line.addView(
                button(name, action),
                LinearLayout.LayoutParams(-2, dp(46)).apply { marginEnd = dp(6) },
            )
        }
        scroll.addView(line)
        parent.addView(scroll)
    }

    private fun page(title: String, back: (() -> Unit)? = null): LinearLayout {
        stopCanvas()
        root =
            column().apply {
                setBackgroundColor(bg)
                setPadding(dp(12), 0, dp(12), 0)
            }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(dp(12), bars.top, dp(12), bars.bottom)
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
        val top = row()
        if (back != null) top.addView(button("‹", back))
        top.addView(text(title, 22f), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)
        return root
    }

    private fun scroll(parent: LinearLayout): LinearLayout {
        val box = column()
        val sc = ScrollView(this)
        sc.isFillViewport = true
        sc.addView(box)
        parent.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        return box
    }

    private fun message(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }

    private fun runIO(action: () -> Unit) {
        io.execute {
            try {
                action()
            } catch (e: Exception) {
                handler.post { message(e.message ?: "Unable to complete operation") }
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = ProjectStore(this)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val restore = File(cacheDir, "editor-session.json")
        if (state?.getBoolean("editorActive") == true && restore.isFile) {
            try {
                project = Codec.decode(restore.readText())
                editor()
                return
            } catch (_: Exception) {}
        }
        home()
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("editorActive", project != null)
        project?.let { File(cacheDir, "editor-session.json").writeText(Codec.encode(it)) }
        super.onSaveInstanceState(out)
    }

    override fun onResume() {
        super.onResume()
        gl?.onResume()
        startSensors()
        handler.removeCallbacks(ticker)
        if (gl != null) handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        sensors?.stop()
        gl?.onPause()
        saveQuiet()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sensors?.stop()
        io.shutdown()
        super.onDestroy()
    }

    @Deprecated("Android activity back handling")
    override fun onBackPressed() {
        if (preview) {
            preview = false
            editor()
        } else if (project != null) {
            saveQuiet()
            project = null
            home()
        } else super.onBackPressed()
    }

    private fun stopCanvas() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(refresh)
        refreshing = false
        sensors?.stop()
        sensors = null
        gl?.onPause()
        gl = null
        renderer = null
        overlay = null
        sheet?.dismiss()
        sheet = null
    }

    private fun home(tab: String = "Home") {
        activeScreen = tab
        val parent = page(if (tab == "Home") "AZ / Motion Studio" else "My Wallpapers")
        val content = scroll(parent)
        if (tab == "Home") {
            content.addView(text("Make still images move.", 29f))
            content.addView(
                text("Paint a region. Add an anchor. Tilt to bring it to life.", 15f, muted)
            )
            addButton(content, "＋ Import from gallery") { pickImage() }
            addButton(content, "Browse starter wallpapers") { gallery() }
        }
        addButton(content, "Import project / restore backup") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE),
                102,
            )
        }
        content.addView(text(if (tab == "Home") "Recent projects" else "Saved wallpapers", 19f))
        val list = store.list()
        if (list.isEmpty())
            content.addView(text("Your saved projects will appear here.", 14f, muted))
        list.forEach { p ->
            val card =
                column().apply {
                    background = round()
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                }
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            val line = row()
            val image =
                ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = p.name
                }
            line.addView(image, LinearLayout.LayoutParams(dp(62), dp(90)))
            line.addView(
                text(
                    p.name + if (prefs.getString("active", "") == p.id) "  • last selected" else "",
                    17f,
                ),
                LinearLayout.LayoutParams(0, -2, 1f),
            )
            card.addView(line)
            runIO {
                val b = store.decode(p.image, 160)
                handler.post { image.setImageBitmap(b) }
            }
            toolbar(
                card,
                listOf(
                    "Edit" to { open(p) },
                    "Apply" to { applyWallpaper(p) },
                    "More" to { projectMenu(p) },
                ),
            )
        }
        toolbar(
            parent,
            listOf(
                "Home" to { home() },
                "My Wallpapers" to { home("My Wallpapers") },
                "Settings" to { settings() },
            ),
        )
    }

    private fun gallery() {
        val parent = page("Offline starter gallery") { home() }
        val content = scroll(parent)
        content.addView(
            text(
                "Six original illustrations, generated on your device. Each starts with a motion point.",
                14f,
                muted,
            )
        )
        val names =
            listOf(
                "Anime • moonlit silhouette",
                "Nature • forest",
                "Abstract • graphite aurora",
                "Fantasy • distant peaks",
                "Night sky • constellations",
                "Minimal • charcoal",
            )
        names.forEachIndexed { i, name ->
            val image =
                ImageView(this).apply {
                    setImageBitmap(Artwork.make(i))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = name
                }
            content.addView(
                image,
                LinearLayout.LayoutParams(-1, dp(220)).apply { topMargin = dp(12) },
            )
            addButton(content, "Create: $name") {
                runIO {
                    val p = store.starter(i)
                    handler.post { open(p) }
                }
            }
        }
    }

    private fun pickImage() {
        val intent =
            if (Build.VERSION.SDK_INT >= 33)
                Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
            else
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, 101)
    }

    @Deprecated("Platform activity result bridge")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (result != RESULT_OK) return
        val uri = data?.data ?: return
        when (request) {
            101 ->
                runIO {
                    val p = store.importImage(uri)
                    p.global.sensitivity = prefs.getInt("sensitivity", 100) / 100f
                    handler.post { open(p) }
                }
            102 ->
                runIO {
                    val p =
                        contentResolver.openInputStream(uri)?.use { store.importProject(it) }
                            ?: error("Unable to open backup")
                    handler.post { open(p) }
                }
            103 ->
                exportProject?.let { p ->
                    runIO {
                        contentResolver.openOutputStream(uri)?.use { store.export(p, it) }
                            ?: error("Unable to write export")
                        handler.post { message("Project exported") }
                    }
                }
        }
    }

    private fun open(p: Project) {
        p.fps = prefs.getInt("fps", p.fps)
        p.quality = prefs.getInt("quality", p.quality)
        project = p
        undo.clear()
        redo.clear()
        dirty = false
        preview = false
        editor()
    }

    private fun snapshot() {
        val p = project ?: return
        undo.addLast(Codec.encode(p))
        redo.clear()
        while (undo.size > 24 || undo.sumOf { it.length } > 8_000_000) undo.removeFirst()
    }

    private fun undo(forward: Boolean) {
        val source = if (forward) redo else undo
        val target = if (forward) undo else redo
        if (source.isEmpty()) return
        project?.let { target.addLast(Codec.encode(it)) }
        project = Codec.decode(source.removeLast())
        dirty = true
        editor()
    }

    private fun changed() {
        dirty = true
        overlay?.dirty()
        if (!refreshing) {
            refreshing = true
            handler.postDelayed(refresh, 70)
        }
    }

    private fun saveQuiet() {
        val p = project ?: return
        if (!dirty) return
        val copy = Codec.decode(Codec.encode(p))
        dirty = false
        runIO { store.save(copy) }
    }

    private fun save() {
        val p = project ?: return
        val copy = Codec.decode(Codec.encode(p))
        runIO {
            store.save(copy)
            handler.post {
                dirty = false
                message("Project saved")
            }
        }
    }

    private fun editor() {
        val p = project ?: return
        activeScreen = "Editor"
        val parent =
            page(p.name) {
                saveQuiet()
                project = null
                home()
            }
        toolbar(
            parent,
            listOf(
                "Undo" to { undo(false) },
                "Redo" to { undo(true) },
                "Save" to { save() },
                "Preview" to
                    {
                        preview = true
                        previewScreen()
                    },
                "Rename" to
                    {
                        nameDialog(p.name) {
                            snapshot()
                            p.name = it
                            changed()
                            editor()
                        }
                    },
            ),
        )
        canvas(parent, true)
        val status = text("Select or drag points • pinch to zoom • Pan to reposition", 12f, muted)
        parent.addView(status)
        fun setTool(t: Tool) {
            if (
                t in
                    listOf(
                        Tool.BRUSH,
                        Tool.ERASER,
                        Tool.LASSO,
                        Tool.POLYGON,
                        Tool.ELLIPSE,
                        Tool.RECTANGLE,
                        Tool.WAND,
                    ) && selectedRegion() == null
            )
                addRegion()
            overlay?.tool = t
            status.text =
                "${t.name.lowercase().replaceFirstChar{it.uppercase()}} • ${if(t==Tool.POLYGON)"Tap vertices, then Close polygon" else "Draw directly on the image"}"
        }
        toolbar(
            parent,
            listOf(
                "Select" to { setTool(Tool.SELECT) },
                "Add point" to { setTool(Tool.POINT) },
                "Brush" to { setTool(Tool.BRUSH) },
                "Eraser" to { setTool(Tool.ERASER) },
                "Lasso" to { setTool(Tool.LASSO) },
                "Polygon" to { setTool(Tool.POLYGON) },
                "Close polygon" to { overlay?.finishPolygon() },
                "Ellipse" to { setTool(Tool.ELLIPSE) },
                "Rectangle" to { setTool(Tool.RECTANGLE) },
                "Wand" to { setTool(Tool.WAND) },
                "Pan" to { setTool(Tool.PAN) },
            ),
        )
        toolbar(
            parent,
            listOf(
                "Layers" to { layers() },
                "Physics" to { physicsSheet() },
                "Global physics" to
                    {
                        overlay?.selectedPoint = ""
                        overlay?.selectedRegion = ""
                        physicsSheet()
                    },
                "Brush options" to { brushSheet() },
                "Crop / frame" to { frameSheet() },
                "Hide controls" to
                    {
                        overlay?.let {
                            it.hidden = !it.hidden
                            it.invalidate()
                        }
                    },
                "Project" to { projectMenu(p) },
            ),
        )
    }

    private fun canvas(parent: LinearLayout, editing: Boolean) {
        val p = project ?: return
        val container = FrameLayout(this)
        parent.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        val r = MotionRenderer(this)
        renderer = r
        r.pending = Codec.decode(Codec.encode(p))
        val view = GLSurfaceView(this)
        view.setEGLContextClientVersion(2)
        view.setRenderer(r)
        view.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        gl = view
        container.addView(view, FrameLayout.LayoutParams(-1, -1))
        if (editing) {
            val o = EditorOverlay(this)
            overlay = o
            o.project = p
            o.checkpoint = { snapshot() }
            o.changed = { changed() }
            o.selection = {}
            container.addView(o, FrameLayout.LayoutParams(-1, -1))
            runIO {
                val b = store.decode(p.image, 256)
                val small = Bitmap.createScaledBitmap(b, 128, 128, true)
                val pixels = IntArray(128 * 128)
                small.getPixels(pixels, 0, 128, 0, 0, 128, 128)
                val w = b.width.toFloat()
                val h = b.height.toFloat()
                small.recycle()
                b.recycle()
                handler.post {
                    if (overlay === o) {
                        o.imageW = w
                        o.imageH = h
                        o.pixels = pixels
                        o.dirty()
                    }
                }
            }
        }
        sensors =
            MotionSensors(this) { x, y ->
                if (!manual) {
                    r.inputX = x
                    r.inputY = y
                }
            }
        startSensors()
        handler.post(ticker)
    }

    private fun startSensors() {
        if (!manual) sensors?.start((project?.fps ?: 30) <= 20)
    }

    private fun selectedRegion() = project?.regions?.find { it.id == overlay?.selectedRegion }

    private fun selectedPoint() = project?.points?.find { it.id == overlay?.selectedPoint }

    private fun addRegion() {
        val p = project ?: return
        if (p.regions.size >= 32) {
            message("32 regions maximum")
            return
        }
        snapshot()
        val r = Region(name = "Region ${p.regions.size+1}")
        p.regions.add(r)
        overlay?.selectedRegion = r.id
        overlay?.selectedPoint = ""
        changed()
    }

    private fun bottom(title: String, build: (LinearLayout) -> Unit) {
        sheet?.dismiss()
        val dialog = Dialog(this)
        sheet = dialog
        val box =
            column().apply {
                background = round(bg, 28)
                setPadding(dp(16), dp(10), dp(16), dp(16))
            }
        val head = row()
        head.addView(text(title, 20f), LinearLayout.LayoutParams(0, -2, 1f))
        var expanded = false
        head.addView(
            button("↕") {
                expanded = !expanded
                dialog.window?.setLayout(
                    -1,
                    (resources.displayMetrics.heightPixels * (if (expanded) .88f else .58f)).toInt(),
                )
            }
        )
        head.addView(button("Done") { dialog.dismiss() })
        box.addView(head)
        val content = scroll(box)
        build(content)
        dialog.setContentView(box)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setDimAmount(.3f)
            setLayout(-1, (resources.displayMetrics.heightPixels * .58f).toInt())
        }
        dialog.show()
        dialog.window?.setLayout(-1, (resources.displayMetrics.heightPixels * .58f).toInt())
    }

    private fun slider(
        parent: LinearLayout,
        label: String,
        value: Float,
        min: Float,
        max: Float,
        steps: Int = 100,
        update: (Float) -> Unit,
    ) {
        val title = text("$label: ${"%.2f".format(value)}", 14f)
        parent.addView(title)
        parent.addView(
            SeekBar(this).apply {
                this.max = steps
                progress = ((value - min) / (max - min) * steps).toInt()
                progressTintList = ColorStateList.valueOf(0xffb9c4d1.toInt())
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onStartTrackingTouch(bar: SeekBar?) {
                            snapshot()
                        }

                        override fun onStopTrackingTouch(bar: SeekBar?) {}

                        override fun onProgressChanged(bar: SeekBar?, v: Int, fromUser: Boolean) {
                            if (fromUser) {
                                val n = min + (max - min) * v / steps
                                title.text = "$label: ${"%.2f".format(n)}"
                                update(n)
                                changed()
                            }
                        }
                    }
                )
            }
        )
    }

    private fun toggle(
        parent: LinearLayout,
        label: String,
        value: Boolean,
        update: (Boolean) -> Unit,
    ) {
        parent.addView(
            Switch(this).apply {
                text = label
                setTextColor(ink)
                isChecked = value
                setOnCheckedChangeListener { _, checked ->
                    snapshot()
                    update(checked)
                    changed()
                }
            }
        )
    }

    private fun choices(title: String, values: List<String>, select: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(values.toTypedArray()) { _, i -> select(i) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun nameDialog(current: String, save: (String) -> Unit) {
        val input =
            EditText(this).apply {
                setText(current)
                setSingleLine()
                setSelectAllOnFocus(true)
            }
        AlertDialog.Builder(this)
            .setTitle("Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                input.text.toString().trim().take(80).takeIf { it.isNotEmpty() }?.let(save)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun physicsSheet() {
        val p = project ?: return
        val point = selectedPoint()
        val region = selectedRegion()
        val config = point?.physics ?: region?.physics ?: p.global
        bottom(point?.name ?: region?.name ?: "Global physics") { box ->
            addButton(box, "Preset: Gentle / Natural / Soft / Elastic / Strong") {
                choices(
                    "Physics preset",
                    listOf("Gentle", "Natural", "Soft", "Elastic", "Strong"),
                ) { i ->
                    snapshot()
                    val values =
                        listOf(
                            Physics(.3f, stiffness = 45f, damping = 70f),
                            Physics(),
                            Physics(.65f, stiffness = 20f, damping = 65f),
                            Physics(.9f, stiffness = 30f, damping = 15f),
                            Physics(1.3f, stiffness = 60f, damping = 40f),
                        )
                    val v = values[i]
                    config.intensity = v.intensity
                    config.stiffness = v.stiffness
                    config.damping = v.damping
                    changed()
                    physicsSheet()
                }
            }
            slider(box, "Intensity %", config.intensity * 100, 0f, 200f) {
                config.intensity = it / 100
            }
            slider(box, "Sensitivity %", config.sensitivity * 100, 0f, 200f) {
                config.sensitivity = it / 100
            }
            slider(box, "Stiffness", config.stiffness, 0f, 100f) { config.stiffness = it }
            slider(box, "Damping", config.damping, 0f, 100f) { config.damping = it }
            slider(box, "Influence radius / 1000 px", config.radius * 1000, 1f, 500f) {
                config.radius = it / 1000
            }
            slider(box, "Maximum displacement / 1000 px", config.maxDisplacement * 1000, 0f, 100f) {
                config.maxDisplacement = it / 1000
            }
            slider(box, "Horizontal %", config.horizontal * 100, 0f, 200f) {
                config.horizontal = it / 100
            }
            slider(box, "Vertical %", config.vertical * 100, 0f, 200f) {
                config.vertical = it / 100
            }
            slider(box, "Delay ms", config.delay.toFloat(), 0f, 500f) { config.delay = it.toInt() }
            slider(box, "Feather %", config.feather * 100, 0f, 100f) { config.feather = it / 100 }
            addButton(
                box,
                "Direction: ${listOf("Free","Horizontal","Vertical")[config.direction]}",
            ) {
                choices("Direction", listOf("Free", "Horizontal", "Vertical")) {
                    snapshot()
                    config.direction = it
                    changed()
                    physicsSheet()
                }
            }
            if (point != null) {
                toggle(box, "Enabled", point.enabled) { point.enabled = it }
                toggle(box, "Fixed anchor (locks nearby pixels)", point.fixed) { point.fixed = it }
                addButton(box, "Assign point to region") {
                    choices("Region", listOf("No region") + p.regions.map { it.name }) { i ->
                        snapshot()
                        point.region = if (i == 0) "" else p.regions[i - 1].id
                        changed()
                    }
                }
            }
            if (region != null && point == null)
                toggle(box, "Enabled", region.enabled) { region.enabled = it }
            addButton(box, "Copy physics") {
                clipboard = config.copy()
                message("Physics copied")
            }
            addButton(box, "Paste physics") {
                clipboard?.let { v ->
                    snapshot()
                    val replacement = v.copy()
                    when {
                        point != null -> point.physics = replacement
                        region != null -> region.physics = replacement
                        else -> p.global = replacement
                    }
                    changed()
                    physicsSheet()
                }
            }
            addButton(box, "Reset physics") {
                snapshot()
                when {
                    point != null -> point.physics = Physics()
                    region != null -> region.physics = Physics()
                    else -> p.global = Physics(intensity = 1f)
                }
                changed()
                physicsSheet()
            }
        }
    }

    private fun brushSheet() {
        val o = overlay ?: return
        bottom("Selection tools") { box ->
            slider(box, "Brush size", o.brushRadius * 1000, 5f, 250f) { o.brushRadius = it / 1000 }
            slider(box, "Hardness %", o.hardness * 100, 0f, 100f) { o.hardness = it / 100 }
            slider(box, "Weight %", o.opacity * 100, 0f, 100f) { o.opacity = it / 100 }
            slider(box, "Wand color tolerance", o.tolerance.toFloat(), 0f, 255f) {
                o.tolerance = it.toInt()
            }
            slider(box, "Overlay opacity %", o.project.overlay * 100, 0f, 100f) {
                o.project.overlay = it / 100
            }
        }
    }

    private fun frameSheet() {
        val p = project ?: return
        bottom("Crop & framing") { box ->
            box.addView(
                text(
                    "Pan moves the image. Pinch zooms. Coordinates remain attached to the original image.",
                    14f,
                    muted,
                )
            )
            slider(box, "Zoom", p.zoom, .25f, 5f) { p.zoom = it }
            slider(box, "Horizontal position", p.offsetX, -1f, 1f) { p.offsetX = it }
            slider(box, "Vertical position", p.offsetY, -1f, 1f) { p.offsetY = it }
            toggle(box, "Fit entire image", p.fit) { p.fit = it }
            addButton(box, "Rotate 90°") {
                snapshot()
                p.rotation = (p.rotation + 90) % 360
                changed()
            }
            addButton(box, "Center / reset crop") {
                snapshot()
                p.zoom = 1f
                p.offsetX = 0f
                p.offsetY = 0f
                p.rotation = 0
                changed()
                frameSheet()
            }
        }
    }

    private fun layers() {
        val p = project ?: return
        bottom("Regions & movement points") { box ->
            addButton(box, "＋ Add region") {
                addRegion()
                layers()
            }
            p.regions.toList().asReversed().forEach { r ->
                val card =
                    column().apply {
                        background = round(surface)
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                    }
                box.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
                card.addView(
                    text(
                        "${if(r.enabled)"◉" else "○"} ${r.name}  ·  ${(r.physics.intensity*100).toInt()}% ${if(r.locked)"🔒" else ""}"
                    )
                )
                toolbar(
                    card,
                    listOf(
                        "Select" to
                            {
                                overlay?.selectedRegion = r.id
                                overlay?.selectedPoint = ""
                                overlay?.dirty()
                                sheet?.dismiss()
                            },
                        "Physics" to
                            {
                                overlay?.selectedRegion = r.id
                                overlay?.selectedPoint = ""
                                physicsSheet()
                            },
                        "Visible" to
                            {
                                snapshot()
                                r.enabled = !r.enabled
                                changed()
                                layers()
                            },
                        "Lock" to
                            {
                                snapshot()
                                r.locked = !r.locked
                                changed()
                                layers()
                            },
                        "More" to { regionMenu(r) },
                    ),
                )
            }
            box.addView(text("Movement points", 18f))
            p.points.toList().forEach { point ->
                toolbar(
                    box,
                    listOf(
                        "${if(point.fixed)"⚓" else "•"} ${point.name}" to
                            {
                                overlay?.selectedPoint = point.id
                                overlay?.selectedRegion = point.region
                                overlay?.dirty()
                                physicsSheet()
                            },
                        "Rename" to
                            {
                                nameDialog(point.name) {
                                    snapshot()
                                    point.name = it
                                    changed()
                                    layers()
                                }
                            },
                        "Delete" to
                            {
                                snapshot()
                                p.points.remove(point)
                                changed()
                                layers()
                            },
                    ),
                )
            }
            if (p.points.isEmpty())
                box.addView(text("Use Add Point, then tap the canvas.", 14f, muted))
        }
    }

    private fun regionMenu(r: Region) {
        val p = project ?: return
        choices(
            r.name,
            listOf(
                "Rename",
                "Duplicate",
                "Move up",
                "Move down",
                "Group name",
                "Merge with region",
                "Transform mask",
                "Delete",
            ),
        ) { i ->
            when (i) {
                0 ->
                    nameDialog(r.name) {
                        snapshot()
                        r.name = it
                        changed()
                        layers()
                    }
                1 -> {
                    if (p.regions.size < 32) {
                        snapshot()
                        p.regions.add(
                            r.copy(
                                id = id(),
                                name = r.name + " copy",
                                physics = r.physics.copy(),
                                mask = r.mask.copyOf(),
                                vertices = r.vertices.toMutableList(),
                            )
                        )
                        changed()
                        layers()
                    }
                }
                2,
                3 -> {
                    snapshot()
                    val index = p.regions.indexOf(r)
                    val next = (index + if (i == 2) 1 else -1).coerceIn(0, p.regions.lastIndex)
                    p.regions.removeAt(index)
                    p.regions.add(next, r)
                    changed()
                    layers()
                }
                4 ->
                    nameDialog(r.group) {
                        snapshot()
                        r.group = it
                        p.points.filter { v -> v.region == r.id }.forEach { v -> v.group = it }
                        changed()
                        layers()
                    }
                5 -> {
                    val others = p.regions.filter { it.id != r.id }
                    choices("Merge into", others.map { it.name }) { n ->
                        snapshot()
                        val dst = others[n]
                        for (k in dst.mask.indices) dst.mask[k] = max(dst.mask[k], r.mask[k])
                        p.points.filter { it.region == r.id }.forEach { it.region = dst.id }
                        p.regions.remove(r)
                        changed()
                        layers()
                    }
                }
                6 -> transform(r)
                7 -> {
                    snapshot()
                    p.regions.remove(r)
                    p.points.filter { it.region == r.id }.forEach { it.region = "" }
                    changed()
                    layers()
                }
            }
        }
    }

    private fun transform(r: Region) {
        if (r.locked) {
            message("Unlock this region first")
            return
        }
        val original = r.mask.copyOf()
        var dx = 0f
        var dy = 0f
        var scale = 1f
        var angle = 0f
        fun update() {
            r.mask = Masks.transform(original, dx, dy, scale, angle)
            changed()
        }
        bottom("Transform ${r.name}") { box ->
            slider(box, "Move X", dx, -1f, 1f) {
                dx = it
                update()
            }
            slider(box, "Move Y", dy, -1f, 1f) {
                dy = it
                update()
            }
            slider(box, "Scale", scale, .1f, 3f) {
                scale = it
                update()
            }
            slider(box, "Rotation °", 0f, -180f, 180f) {
                angle = it * PI.toFloat() / 180
                update()
            }
        }
    }

    private fun projectMenu(p: Project) {
        choices(
            p.name,
            listOf(
                "Save",
                "Save As / duplicate",
                "Rename",
                "Export / backup project",
                "Apply wallpaper",
                "Delete",
            ),
        ) { i ->
            when (i) {
                0 -> {
                    project = p
                    save()
                }
                1 ->
                    runIO {
                        val copy = store.duplicate(p)
                        handler.post { open(copy) }
                    }
                2 ->
                    nameDialog(p.name) {
                        p.name = it
                        runIO {
                            store.save(p)
                            handler.post {
                                if (project?.id == p.id) editor()
                                else
                                    home(
                                        if (activeScreen == "My Wallpapers") activeScreen
                                        else "Home"
                                    )
                            }
                        }
                    }
                3 -> {
                    exportProject = Codec.decode(Codec.encode(p))
                    startActivityForResult(
                        Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .setType("application/zip")
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .putExtra(
                                Intent.EXTRA_TITLE,
                                p.name.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".azmotion.zip",
                            ),
                        103,
                    )
                }
                4 -> applyWallpaper(p)
                5 ->
                    AlertDialog.Builder(this)
                        .setTitle("Delete this project?")
                        .setMessage("The original gallery image is preserved.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            runIO {
                                store.delete(p)
                                handler.post {
                                    if (project?.id == p.id) project = null
                                    home()
                                }
                            }
                        }
                        .show()
            }
        }
    }

    private fun previewScreen() {
        val p = project ?: return
        val parent =
            page("Live preview") {
                preview = false
                editor()
            }
        canvas(parent, false)
        val info = text("Tilt or shake your phone. Switch to manual to simulate.", 12f, muted)
        parent.addView(info)
        toolbar(
            parent,
            listOf(
                "${if(manual)"Use sensors" else "Manual mode"}" to
                    {
                        manual = !manual
                        if (manual) {
                            sensors?.stop()
                            renderer?.inputX = 0f
                            renderer?.inputY = 0f
                        } else startSensors()
                        previewScreen()
                    },
                "Pause / resume" to { renderer?.let { it.paused = !it.paused } },
                "Slow motion" to { renderer?.let { it.slow = !it.slow } },
                "Reset" to
                    {
                        renderer?.reset()
                        renderer?.inputX = 0f
                        renderer?.inputY = 0f
                    },
                "Full screen" to
                    {
                        info.visibility =
                            if (info.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        window.insetsController?.let {
                            if (info.visibility == View.GONE)
                                it.hide(WindowInsets.Type.systemBars())
                            else it.show(WindowInsets.Type.systemBars())
                        }
                    },
                "Diagnostics" to
                    {
                        renderer?.let { r ->
                            message(
                                "FPS ${"%.1f".format(r.fps)} • render ${"%.1f".format(r.renderMs)} ms\nInput ${"%.2f".format(r.inputX)}, ${"%.2f".format(r.inputY)} • ${p.points.size} points • ${p.regions.size} regions"
                            )
                        }
                    },
            ),
        )
        if (manual) {
            val controls = column()
            parent.addView(controls)
            var force = 1f
            slider(controls, "Shake intensity", 1f, 0f, 3f) { force = it }
            val joystick =
                object : View(this) {
                    val paint = Paint(3)
                    var xPos = .5f
                    var yPos = .5f

                    override fun onDraw(c: Canvas) {
                        paint.color = surface
                        c.drawRoundRect(
                            0f,
                            0f,
                            width.toFloat(),
                            height.toFloat(),
                            dp(20).toFloat(),
                            dp(20).toFloat(),
                            paint,
                        )
                        paint.color = muted
                        c.drawCircle(width / 2f, height / 2f, height * .32f, paint)
                        paint.color = ink
                        c.drawCircle(xPos * width, yPos * height, dp(13).toFloat(), paint)
                    }

                    override fun onTouchEvent(e: MotionEvent): Boolean {
                        parent.requestDisallowInterceptTouchEvent(true)
                        if (
                            e.action == MotionEvent.ACTION_UP ||
                                e.action == MotionEvent.ACTION_CANCEL
                        ) {
                            xPos = .5f
                            yPos = .5f
                        } else {
                            xPos = (e.x / width).coerceIn(0f, 1f)
                            yPos = (e.y / height).coerceIn(0f, 1f)
                        }
                        renderer?.inputX = (xPos - .5f) * 4
                        renderer?.inputY = (yPos - .5f) * 4
                        invalidate()
                        return true
                    }
                }
            joystick.contentDescription = "Virtual tilt joystick"
            controls.addView(joystick, LinearLayout.LayoutParams(-1, dp(100)))
            addButton(controls, "Test shake") {
                val r = renderer ?: return@addButton
                val start = SystemClock.uptimeMillis()
                val shake =
                    object : Runnable {
                        override fun run() {
                            if (renderer !== r) return
                            val t = (SystemClock.uptimeMillis() - start) / 1000f
                            if (t < 1.5f) {
                                r.inputX = sin(t * 34) * force
                                r.inputY = cos(t * 25) * force
                                handler.postDelayed(this, 16)
                            } else {
                                r.inputX = 0f
                                r.inputY = 0f
                            }
                        }
                    }
                handler.post(shake)
            }
        }
        addButton(parent, "Apply live wallpaper") { applyWallpaper(p) }
    }

    private fun applyWallpaper(p: Project) {
        val copy = Codec.decode(Codec.encode(p))
        runIO {
            store.save(copy)
            store.setActive(copy)
            prefs.edit().putString("active", copy.id).commit()
            handler.post {
                try {
                    startActivity(
                        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                            .putExtra(
                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(this, MotionWallpaperService::class.java),
                            )
                    )
                } catch (e: ActivityNotFoundException) {
                    message("This device has no live wallpaper picker")
                }
            }
        }
    }

    private fun settings() {
        val parent = page("Settings") { home() }
        val box = scroll(parent)
        addButton(box, "Performance: Battery saver / Balanced / High quality") {
            choices(
                "Performance mode",
                listOf(
                    "Battery saver • 20 FPS / 32 grid",
                    "Balanced • 30 FPS / 64 grid",
                    "High quality • 60 FPS / 96 grid",
                ),
            ) { i ->
                val fps = listOf(20, 30, 60)[i]
                val quality = listOf(32, 64, 96)[i]
                prefs.edit().putInt("fps", fps).putInt("quality", quality).apply()
                project?.let {
                    it.fps = fps
                    it.quality = quality
                    dirty = true
                }
                message("Performance settings saved")
            }
        }
        slider(box, "Default sensitivity %", prefs.getInt("sensitivity", 100).toFloat(), 0f, 200f) {
            prefs.edit().putInt("sensitivity", it.toInt()).apply()
        }
        slider(box, "Default frame rate", prefs.getInt("fps", 30).toFloat(), 15f, 60f, 45) {
            prefs.edit().putInt("fps", it.toInt()).apply()
        }
        addButton(box, "Help: creating movement") {
            AlertDialog.Builder(this)
                .setTitle("Motion workflow")
                .setMessage(
                    "1. Import an image or choose a starter.\n2. Add a region, then paint or lasso the movable area.\n3. Add points inside it; assign them to that region in Physics.\n4. Enable Fixed anchor at roots or fixed edges.\n5. Select each point and adjust its intensity and radius.\n6. Preview with sensors or the manual joystick. Save and Apply.\n\nPan is a separate tool. Pinch zooms; in Add Point mode it changes the selected radius.\n\nA flat image has no hidden background. Mesh warping stretches nearby pixels; use restrained motion to reduce artifacts. Lock screen support depends on your launcher/device. Android asks you to confirm wallpaper activation."
                )
                .setPositiveButton("OK", null)
                .show()
        }
        addButton(box, "Diagnostics") {
            val s = MotionSensors(this) { _, _ -> }
            message(
                "Android ${Build.VERSION.RELEASE}\n${Build.MANUFACTURER} ${Build.MODEL}\nAccelerometer: ${s.available}\nSaved projects: ${store.list().size}\nAll processing is offline"
            )
        }
        box.addView(text("Battery behavior", 19f))
        box.addView(
            text(
                "Wallpaper rendering and sensor sampling stop when Android reports the wallpaper hidden. Battery saver lowers frame rate and sensor sampling. No background scanning, network requests, analytics or unrestricted storage access.",
                14f,
                muted,
            )
        )
        addButton(box, "Android app settings") {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }
}
