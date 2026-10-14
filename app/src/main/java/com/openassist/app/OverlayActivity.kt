package com.openassist.app

import android.Manifest
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
import android.widget.Button
import android.widget.Toast
import kotlin.math.max

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
    private lateinit var attachScroll: HorizontalScrollView
    private lateinit var attachRow: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private lateinit var langBtn: TextView
    private lateinit var engineBtn: TextView
    private lateinit var input: OaEditText
    private lateinit var attachBtn: ImageButton
    private lateinit var pasteBtn: ImageButton
    private lateinit var micBtn: ImageButton
    private lateinit var sendBtn: ImageButton
    private lateinit var theme: Ui.Theme

    // In-Pill Audio Wave Visualizer
    private lateinit var searchVisualizerContainer: LinearLayout
    private val searchWaveBars = ArrayList<View>()

    // Mascot Companion & Petting
    private lateinit var mascotTopBtn: View
    private lateinit var mascotTopIcon: ImageView
    private lateinit var mascotTopText: TextView
    private lateinit var mascotSearchPillBtn: ImageView
    private lateinit var mascotBubble: TextView
    private var petIndex = 0
    private val mascotPetPhrases = listOf(
        "*purrs happily* You're doing great today! ",
        "I love learning new things with you! ",
        "*happy chirp* Always ready by your side! ",
        "You're my favorite human! ",
        "Ready whenever you are! What shall we search? ",
        "All systems green! Ask me anything! "
    )
    private val mascotGeneralTips = listOf(
        "  Circle any equation, image, or text to search!",
        "  Double tap any text on screen to copy it instantly!",
        "  Drag two fingers to zoom in and inspect fine details!",
        "  Tap the mic to dictate your questions hands-free!",
        "  Switch your AI engine anytime in Settings!"
    )

    private var screenshot: Bitmap? = null
    private var selectionFile: Staged? = null
    private val extras = ArrayList<Staged>()
    private var selGen = 0
    private var listening = false
    private var keepAlive = false
    private var basePromptBeforeSpeech = ""
    private var insetLeft = 0
    private var insetRight = 0
    private var insetTop = 0
    private var insetBottom = 0

    private fun getMascotInfo(style: String): Pair<Int, String> {
        val m = MascotHelper.getMascot(style)
        return Pair(m.drawableRes, m.name)
    }

    private fun refreshMascotViews() {
        if (!::mascotTopIcon.isInitialized || !::mascotTopText.isInitialized || !::mascotSearchPillBtn.isInitialized) return
        val m = MascotHelper.getMascot(prefs.mascotStyle)
        mascotTopIcon.setImageResource(m.drawableRes)
        mascotTopText.text = m.name
        mascotSearchPillBtn.setImageResource(m.drawableRes)
        mascotTopBtn.visibility = if (prefs.showMascotTopChips) View.VISIBLE else View.GONE
        mascotSearchPillBtn.visibility = if (prefs.showMascotSearchPill) View.VISIBLE else View.GONE
        mascotBubble.visibility = if (prefs.showMascotSpeechBubble) View.VISIBLE else View.GONE
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            prefs = Prefs(this)
            theme = Ui.theme(this)
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
            Ui.applySystemBars(this, theme)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            buildUi()
            setContentView(root)
            applyState()
            val app = applicationContext
            Thread { StagedFiles.cleanOld(app) }.start()
        } catch (t: Throwable) {
            android.util.Log.e("OverlayActivity", "Fatal in OverlayActivity onCreate", t)
            Ui.showMascotToast(this, "Could not open overlay", prefs.mascotStyle, type = ToastType.GENERAL)
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::theme.isInitialized && theme.isDark != prefs.isDarkMode) {
            recreate()
            return
        }
        selGen++
        selectionFile = null
        extras.clear()
        input.setText("")
        applyState()
        refreshMascotViews()
    }

    override fun onResume() {
        super.onResume()
        if (::theme.isInitialized && theme.isDark != prefs.isDarkMode) {
            recreate()
            return
        }
        refreshMascotViews()
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
            setTextColor(theme.TEXT)
            gravity = Gravity.CENTER
            background = Ui.pill(this@OverlayActivity, theme.PANEL)
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

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        copyBtn = Ui.chip(this, getString(R.string.btn_copy), false, theme) { copyNow() }
        wholeBtn = Ui.chip(this, getString(R.string.btn_whole), false, theme) { selection.selectAll() }
        modeBtn = Ui.chip(this, getString(R.string.mode_circle), false, theme) { toggleMode() }
        fitBtn = Ui.chip(this, getString(R.string.btn_fit), false, theme) { selection.resetView() }
        val close = Ui.iconButton(this, R.drawable.ic_close, getString(R.string.cd_close), theme = theme, bg = theme.SURFACE_HI) { finish() }

        mascotTopBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.pill(this@OverlayActivity, Color.parseColor("#312E81"))
            setPadding(dp(8), dp(4), dp(10), dp(4))
            visibility = if (prefs.showMascotTopChips) View.VISIBLE else View.GONE
            val (mascotDrawable, mascotName) = getMascotInfo(prefs.mascotStyle)
            mascotTopIcon = ImageView(this@OverlayActivity).apply {
                setImageResource(mascotDrawable)
            }
            addView(mascotTopIcon, LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(space(4))
            mascotTopText = TextView(this@OverlayActivity).apply {
                text = mascotName
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(mascotTopText)
            setOnClickListener { petMascot(this) }
        }

        topBar.addView(mascotTopBtn)
        topBar.addView(space(6))
        topBar.addView(copyBtn)
        topBar.addView(space(6))
        topBar.addView(wholeBtn)
        topBar.addView(space(6))
        topBar.addView(modeBtn)
        topBar.addView(space(6))
        topBar.addView(fitBtn)
        topBar.addView(space(6))
        topBar.addView(close)

        mascotBubble = TextView(this).apply {
            setTextColor(if (theme.isDark) Color.WHITE else Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            val bubbleBg = Ui.rounded(
                if (theme.isDark) Color.parseColor("#1E1B4B") else Color.parseColor("#EEF2FF"),
                dp(18).toFloat()
            )
            background = bubbleBg
            setPadding(dp(14), dp(8), dp(14), dp(8))
            elevation = dp(6).toFloat()
            visibility = if (prefs.showMascotSpeechBubble) View.VISIBLE else View.GONE
            val mTextLang = prefs.translationStore.getEffectiveLanguage("mascot")
            text = prefs.mascotDialogueStore.getRandomPhrase(prefs.mascotStyle, mTextLang)
            setOnClickListener { petMascot(this) }
        }
        root.addView(
            mascotBubble,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                topMargin = dp(96)
                leftMargin = dp(16)
                rightMargin = dp(16)
            }
        )

        val topBarScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(topBar)
        }
        root.addView(
            topBarScroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(40)
                rightMargin = dp(16)
            }
        )

        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        attachScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }
        attachRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = dp(2)
            setPadding(pad, pad, pad, pad)
        }
        attachScroll.addView(attachRow)
        panel.addView(attachScroll, lp(bottom = 8))

        val toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pasteBtn = Ui.iconButton(this, R.drawable.ic_paste, getString(R.string.cd_paste), theme = theme, bg = theme.SURFACE_HI, sizeDp = 36) { pasteClip() }
        toolsRow.addView(pasteBtn)
        toolsRow.addView(space(6))
        langBtn = Ui.chip(this, "", false, theme) { cycleLang() }
        toolsRow.addView(langBtn)
        toolsRow.addView(space(6))
        engineBtn = Ui.chip(this, "", false, theme) { toggleEngineMethod() }.apply {
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

        val pillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(theme.SURFACE, dp(30).toFloat())
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        attachBtn = Ui.iconButton(
            this, R.drawable.ic_add, getString(R.string.cd_attach),
            theme = theme, tint = theme.TEXT, bg = theme.SURFACE_HI, sizeDp = 38
        ) { pickFiles() }
        pillRow.addView(attachBtn)
        pillRow.addView(space(6))

        val (mascotPillDrawable, _) = getMascotInfo(prefs.mascotStyle)
        mascotSearchPillBtn = ImageView(this).apply {
            setImageResource(mascotPillDrawable)
            background = Ui.pill(this@OverlayActivity, Color.parseColor("#1E1B4B"))
            val pad = dp(5)
            setPadding(pad, pad, pad, pad)
            visibility = if (prefs.showMascotSearchPill) View.VISIBLE else View.GONE
            setOnClickListener { petMascot(this) }
        }
        pillRow.addView(mascotSearchPillBtn, LinearLayout.LayoutParams(dp(32), dp(32)))
        pillRow.addView(space(4))

        val searchCenter = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        input = OaEditText(this).apply {
            onFiles = { files, failed -> addFiles(files, failed) }
            background = null
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
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

        searchVisualizerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(4), 0, dp(4), 0)
        }
        searchWaveBars.clear()
        for (i in 0 until 5) {
            val bar = View(this).apply {
                background = Ui.rounded(theme.ACCENT, dp(3).toFloat())
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

        micBtn = Ui.iconButton(this, R.drawable.ic_mic, getString(R.string.cd_mic), theme = theme, bg = theme.SURFACE_HI, sizeDp = 38) { toggleMic() }.apply {
            setOnLongClickListener {
                toggleEngineMethod()
                true
            }
        }

        sendBtn = Ui.iconButton(
            this, R.drawable.ic_send, getString(R.string.cd_send),
            theme = theme, tint = theme.ON_ACCENT, bg = theme.ACCENT, sizeDp = 38
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
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    val ime = insets.getInsets(WindowInsets.Type.ime())
                    val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
                    insetTop = maxOf(bars.top, cutout.top)
                    insetBottom = maxOf(bars.bottom, ime.bottom, cutout.bottom)
                    insetLeft = maxOf(bars.left, cutout.left)
                    insetRight = maxOf(bars.right, cutout.right)
                } else {
                    @Suppress("DEPRECATION")
                    insetTop = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    insetBottom = insets.systemWindowInsetBottom
                    @Suppress("DEPRECATION")
                    insetLeft = insets.systemWindowInsetLeft
                    @Suppress("DEPRECATION")
                    insetRight = insets.systemWindowInsetRight
                    if (Build.VERSION.SDK_INT >= 28) {
                        val dc = insets.displayCutout
                        if (dc != null) {
                            insetTop = maxOf(insetTop, dc.safeInsetTop)
                            insetBottom = maxOf(insetBottom, dc.safeInsetBottom)
                            insetLeft = maxOf(insetLeft, dc.safeInsetLeft)
                            insetRight = maxOf(insetRight, dc.safeInsetRight)
                        }
                    }
                }
            } catch (e: Throwable) {
                insetTop = dp(24)
                insetBottom = dp(24)
                insetLeft = 0
                insetRight = 0
            }
            updateTopBarLayout()
            updateMascotBubbleLayout()
            updateHintLayout()
            updatePanelLayout()
            insets
        }

        refreshChips()
        refreshLang()
        refreshEngineChip()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTopBarLayout()
        updateMascotBubbleLayout()
        updateHintLayout()
        updatePanelLayout()
    }

    private fun updateMascotBubbleLayout() {
        if (!::mascotBubble.isInitialized) return
        val lp = (mascotBubble.layoutParams as? FrameLayout.LayoutParams) ?: return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        lp.gravity = Gravity.TOP or Gravity.START
        lp.topMargin = insetTop + dp(if (isLandscape) 56 else 62)
        lp.leftMargin = maxOf(dp(16), insetLeft + dp(14))
        lp.rightMargin = maxOf(dp(16), insetRight + dp(14))
        mascotBubble.layoutParams = lp
        mascotBubble.requestLayout()
    }

    private fun updateTopBarLayout() {
        if (!::topBar.isInitialized) return
        val topContainer = (topBar.parent as? View) ?: topBar
        val lp = (topContainer.layoutParams as? FrameLayout.LayoutParams) ?: return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            lp.gravity = Gravity.TOP or Gravity.START
            lp.topMargin = insetTop + dp(8)
            lp.leftMargin = maxOf(dp(20), insetLeft + dp(16))
            lp.rightMargin = maxOf(dp(88), insetRight + dp(48))
        } else {
            lp.gravity = Gravity.TOP or Gravity.END
            lp.topMargin = insetTop + dp(8)
            lp.rightMargin = maxOf(dp(18), insetRight + dp(16))
            lp.leftMargin = maxOf(dp(18), insetLeft + dp(16))
        }
        topContainer.layoutParams = lp
        topContainer.requestLayout()
    }

    private fun updateHintLayout() {
        if (!::hint.isInitialized) return
        val lp = (hint.layoutParams as? FrameLayout.LayoutParams) ?: return
        val bubbleOffset = if (prefs.showMascotSpeechBubble) dp(54) else 0
        lp.topMargin = insetTop + dp(64) + bubbleOffset
        lp.leftMargin = maxOf(dp(24), insetLeft + dp(16))
        lp.rightMargin = maxOf(dp(24), insetRight + dp(16))
        hint.layoutParams = lp
        hint.requestLayout()
    }

    private fun updatePanelLayout() {
        if (!::panel.isInitialized) return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val lp = (panel.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (isLandscape) {
            val maxAllowedWidth = minOf(dp(480), (screenWidth * 0.58f).toInt())
            lp.width = maxAllowedWidth
            lp.gravity = Gravity.BOTTOM or Gravity.START
            val safeLeftMargin = maxOf(dp(20), insetLeft + dp(16))
            val safeRightMargin = maxOf(dp(88), insetRight + dp(48))
            val safeBottomMargin = maxOf(dp(16), insetBottom + dp(12))
            lp.setMargins(safeLeftMargin, 0, safeRightMargin, safeBottomMargin)
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            val safeStartMargin = maxOf(dp(14), insetLeft + dp(12))
            val safeEndMargin = maxOf(dp(14), insetRight + dp(12))
            val safeBottomMargin = maxOf(dp(16), insetBottom + dp(12))
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
            val label = if (s.openInApp) "${s.name} ↗" else s.name
            val chip = Ui.chip(this, label, s.id == current, theme) {
                prefs.serviceId = s.id
                refreshChips()
                updateInputHint()
            }
            chip.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle(s.name)
                    .setMessage("Category: ${s.category.displayName}\nURL: ${s.url}\nOpen in App: ${if (s.openInApp) "Yes" else "No"}" + if (s.isCustom) "\n\nWould you like to remove this chip?" else "")
                    .apply {
                        if (s.isCustom) {
                            setPositiveButton("Remove") { _, _ ->
                                prefs.removeCustomService(s.id)
                                refreshChips()
                                updateInputHint()
                                Ui.showMascotToast(this@OverlayActivity, "Removed ${s.name}", prefs.mascotStyle, type = ToastType.ASSISTANT)
                            }
                        }
                        setNeutralButton("Manage Chips") { _, _ ->
                            val intent = Intent(this@OverlayActivity, SettingsActivity::class.java).apply {
                                putExtra("EXTRA_OPEN_CHIP_MANAGER", true)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            finish()
                        }
                        setNegativeButton("Close", null)
                    }
                    .show()
                true
            }
            chipsRow.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8) })
        }
        val addChip = Ui.chip(this, "+ Add Link", false, theme) {
            showAddCustomAiDialog()
        }
        chipsRow.addView(addChip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) })

        val manageChip = Ui.chip(this, "⚙ Chips", false, theme) {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                putExtra("EXTRA_OPEN_CHIP_MANAGER", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish()
        }
        chipsRow.addView(manageChip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) })
    }

    private fun showAddCustomAiDialog() {
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@OverlayActivity, theme)
            val pad = dp(18)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Add Custom Website or Search App"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Enter name, URL or template with {q} (e.g. https://domain.com/search?q={q})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            setPadding(0, dp(2), 0, dp(12))
        })

        val pad = dp(10)
        val nameInput = android.widget.EditText(this).apply {
            hint = "Website Name (e.g. Reddit, arXiv, Ollama)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, dp(8).toFloat())
            setPadding(pad, pad, pad, pad)
        }
        val urlInput = android.widget.EditText(this).apply {
            hint = "URL or Search Template (https://... or ...?q={q})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, dp(8).toFloat())
            setPadding(pad, pad, pad, pad)
        }
        val pkgInput = android.widget.EditText(this).apply {
            hint = "App Package Name (Optional, e.g. com.example.app)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, dp(8).toFloat())
            setPadding(pad, pad, pad, pad)
        }
        root.addView(nameInput)
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) })
        root.addView(urlInput)
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) })
        root.addView(pkgInput)

        val openInAppCheck = android.widget.CheckBox(this).apply {
            text = "Open in native app if installed"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            layoutParams = lp
        }
        root.addView(openInAppCheck)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
            layoutParams = lp
        }
        val cancelBtn = android.widget.Button(this).apply {
            text = "Cancel"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, dp(8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        val addBtn = android.widget.Button(this).apply {
            text = "Add & Select"
            textSize = 12f
            background = Ui.rounded(theme.ACCENT, dp(8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                val pkg = pkgInput.text.toString().trim().takeIf { it.isNotBlank() }
                val openInApp = openInAppCheck.isChecked
                if (url.isNotBlank()) {
                    val newService = prefs.addCustomService(
                        name = name,
                        url = url,
                        category = ServiceCategory.CUSTOM,
                        openInApp = openInApp,
                        packageName = pkg
                    )
                    refreshChips()
                    updateInputHint()
                    Ui.showMascotToast(this@OverlayActivity, "Added and selected ${newService.name}", prefs.mascotStyle)
                    dialog?.dismiss()
                } else {
                    Ui.showMascotToast(this@OverlayActivity, "Please enter a valid URL", prefs.mascotStyle)
                }
            }
        }
        btnRow.addView(cancelBtn)
        btnRow.addView(addBtn)
        root.addView(btnRow)

        val builder = AlertDialog.Builder(this)
            .setView(root)
        dialog = builder.create()
        dialog?.show()
        dialog?.window?.let { win ->
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            win.decorView.setBackgroundColor(Color.TRANSPARENT)
            val dm = resources.displayMetrics
            win.setLayout(
                (dm.widthPixels * 0.90).toInt().coerceAtMost(dp(420)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun refreshLang() {
        langBtn.text = prefs.voiceLang.chip
    }

    private fun refreshEngineChip() {
        val label = prefs.getEngineChipLabel()
        engineBtn.text = label
        val isFuto = prefs.isFutoActive()
        engineBtn.background = Ui.pill(this, if (isFuto) theme.ACCENT else theme.SURFACE_HI)
        engineBtn.setTextColor(if (isFuto) theme.ON_ACCENT else theme.TEXT)
    }

    private fun toggleEngineMethod() {
        prefs.toggleEngine()
        refreshEngineChip()
        updateInputHint()
        Ui.showMascotToast(this, "Active Voice Input: ${prefs.getEngineChipLabel()}", prefs.mascotStyle, type = ToastType.SPEECH)
    }

    private fun showEngineMenu() {
        val store = ModelStore(this)
        val models = store.getModels()
        val options = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        options.add("System Default Recognizer")
        actions.add {
            prefs.recognizer = Prefs.ENGINE_DEFAULT
        }

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
                showModelPicker(store)
            }
        }

        if (models.size > 1) {
            options.add("  Cycle to Next Model (${models.size} loaded)")
            actions.add {
                val next = store.cycleModel()
                prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
                Ui.showMascotToast(this, "Switched model to: ${next?.name}", prefs.mascotStyle, type = ToastType.SPEECH)
            }
        }

        options.add("Google Speech Recognition (Recommended on Oppo)")
        actions.add {
            prefs.recognizer = Prefs.ENGINE_GOOGLE_TTS
        }

        options.add("FUTO Voice Input / Keyboard (Offline Whisper)")
        actions.add {
            prefs.recognizer = Prefs.ENGINE_FUTO_VOICE_INPUT
        }

        val modeLabel = if (prefs.isPostProcessing) "Post-Processing (Default)" else "Real-Time Streaming"
        options.add("Whisper Mode: $modeLabel (Tap to Switch)")
        actions.add {
            prefs.processingMode = if (prefs.isPostProcessing) Prefs.MODE_REAL_TIME else Prefs.MODE_POST_PROCESSING
            val updated = if (prefs.isPostProcessing) "Post-Processing (Default)" else "Real-Time Streaming"
            Ui.showMascotToast(this, "Set Whisper to $updated", prefs.mascotStyle, type = ToastType.SPEECH)
        }

        options.add("  Open Voice & Model Settings...")
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
                Ui.showMascotToast(this, "Selected model: ${selected.name}", prefs.mascotStyle, type = ToastType.SPEECH)
            }
            .show()
    }

    private fun cycleLang() {
        prefs.voiceLang = prefs.voiceLang.next()
        refreshLang()
        Ui.showMascotToast(this, prefs.voiceLang.label, prefs.mascotStyle, type = ToastType.SPEECH)
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
                    Ui.showMascotToast(this, getString(R.string.toast_copy_failed), prefs.mascotStyle, type = ToastType.CIRCLE)
                    return@runOnUiThread
                }
                selectionFile = s
                StagedFiles.lastSelection = s
                val ok = ClipboardHelper.copy(this, s)
                Ui.showMascotToast(
                    this, getString(if (ok) R.string.toast_copied else R.string.toast_copy_failed), prefs.mascotStyle, type = ToastType.CIRCLE
                )
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
            Ui.showMascotToast(this, getString(R.string.toast_nothing_selected), prefs.mascotStyle, type = ToastType.CIRCLE)
            return
        }
        val ok = ClipboardHelper.copy(this, s)
        Ui.showMascotToast(
            this, getString(if (ok) R.string.toast_copied else R.string.toast_copy_failed), prefs.mascotStyle, type = ToastType.CIRCLE
        )
    }

    private fun refreshAttachments() {
        val files = allFiles()
        if (files.isEmpty()) {
            attachScroll.visibility = View.GONE
        } else {
            attachScroll.visibility = View.VISIBLE
            hint.visibility = View.GONE
            attachRow.removeAllViews()

            for (f in files) {
                val chip = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = Ui.rounded(theme.PANEL, dp(16).toFloat())
                    val padH = dp(8)
                    val padV = dp(4)
                    setPadding(padH, padV, padH, padV)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        rightMargin = dp(6)
                    }
                    layoutParams = lp
                }

                val preview = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    background = Ui.rounded(theme.SURFACE_HI, dp(6).toFloat())
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, dp(6).toFloat())
                        }
                    }
                    if (f.isImage) {
                        setImageBitmap(thumbnail(f))
                    } else {
                        setImageResource(R.drawable.ic_attach)
                        imageTintList = ColorStateList.valueOf(theme.TEXT)
                        val p = dp(3)
                        setPadding(p, p, p, p)
                    }
                }
                chip.addView(preview, LinearLayout.LayoutParams(dp(24), dp(24)))

                val nameLabel = TextView(this).apply {
                    val displayName = if (f === selectionFile) "Circled image" else f.name
                    text = displayName
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(theme.TEXT)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    maxWidth = dp(130)
                    val p = dp(6)
                    setPadding(p, 0, p, 0)
                }
                chip.addView(nameLabel)

                val removeBtn = ImageView(this).apply {
                    setImageResource(R.drawable.ic_close)
                    imageTintList = ColorStateList.valueOf(theme.MUTED)
                    isClickable = true
                    isFocusable = true
                    val p = dp(3)
                    setPadding(p, p, p, p)
                    setOnClickListener {
                        if (f === selectionFile) {
                            selGen++
                            selectionFile = null
                            selection.clearSelection()
                        } else {
                            extras.remove(f)
                        }
                        refreshAttachments()
                    }
                }
                chip.addView(removeBtn, LinearLayout.LayoutParams(dp(20), dp(20)))

                attachRow.addView(chip)
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
        if (failed > 0) Ui.showMascotToast(this, getString(R.string.toast_stage_failed, failed), prefs.mascotStyle, type = ToastType.CIRCLE)
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
            Ui.showMascotToast(this, getString(R.string.toast_stage_failed_generic), prefs.mascotStyle, type = ToastType.CIRCLE)
        }
    }

    private fun pasteClip() {
        val own = selectionFile?.uri
        val uris = ClipboardHelper.clipboardFileUris(this).filter { it != own }
        if (uris.isEmpty()) {
            Ui.showMascotToast(this, getString(R.string.toast_no_clip), prefs.mascotStyle, type = ToastType.CIRCLE)
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
            val activeService = AiServices.current(prefs)

            // 1. Check if user configured this chip to Open in App
            if (activeService.openInApp && !activeService.packageName.isNullOrBlank()) {
                val isInstalled = AiServices.isAppInstalled(this, activeService.packageName)
                if (isInstalled) {
                    val deepLink = activeService.getAppDeepLinkUri(text ?: "")
                    val appIntent = if (!deepLink.isNullOrBlank()) {
                        Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                            setPackage(activeService.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else {
                        packageManager.getLaunchIntentForPackage(activeService.packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    if (appIntent != null) {
                        try {
                            startActivity(appIntent)
                            finish()
                            @Suppress("DEPRECATION")
                            overridePendingTransition(0, 0)
                            return
                        } catch (e: Exception) {
                            // Fallback to web
                        }
                    }
                }
            }

            // 2. Compute target URL (direct search URL for search/social engines)
            val targetUrl = if (activeService.category != ServiceCategory.AI && !text.isNullOrBlank()) {
                activeService.getSearchUrl(text)
            } else {
                activeService.url
            }

            val intent = Intent(this, WebActivity::class.java).apply {
                action = WebActivity.ACTION_ASSIST
                putExtra(WebActivity.EXTRA_MODE, mode)
                putExtra(WebActivity.EXTRA_URL, targetUrl)
                if (text != null) {
                    putExtra(WebActivity.EXTRA_TEXT, text)
                    putExtra("EXTRA_QUERY", text)
                }
                putExtra(WebActivity.EXTRA_SUBMIT, submit)
                if (files.isNotEmpty()) {
                    putExtra(WebActivity.EXTRA_FILES, files.map { it.file.absolutePath }.toTypedArray())
                }

                // If user chose Open in App but app wasn't installed, pass notification info
                if (activeService.openInApp && !activeService.packageName.isNullOrBlank()) {
                    val isInstalled = AiServices.isAppInstalled(this@OverlayActivity, activeService.packageName)
                    if (!isInstalled) {
                        putExtra("EXTRA_NOT_INSTALLED_APP", activeService.name)
                        putExtra("EXTRA_NOT_INSTALLED_ID", activeService.id)
                    }
                }

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Ui.showMascotToast(this, "Could not open assistant: ${e.message}", prefs.mascotStyle, type = ToastType.ASSISTANT)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun toggleMic() {
        if (listening) {
            if (prefs.isLocalModelMode && prefs.isPostProcessing) {
                if (::input.isInitialized) {
                    input.hint = "Transcribing with Whisper..."
                }
            }
            if (::input.isInitialized) {
                basePromptBeforeSpeech = input.text.toString().trim()
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
                Ui.showMascotToast(this, getString(R.string.err_mic_permission), prefs.mascotStyle, duration = Toast.LENGTH_LONG, type = ToastType.SPEECH)
            }
        }
    }

    override fun onStarting(engineName: String) {
        listening = true
        if (::input.isInitialized) {
            basePromptBeforeSpeech = input.text.toString().trim()
        }
        onStateChanged(true, engineName)
    }

    override fun onReadyForSpeech(engineName: String) {
        listening = true
        if (::input.isInitialized && basePromptBeforeSpeech.isEmpty()) {
            basePromptBeforeSpeech = input.text.toString().trim()
        }
        onStateChanged(true, engineName)
    }

    override fun onPartial(text: String) {
        if (!::input.isInitialized) return
        val out = text.trim()
        if (out.isNotEmpty()) {
            val prefix = basePromptBeforeSpeech.trim()
            val combined = if (prefix.isEmpty()) out else "$prefix $out"
            input.setText(combined)
            input.setSelection(combined.length)
            updateSendVisibility()
        }
    }

    override fun onFinal(text: String) {
        if (!::input.isInitialized) return
        val out = text.trim()
        if (out.isEmpty()) {
            onStateChanged(false, speech.currentEngineName)
            return
        }
        val prefix = basePromptBeforeSpeech.trim()
        val combined = if (prefix.isEmpty()) out else "$prefix $out"
        input.setText(combined)
        input.setSelection(combined.length)
        basePromptBeforeSpeech = combined
        updateSendVisibility()
        onStateChanged(false, speech.currentEngineName)
        if (combined.isNotBlank() && prefs.autoSubmit) send()
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (!listening) return
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1f)
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
                    Ui.showMascotToast(this, "Visit https://voiceinput.futo.org/", prefs.mascotStyle, duration = Toast.LENGTH_LONG, type = ToastType.SPEECH)
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
        if (::input.isInitialized) {
            basePromptBeforeSpeech = input.text.toString().trim()
            input.hint = message
            input.postDelayed({
                if (!listening && ::input.isInitialized) updateInputHint()
            }, 2505)
        }
    }

    override fun onStateChanged(listening: Boolean, engineName: String) {
        this.listening = listening
        if (::micBtn.isInitialized) {
            micBtn.background = Ui.pill(this, if (listening) theme.ACCENT else theme.SURFACE_HI)
            micBtn.imageTintList = ColorStateList.valueOf(
                if (listening) theme.ON_ACCENT else theme.TEXT
            )
        }
        if (::searchVisualizerContainer.isInitialized) {
            searchVisualizerContainer.visibility = if (listening) View.VISIBLE else View.GONE
        }
        if (listening) {
            if (::input.isInitialized) {
                input.hint = "Listening via $engineName..."
            }
        } else {
            resetWaveBars()
            updateInputHint()
        }
    }

    private fun resetWaveBars() {
        for (b in searchWaveBars) {
            val lp = b.layoutParams
            lp.height = dp(6)
            b.layoutParams = lp
        }
    }

    private fun petMascot(anchor: View) {
        if (prefs.hapticFeedbackEnabled) {
            anchor.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        val mascotLang = prefs.translationStore.getEffectiveLanguage("mascot")
        val phrase = prefs.mascotDialogueStore.getRandomPhrase(prefs.mascotStyle, mascotLang)
        mascotBubble.text = phrase
        if (prefs.showMascotSpeechBubble) {
            mascotBubble.visibility = View.VISIBLE
            mascotBubble.alpha = 1f
        }
        try {
            val heart = TextView(this).apply {
                text = " "
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            }
            val loc = IntArray(2)
            anchor.getLocationInWindow(loc)
            val rootLoc = IntArray(2)
            root.getLocationInWindow(rootLoc)
            val x = loc[0] - rootLoc[0] + anchor.width / 2 - dp(12)
            val y = loc[1] - rootLoc[1] - dp(10)
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = max(0, x)
                topMargin = max(0, y)
            }
            root.addView(heart, lp)
            heart.animate()
                .translationYBy(-dp(48).toFloat())
                .scaleX(1.4f)
                .scaleY(1.4f)
                .alpha(0f)
                .setDuration(700)
                .withEndAction { root.removeView(heart) }
                .start()
        } catch (_: Exception) {}
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
        speech.destroy()
        ScreenshotHolder.bitmap = null
        super.onDestroy()
    }
}
