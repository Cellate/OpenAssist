package com.openassist.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max

/**
 * The overlay shown when the user long-presses Home. Drag a box over the screenshot (pinch and
 * two-finger drag to zoom and move it), the selection is copied to the clipboard automatically,
 * then type, dictate or just send it to the AI.
 */
class OverlayActivity : Activity(), Speech.Callback {

    private val RC_MIC = 21
    private val RC_PICK = 22
    private val RC_SPEECH = 25

    private lateinit var prefs: Prefs
    private lateinit var speech: Speech

    private lateinit var root: FrameLayout
    private lateinit var selection: SelectionView
    private lateinit var hint: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var copyBtn: TextView
    private lateinit var wholeBtn: TextView
    private lateinit var modeBtn: TextView
    private lateinit var fitBtn: TextView
    private lateinit var panel: LinearLayout
    private lateinit var attachRow: LinearLayout
    private lateinit var thumb: ImageView
    private lateinit var attachLabel: TextView
    private lateinit var chipsRow: LinearLayout
    private lateinit var langBtn: TextView
    private lateinit var engineBtn: TextView
    private lateinit var input: OaEditText
    private lateinit var attachBtn: ImageButton
    private lateinit var pasteBtn: ImageButton
    private lateinit var micBtn: ImageButton
    private lateinit var sendBtn: ImageButton

    // In-Pill Audio Wave Visualizer (Google Gemini style)
    private lateinit var searchVisualizerContainer: LinearLayout
    private val searchWaveBars = ArrayList<View>()

    // Clear Privacy Listening Card
    private lateinit var listeningCard: LinearLayout
    private lateinit var listeningDot: View
    private lateinit var listeningTitle: TextView
    private lateinit var listeningEngineChip: TextView
    private lateinit var listeningPreviewText: TextView
    private val waveBars = ArrayList<View>()
    private lateinit var listeningStopBtn: TextView
    private lateinit var listeningCancelBtn: TextView
    private var dotAnimator: ObjectAnimator? = null
    private var micPulseAnimator: ObjectAnimator? = null

    private var screenshot: Bitmap? = null
    private var selectionFile: Staged? = null
    private val extras = ArrayList<Staged>()
    private var selGen = 0
    private var listening = false
    private var keepAlive = false
    private var basePromptBeforeSpeech = ""
    private var currentCutoutLeft = 0
    private var currentCutoutRight = 0
    private var currentBottomInset = 0

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        speech = Speech(this, prefs, this)

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                window.isNavigationBarContrastEnforced = false
            } catch (e: Throwable) {}
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        buildUi()
        setContentView(root)
        applyState()

        val app = applicationContext
        Thread { StagedFiles.cleanOld(app) }.start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        selGen++
        selectionFile = null
        extras.clear()
        input.setText("")
        applyState()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun allFiles(): List<Staged> = listOfNotNull(selectionFile) + extras

    @Suppress("DEPRECATION")
    private fun buildUi() {
        root = FrameLayout(this)

        selection = SelectionView(this).apply {
            onStart = { hint.animate().alpha(0f).setDuration(120).start() }
            onSelected = { r -> onRegion(r) }
        }
        root.addView(selection, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        hint = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Ui.TEXT)
            gravity = Gravity.CENTER
            background = Ui.pill(this@OverlayActivity, Ui.PANEL)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener {
                if (screenshot == null) openAssistantSettings()
            }
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(80)
                leftMargin = dp(24)
                rightMargin = dp(24)
            }
        )

        // Top-right: copy selection, whole screen, fit view, close.
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        copyBtn = Ui.chip(this, getString(R.string.btn_copy), false) { copyNow() }
        wholeBtn = Ui.chip(this, getString(R.string.btn_whole), false) { selection.selectAll() }
        modeBtn = Ui.chip(this, getString(R.string.mode_circle), false) { toggleMode() }
        fitBtn = Ui.chip(this, getString(R.string.btn_fit), false) { selection.resetView() }
        val close = Ui.iconButton(this, R.drawable.ic_close, getString(R.string.cd_close), bg = Ui.SURFACE_HI) { finish() }
        topBar.addView(copyBtn)
        topBar.addView(space(6))
        topBar.addView(wholeBtn)
        topBar.addView(space(6))
        topBar.addView(modeBtn)
        topBar.addView(space(6))
        topBar.addView(fitBtn)
        topBar.addView(space(6))
        topBar.addView(close)
        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        // Bottom panel: attachments, tools + AI chips, listening card, the ask bar.
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        attachRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = Ui.rounded(Ui.PANEL, dp(20).toFloat())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = Ui.rounded(Ui.SURFACE_HI, dp(10).toFloat())
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                }
            }
        }
        attachRow.addView(thumb, LinearLayout.LayoutParams(dp(48), dp(48)))
        attachLabel = TextView(this).apply {
            setTextColor(Ui.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 2
            setPadding(dp(12), 0, dp(12), 0)
        }
        attachRow.addView(attachLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        attachRow.addView(
            Ui.iconButton(this, R.drawable.ic_close, getString(R.string.cd_remove), sizeDp = 36) {
                selGen++
                selectionFile = null
                extras.clear()
                selection.clearSelection()
                refreshAttachments()
            }
        )
        panel.addView(attachRow, lp(bottom = 8))

        val toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pasteBtn = Ui.iconButton(this, R.drawable.ic_paste, getString(R.string.cd_paste), bg = Ui.SURFACE_HI, sizeDp = 36) { pasteClip() }
        toolsRow.addView(pasteBtn)
        toolsRow.addView(space(6))

        langBtn = Ui.chip(this, "", false) { cycleLang() }
        toolsRow.addView(langBtn)
        toolsRow.addView(space(6))

        engineBtn = Ui.chip(this, "", false) { toggleEngineMethod() }.apply {
            setOnLongClickListener {
                showEngineMenu()
                true
            }
        }
        toolsRow.addView(engineBtn)
        toolsRow.addView(space(8))

        val chipScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipScroll.addView(chipsRow)
        toolsRow.addView(chipScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(toolsRow, lp(bottom = 8))

        // --- Clear Privacy Listening Card ---
        listeningCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = Ui.rounded(Color.parseColor("#171A21"), dp(20).toFloat())
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        // Top Status Header: Red Live Dot + "LIVE MICROPHONE" + Engine Tag
        val listenHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listeningDot = View(this).apply {
            background = Ui.pill(this@OverlayActivity, Color.parseColor("#EF4444"))
        }
        listenHeader.addView(listeningDot, LinearLayout.LayoutParams(dp(10), dp(10)))
        listenHeader.addView(space(8))

        listeningTitle = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#EF4444"))
            typeface = Typeface.DEFAULT_BOLD
            text = "● LIVE MIC ACTIVE"
        }
        listenHeader.addView(listeningTitle)
        listenHeader.addView(space(8))

        listeningEngineChip = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Ui.ACCENT)
            background = Ui.pill(this@OverlayActivity, Color.parseColor("#263248"))
            setPadding(dp(8), dp(3), dp(8), dp(3))
            text = "Google Speech Services"
        }
        listenHeader.addView(listeningEngineChip)

        listenHeader.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        // Privacy indicator icon & text
        val privacyText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.parseColor("#94A3B8"))
            text = "🔒 Privacy: Audio is not saved"
        }
        listenHeader.addView(privacyText)
        listeningCard.addView(listenHeader)

        // Middle Row: Real-time Audio Waveform & Recognized Text
        val waveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }

        val waveContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.BOTTOM
        }
        waveBars.clear()
        for (i in 0 until 7) {
            val bar = View(this).apply {
                background = Ui.rounded(Color.parseColor("#38BDF8"), dp(2).toFloat())
            }
            waveBars.add(bar)
            waveContainer.addView(bar, LinearLayout.LayoutParams(dp(3), dp(10)).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            })
        }
        waveRow.addView(waveContainer)
        waveRow.addView(space(10))

        listeningPreviewText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            text = "Listening... Speak your prompt now"
        }
        waveRow.addView(listeningPreviewText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        listeningCard.addView(waveRow)

        // Bottom Action Controls: [ ✓ Done ] and [ Cancel ]
        val listenActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        listeningCancelBtn = TextView(this).apply {
            text = "Cancel"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { speech.stop() }
        }
        listenActions.addView(listeningCancelBtn)
        listenActions.addView(space(6))

        listeningStopBtn = TextView(this).apply {
            text = "✓ Done & Send"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0A1835"))
            background = Ui.pill(this@OverlayActivity, Ui.ACCENT)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener {
                if (prefs.isLocalModelMode && prefs.isPostProcessing) {
                    listeningPreviewText.text = "Processing audio with Whisper..."
                }
                speech.stop()
                if (!prefs.isLocalModelMode && input.text.isNotBlank()) send()
            }
        }
        listenActions.addView(listeningStopBtn)
        listeningCard.addView(listenActions)

        panel.addView(listeningCard, lp(bottom = 8))

        // Main Prompt Input Row - Spacious, clean, and professional Google Gemini Material theme
        val pillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.SURFACE, dp(30).toFloat())
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        // Attachment '+' button located directly to the left of the search text field, just like Gemini
        attachBtn = Ui.iconButton(
            this, R.drawable.ic_add, getString(R.string.cd_attach),
            tint = Ui.TEXT, bg = Ui.SURFACE_HI, sizeDp = 38
        ) { pickFiles() }
        pillRow.addView(attachBtn)
        pillRow.addView(space(6))

        // Center search container holding the text field and in-pill wave visualizer
        val searchCenter = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        input = OaEditText(this).apply {
            onFiles = { files, failed -> addFiles(files, failed) }
            background = null
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            // IME_FLAG_NO_EXTRACT_UI prevents fullscreen/centered extracted editor in landscape!
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setHorizontallyScrolling(false)
            maxLines = 4
            setPadding(dp(4), dp(8), dp(4), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = updateSendVisibility()
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    send()
                    true
                } else {
                    false
                }
            }
        }
        updateInputHint()
        searchCenter.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL
            )
        )

        // In-pill Gemini waveform visualizer
        searchVisualizerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(4), 0, dp(4), 0)
        }
        searchWaveBars.clear()
        val geminiColors = intArrayOf(Ui.GEMINI_BLUE, Ui.GEMINI_RED, Ui.GEMINI_YELLOW, Ui.GEMINI_GREEN, Ui.ACCENT)
        for (i in 0 until 5) {
            val bar = View(this).apply {
                background = Ui.rounded(geminiColors[i % geminiColors.size], dp(3).toFloat())
            }
            searchWaveBars.add(bar)
            searchVisualizerContainer.addView(bar, LinearLayout.LayoutParams(dp(4), dp(12)).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            })
        }
        searchCenter.addView(
            searchVisualizerContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply { rightMargin = dp(6) }
        )

        pillRow.addView(searchCenter)
        pillRow.addView(space(6))

        micBtn = Ui.iconButton(this, R.drawable.ic_mic, getString(R.string.cd_mic), bg = Ui.SURFACE_HI, sizeDp = 38) { toggleMic() }.apply {
            setOnLongClickListener {
                toggleEngineMethod()
                true
            }
        }
        sendBtn = Ui.iconButton(
            this, R.drawable.ic_send, getString(R.string.cd_send),
            tint = Ui.ON_ACCENT, bg = Ui.ACCENT, sizeDp = 38
        ) { send() }
        sendBtn.visibility = View.GONE

        pillRow.addView(micBtn)
        pillRow.addView(space(6))
        pillRow.addView(sendBtn)
        panel.addView(pillRow)

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            )
        )

        root.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    val ime = insets.getInsets(WindowInsets.Type.ime())
                    val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
                    top = maxOf(bars.top, cutout.top)
                    bottom = maxOf(bars.bottom, ime.bottom, cutout.bottom)
                    currentCutoutLeft = cutout.left
                    currentCutoutRight = cutout.right
                } else {
                    top = insets.systemWindowInsetTop
                    bottom = insets.systemWindowInsetBottom
                    if (Build.VERSION.SDK_INT >= 28) {
                        val dc = insets.displayCutout
                        currentCutoutLeft = dc?.safeInsetLeft ?: 0
                        currentCutoutRight = dc?.safeInsetRight ?: 0
                    }
                }
            } catch (e: Throwable) {
                top = dp(24)
                bottom = dp(24)
            }
            currentBottomInset = bottom
            if (::topBar.isInitialized) {
                topBar.setPadding(dp(8), top + dp(8), dp(8), 0)
            }
            if (::hint.isInitialized) {
                (hint.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.topMargin = top + dp(64)
                    hint.requestLayout()
                }
            }

            updatePanelLayout()
            insets
        }

        refreshChips()
        refreshLang()
        refreshEngineChip()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePanelLayout()
    }

    private fun updatePanelLayout() {
        if (!::panel.isInitialized) return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels

        val lp = (panel.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (isLandscape) {
            // In landscape: limit max size to 460dp (or 55% of screen width), and anchor to the right
            val maxAllowedWidth = minOf(dp(460), (screenWidth * 0.55f).toInt())
            lp.width = maxAllowedWidth
            lp.gravity = Gravity.BOTTOM or Gravity.END

            // Safe margin from right edge to stay comfortably away from the camera cutout and phone edge
            val safeEndMargin = maxOf(dp(24), currentCutoutRight + dp(16))
            val safeBottomMargin = currentBottomInset + dp(12)
            lp.setMargins(dp(16), 0, safeEndMargin, safeBottomMargin)
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

            val safeStartMargin = maxOf(dp(12), currentCutoutLeft + dp(8))
            val safeEndMargin = maxOf(dp(12), currentCutoutRight + dp(8))
            val safeBottomMargin = currentBottomInset + dp(12)
            lp.setMargins(safeStartMargin, 0, safeEndMargin, safeBottomMargin)
        }
        panel.layoutParams = lp
        panel.requestLayout()
    }

    private fun lp(bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(bottom) }

    private fun space(widthDp: Int): View =
        View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(widthDp), 1) }

    private fun applyState() {
        screenshot = ScreenshotHolder.bitmap
        selection.setBitmap(screenshot)
        hint.alpha = 1f
        hint.visibility = View.VISIBLE
        val has = screenshot != null
        hint.text = getString(if (has) R.string.hint_circle else R.string.hint_no_screenshot)
        val vis = if (has) View.VISIBLE else View.GONE
        copyBtn.visibility = vis
        wholeBtn.visibility = vis
        modeBtn.visibility = vis
        fitBtn.visibility = vis
        refreshMode()
        refreshAttachments()
        refreshEngineChip()
        updateInputHint()
        if (prefs.autoStartMic && !listening) {
            root.postDelayed({
                if (!isFinishing && !isDestroyed && !listening) {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        speech.start()
                    }
                }
            }, 250)
        }
    }

    private fun refreshMode() {
        modeBtn.text = getString(if (selection.mode == SelectionView.Mode.CIRCLE) R.string.mode_circle else R.string.mode_box)
    }

    private fun toggleMode() {
        selection.mode = if (selection.mode == SelectionView.Mode.CIRCLE) SelectionView.Mode.BOX else SelectionView.Mode.CIRCLE
        selection.clearSelection()
        refreshMode()
    }

    private fun updateInputHint() {
        if (!::input.isInitialized) return
        val currentService = AiServices.current(prefs)
        input.hint = getString(R.string.ask_hint, currentService.name)
    }

    private fun refreshChips() {
        chipsRow.removeAllViews()
        val current = AiServices.current(prefs).id
        for (s in AiServices.all(prefs)) {
            val chip = Ui.chip(this, s.name, s.id == current) {
                prefs.serviceId = s.id
                refreshChips()
                updateInputHint()
            }
            if (s.isCustom) {
                chip.setOnLongClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Custom AI: ${s.name}")
                        .setMessage("URL: ${s.url}\n\nWould you like to remove this custom web assistant?")
                        .setPositiveButton("Remove") { _, _ ->
                            prefs.removeCustomService(s.id)
                            refreshChips()
                            updateInputHint()
                            Toast.makeText(this, "Removed ${s.name}", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
            chipsRow.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8) })
        }

        val addChip = Ui.chip(this, "+ Add AI", false) {
            showAddCustomAiDialog()
        }
        chipsRow.addView(addChip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) })
    }

    private fun showAddCustomAiDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }
        val nameInput = android.widget.EditText(this).apply {
            hint = "AI Name (e.g. Open WebUI, Ollama, Grok 3)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
        }
        val urlInput = android.widget.EditText(this).apply {
            hint = "Web URL (e.g. https://... or http://192.168.1.x:3000)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
        }
        layout.addView(nameInput)
        val sp = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) }
        layout.addView(sp)
        layout.addView(urlInput)

        AlertDialog.Builder(this)
            .setTitle("Add Custom AI Web Assistant")
            .setView(layout)
            .setPositiveButton("Add & Select") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (url.isNotBlank()) {
                    val newService = prefs.addCustomService(name, url)
                    refreshChips()
                    updateInputHint()
                    Toast.makeText(this, "Added and selected ${newService.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshLang() {
        langBtn.text = prefs.voiceLang.chip
    }

    private fun refreshEngineChip() {
        val label = prefs.getEngineChipLabel()
        engineBtn.text = label
        val isFuto = prefs.isFutoActive()
        engineBtn.background = Ui.pill(this, if (isFuto) Ui.ACCENT else Ui.SURFACE_HI)
        engineBtn.setTextColor(if (isFuto) Ui.ON_ACCENT else Ui.TEXT)
    }

    private fun toggleEngineMethod() {
        val newEngine = prefs.toggleEngine()
        refreshEngineChip()
        updateInputHint()
        Toast.makeText(this, "Active Voice Input: ${prefs.getEngineChipLabel()}", Toast.LENGTH_SHORT).show()
    }

    private fun showEngineMenu() {
        val store = ModelStore(this)
        val models = store.getModels()
        val options = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        // 1. System Default
        options.add("System Default Recognizer")
        actions.add {
            prefs.recognizer = Prefs.ENGINE_DEFAULT
        }

        // 2. Local Model Mode
        val currentModel = prefs.getActiveLocalModel()
        val localLabel = if (currentModel != null) {
            "Local Model Mode (Active: ${currentModel.name})"
        } else {
            "Local Model Mode (Offline Whisper)"
        }
        options.add(localLabel)
        actions.add {
            prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
            if (models.size > 1) {
                // If multiple models loaded, allow quick switch
                showModelPicker(store)
            }
        }

        // 3. Cycle local models if in local mode and multiple loaded
        if (models.size > 1) {
            options.add("⟳ Cycle to Next Model (${models.size} loaded)")
            actions.add {
                val next = store.cycleModel()
                prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
                Toast.makeText(this, "Switched model to: ${next?.name}", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Google Speech
        options.add("Google Speech Recognition (Recommended on Oppo)")
        actions.add {
            prefs.recognizer = Prefs.ENGINE_GOOGLE_TTS
        }

        // 5. FUTO Voice Input
        options.add("FUTO Voice Input / Keyboard (Offline Whisper)")
        actions.add {
            prefs.recognizer = Prefs.ENGINE_FUTO_VOICE_INPUT
        }

        // 6. Whisper Processing Mode Switcher
        val modeLabel = if (prefs.isPostProcessing) "Post-Processing (Default)" else "Real-Time Streaming"
        options.add("Whisper Mode: $modeLabel (Tap to Switch)")
        actions.add {
            prefs.processingMode = if (prefs.isPostProcessing) Prefs.MODE_REAL_TIME else Prefs.MODE_POST_PROCESSING
            val updated = if (prefs.isPostProcessing) "Post-Processing (Default)" else "Real-Time Streaming"
            Toast.makeText(this, "Set Whisper to $updated", Toast.LENGTH_SHORT).show()
        }

        // 7. Settings
        options.add("⚙ Open Voice & Model Settings...")
        actions.add {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        AlertDialog.Builder(this)
            .setTitle("Speech Recognition Engine")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
                refreshEngineChip()
                updateInputHint()
            }
            .show()
    }

    private fun showModelPicker(store: ModelStore) {
        val models = store.getModels()
        if (models.isEmpty()) return
        val names = models.map { "${it.name} (${it.sizeMb})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Local Whisper Model")
            .setItems(names) { _, which ->
                val selected = models[which]
                store.setDefaultModelId(selected.id)
                store.setModelIdForLang(prefs.voiceLang, selected.id)
                refreshEngineChip()
                updateInputHint()
                Toast.makeText(this, "Selected model: ${selected.name}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun cycleLang() {
        prefs.voiceLang = prefs.voiceLang.next()
        refreshLang()
        Toast.makeText(this, prefs.voiceLang.label, Toast.LENGTH_SHORT).show()
    }

    private fun updateSendVisibility() {
        if (!::input.isInitialized || !::sendBtn.isInitialized) return
        val has = input.text.isNotBlank() || allFiles().isNotEmpty()
        sendBtn.visibility = if (has) View.VISIBLE else View.GONE
    }

    private fun onRegion(r: RectF) {
        val src = screenshot ?: return
        val crop = selection.cropFrom(src, r) ?: return
        val gen = ++selGen
        val app = applicationContext
        Thread {
            val s = StagedFiles.stageBitmap(app, crop)
            runOnUiThread {
                if (isDestroyed || gen != selGen) return@runOnUiThread
                if (s == null) {
                    Toast.makeText(this, R.string.toast_copy_failed, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                selectionFile = s
                StagedFiles.lastSelection = s
                val ok = ClipboardHelper.copy(this, s)
                Toast.makeText(
                    this, if (ok) R.string.toast_copied else R.string.toast_copy_failed, Toast.LENGTH_SHORT
                ).show()
                refreshAttachments()
                if (!prefs.composeAfterCircle && input.text.isNullOrBlank()) {
                    launchWeb(WebActivity.MODE_IMAGE, null, false, listOf(s))
                }
            }
        }.start()
    }

    private fun copyNow() {
        val s = selectionFile
        if (s == null) {
            Toast.makeText(this, R.string.toast_nothing_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val ok = ClipboardHelper.copy(this, s)
        Toast.makeText(
            this, if (ok) R.string.toast_copied else R.string.toast_copy_failed, Toast.LENGTH_SHORT
        ).show()
    }

    private fun refreshAttachments() {
        val files = allFiles()
        if (files.isEmpty()) {
            attachRow.visibility = View.GONE
        } else {
            attachRow.visibility = View.VISIBLE
            hint.visibility = View.GONE
            val first = files.first()
            if (first.isImage) thumb.setImageBitmap(thumbnail(first)) else thumb.setImageDrawable(null)
            attachLabel.text = when {
                files.size > 1 -> getString(R.string.attached_n, files.size)
                first === selectionFile -> getString(R.string.selection_attached)
                else -> first.name
            }
        }
        updateSendVisibility()
    }

    private fun thumbnail(s: Staged): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(s.file.absolutePath, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(s.file.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }

    private fun addFiles(files: List<Staged>, failed: Int) {
        if (failed > 0) Toast.makeText(this, getString(R.string.toast_stage_failed, failed), Toast.LENGTH_SHORT).show()
        if (files.isEmpty()) return
        extras.addAll(files)
        refreshAttachments()
    }

    private fun pickFiles() {
        keepAlive = true
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(i, RC_PICK)
        } catch (e: Exception) {
            keepAlive = false
            Toast.makeText(this, R.string.toast_stage_failed_generic, Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteClip() {
        val own = selectionFile?.uri
        val uris = ClipboardHelper.clipboardFileUris(this).filter { it != own }
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_clip, Toast.LENGTH_SHORT).show()
            return
        }
        StagedFiles.stageAsync(this, uris) { files, failed -> addFiles(files, failed) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        keepAlive = false
        if (requestCode == RC_SPEECH) {
            onStateChanged(false, speech.currentEngineName)
            if (resultCode == RESULT_OK && data != null) {
                val matches = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                    ?: data.getStringArrayListExtra("android.speech.extra.RESULTS")
                val raw = matches?.firstOrNull()?.trim()
                    ?: data.getStringExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.trim()
                    ?: data.getStringExtra("result")?.trim()
                    ?: data.getStringExtra("text")?.trim()
                    ?: data.getStringExtra("query")?.trim()
                if (!raw.isNullOrBlank()) {
                    val out = if (prefs.voiceLang == VoiceLang.HINGLISH) Translit.toHinglish(raw) else raw
                    onFinal(out)
                }
            }
            return
        }
        if (requestCode == RC_PICK && resultCode == RESULT_OK && data != null) {
            val uris = ArrayList<Uri>()
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
            } else {
                data.data?.let { uris.add(it) }
            }
            if (uris.isNotEmpty()) {
                StagedFiles.stageAsync(this, uris) { files, failed -> addFiles(files, failed) }
            }
        }
    }

    private fun send() {
        val text = input.text.toString().trim()
        val files = allFiles()
        if (text.isEmpty() && files.isEmpty()) return
        val mode = if (files.isNotEmpty()) WebActivity.MODE_IMAGE else WebActivity.MODE_TEXT
        launchWeb(mode, text.ifEmpty { null }, prefs.autoSubmit && text.isNotEmpty(), files)
    }

    private fun launchWeb(mode: String, text: String?, submit: Boolean, files: List<Staged>) {
        speech.destroy()
        try {
            val intent = Intent(this, WebActivity::class.java).apply {
                action = WebActivity.ACTION_ASSIST
                putExtra(WebActivity.EXTRA_MODE, mode)
                if (text != null) putExtra(WebActivity.EXTRA_TEXT, text)
                putExtra(WebActivity.EXTRA_SUBMIT, submit)
                if (files.isNotEmpty()) {
                    putExtra(WebActivity.EXTRA_FILES, files.map { it.file.absolutePath }.toTypedArray())
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open web assistant: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun toggleMic() {
        if (listening) {
            if (prefs.isLocalModelMode && prefs.isPostProcessing) {
                showListeningCard(speech.currentEngineName, "Transcribing with Whisper...")
            }
            speech.stop()
            return
        }
        basePromptBeforeSpeech = input.text.toString().trim()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            keepAlive = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_MIC)
            return
        }
        speech.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        keepAlive = false
        if (requestCode == RC_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                speech.start()
            } else {
                Toast.makeText(this, R.string.err_mic_permission, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStarting(engineName: String) {
        listening = true
        val statusText = if (prefs.isLocalModelMode && prefs.isPostProcessing) {
            "Recording audio... Tap mic when done"
        } else if (prefs.isLocalModelMode) {
            "Live microphone active"
        } else {
            "Connecting microphone..."
        }
        showListeningCard(engineName, statusText)
    }

    override fun onReadyForSpeech(engineName: String) {
        listening = true
        val statusText = if (prefs.isLocalModelMode && prefs.isPostProcessing) {
            "Recording audio... Speak now! Tap mic to transcribe"
        } else {
            "Listening to your voice... Speak now"
        }
        showListeningCard(engineName, statusText)
    }

    private fun showListeningCard(engineName: String, statusText: String) {
        if (!::listeningCard.isInitialized) return
        listeningCard.visibility = View.VISIBLE
        listeningTitle.text = "● LIVE MIC ACTIVE"
        listeningEngineChip.text = engineName
        listeningPreviewText.text = statusText

        // Animate mic button in bottom pill with active red / coral glow
        if (::micBtn.isInitialized) {
            micBtn.background = Ui.pill(this, Ui.GEMINI_RED)
            micBtn.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
        if (::searchVisualizerContainer.isInitialized) {
            searchVisualizerContainer.visibility = View.VISIBLE
        }

        startDotPulse()
    }

    override fun onPartial(text: String) {
        if (!::input.isInitialized) return
        val combined = if (basePromptBeforeSpeech.isNotEmpty()) "$basePromptBeforeSpeech $text" else text
        input.setText(combined)
        input.setSelection(input.text.length)
        updateSendVisibility()
        if (::listeningPreviewText.isInitialized) {
            listeningPreviewText.text = "Heard: \"$text\""
        }
    }

    override fun onFinal(text: String) {
        if (!::input.isInitialized) return
        val combined = if (basePromptBeforeSpeech.isNotEmpty()) "$basePromptBeforeSpeech $text" else text
        if (::listeningPreviewText.isInitialized) {
            listeningPreviewText.text = "Received: \"$text\""
        }
        input.setText(combined)
        input.setSelection(input.text.length)
        basePromptBeforeSpeech = combined
        updateSendVisibility()
        onStateChanged(false, speech.currentEngineName)
        if (combined.isNotBlank() && prefs.autoSubmit) send()
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (!listening) return
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1f)
        for (i in waveBars.indices) {
            val variance = (1f + kotlin.math.sin(i.toDouble() * 1.3 + System.currentTimeMillis() / 60.0) * 0.5).toFloat()
            val heightDp = (6 + normalized * 20 * variance).toInt().coerceIn(4, 28)
            val lp = waveBars[i].layoutParams
            lp.height = dp(heightDp)
            waveBars[i].layoutParams = lp
        }
        // Animate visualizer inside the search input pill
        for (i in searchWaveBars.indices) {
            val variance = (1f + kotlin.math.sin(i.toDouble() * 1.5 + System.currentTimeMillis() / 50.0) * 0.6).toFloat()
            val heightDp = (6 + normalized * 18 * variance).toInt().coerceIn(4, 22)
            val lp = searchWaveBars[i].layoutParams
            lp.height = dp(heightDp)
            searchWaveBars[i].layoutParams = lp
        }
    }

    override fun onLaunchSpeechIntent(intent: Intent) {
        try {
            keepAlive = true
            showListeningCard("FUTO Voice Input", "Opening FUTO Voice Input...")
            startActivityForResult(intent, RC_SPEECH)
        } catch (e: Exception) {
            keepAlive = false
            onStateChanged(false, speech.currentEngineName)
            onFutoNotFound()
        }
    }

    override fun onFutoNotFound() {
        onStateChanged(false, speech.currentEngineName)
        AlertDialog.Builder(this)
            .setTitle("FUTO Voice Input Not Installed")
            .setMessage("FUTO Voice Input (offline Whisper) is not installed on this device yet.\n\nWould you like to install FUTO Voice Input, or use Google Speech Services?")
            .setPositiveButton("Get FUTO (Free APK)") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://voiceinput.futo.org/")))
                } catch (e: Exception) {
                    Toast.makeText(this, "Visit https://voiceinput.futo.org/", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Use Google Speech") { _, _ ->
                prefs.recognizer = Prefs.ENGINE_GOOGLE_TTS
                refreshEngineChip()
                updateInputHint()
                speech.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onError(message: String) {
        onStateChanged(false, speech.currentEngineName)
        if (::listeningCard.isInitialized) {
            listeningCard.visibility = View.VISIBLE
            if (::listeningTitle.isInitialized) listeningTitle.text = "Microphone Idle"
            if (::listeningPreviewText.isInitialized) listeningPreviewText.text = message
            listeningCard.postDelayed({
                if (!listening && ::listeningCard.isInitialized) listeningCard.visibility = View.GONE
            }, 2200)
        }
    }

    override fun onStateChanged(listening: Boolean, engineName: String) {
        this.listening = listening
        if (::micBtn.isInitialized) {
            micBtn.background = Ui.pill(this, if (listening) Ui.GEMINI_RED else Ui.SURFACE_HI)
            micBtn.imageTintList = ColorStateList.valueOf(
                if (listening) Color.WHITE else Ui.TEXT
            )
        }
        if (::searchVisualizerContainer.isInitialized) {
            searchVisualizerContainer.visibility = if (listening) View.VISIBLE else View.GONE
        }
        if (listening) {
            showListeningCard(engineName, "Listening... Speak now")
            if (::input.isInitialized) {
                input.hint = "Listening via $engineName..."
            }
        } else {
            stopDotPulse()
            resetWaveBars()
            updateInputHint()
            if (::listeningCard.isInitialized) {
                listeningCard.postDelayed({
                    if (!this.listening && ::listeningCard.isInitialized) listeningCard.visibility = View.GONE
                }, 600)
            }
        }
    }

    private fun startDotPulse() {
        dotAnimator?.cancel()
        dotAnimator = ObjectAnimator.ofFloat(listeningDot, "alpha", 1f, 0.2f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopDotPulse() {
        dotAnimator?.cancel()
        listeningDot.alpha = 1f
    }

    private fun resetWaveBars() {
        for (b in waveBars) {
            val lp = b.layoutParams
            lp.height = dp(6)
            b.layoutParams = lp
        }
        for (b in searchWaveBars) {
            val lp = b.layoutParams
            lp.height = dp(6)
            b.layoutParams = lp
        }
    }

    private fun openAssistantSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isChangingConfigurations && !keepAlive) finish()
    }

    override fun onDestroy() {
        stopDotPulse()
        speech.destroy()
        ScreenshotHolder.bitmap = null
        super.onDestroy()
    }
}
