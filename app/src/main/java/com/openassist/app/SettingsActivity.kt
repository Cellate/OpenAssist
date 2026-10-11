package com.openassist.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var modelStore: ModelStore
    private lateinit var theme: Ui.Theme
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var assistantStatus: TextView
    private lateinit var engineSelectBtn: Button
    private lateinit var engineStatusText: TextView
    private lateinit var localModelsContainer: LinearLayout
    private lateinit var activeModelBadge: TextView
    private lateinit var langMappingContainer: LinearLayout
    private lateinit var postProcBtn: Button
    private lateinit var realTimeBtn: Button
    private lateinit var testTextBox: EditText
    private lateinit var testMicBtn: Button
    private lateinit var testStatusBanner: TextView
    private lateinit var pipelineInfoText: TextView
    private lateinit var langChipsRow: LinearLayout
    private lateinit var activeAiBadge: TextView
    private lateinit var aiServiceSelectBtn: Button
    private lateinit var customAiContainer: LinearLayout

    private val RC_MODEL_PICK = 42
    private val RC_TEST_SPEECH = 43
    private val RC_MIC_PERMISSION = 101

    private var testSpeech: Speech? = null
    private var isTesting = false
    private var isRecordingLocal = false
    private var isTranscribing = false
    private var baseTestTextBeforeSpeech = ""

    private fun safeString(resId: Int, fallback: String): String {
        return try {
            getString(resId)
        } catch (t: Throwable) {
            fallback
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            initUi()
        } catch (t: Throwable) {
            android.util.Log.e("SettingsActivity", "Fatal error initializing SettingsActivity", t)
            renderSafeFallbackUi(t)
        }
    }

    private fun renderSafeFallbackUi(error: Throwable) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Ui.dp(this@SettingsActivity, 24)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#121212"))
        }
        root.addView(TextView(this).apply {
            text = "OpenAssist Settings"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 12))
        })
        root.addView(TextView(this).apply {
            text = "OpenAssist recovered safely from a startup configuration issue:\n\${error.localizedMessage ?: error.javaClass.simpleName}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#EF4444"))
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 20))
        })
        val launchBtn = Button(this).apply {
            text = "Launch Web AI Assistant"
            background = Ui.rounded(Color.parseColor("#2563EB"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            setOnClickListener {
                try {
                    startActivity(Intent(this@SettingsActivity, WebActivity::class.java))
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Could not launch WebActivity", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(launchBtn)
        val retryBtn = Button(this).apply {
            text = "Reload Settings"
            background = Ui.rounded(Color.parseColor("#374151"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 12)
            }
            layoutParams = lp
            setOnClickListener { recreate() }
        }
        root.addView(retryBtn)
        setContentView(root)
    }

    private fun initUi() {
        try {
            prefs = Prefs(this)
        } catch (e: Throwable) {
            prefs = Prefs(this)
        }
        try {
            theme = Ui.theme(this)
        } catch (e: Throwable) {
            theme = Ui.Theme(isDark = true)
        }
        try {
            modelStore = ModelStore(this)
        } catch (e: Throwable) {
            modelStore = ModelStore(this)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(theme.BG)
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(col)
        setContentView(scroll)

        Ui.applySystemBars(this, theme)

        fun header(text: String) {
            val tv = TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (theme.isDark) theme.ACCENT else Color.parseColor("#1E40AF"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, Ui.dp(this@SettingsActivity, 20), 0, Ui.dp(this@SettingsActivity, 8))
            }
            col.addView(tv)
        }

        fun subheader(text: String) {
            val tv = TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, Ui.dp(this@SettingsActivity, 4))
            }
            col.addView(tv)
        }

        fun cardLayout(): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.card(this@SettingsActivity, theme)
                val pad = Ui.dp(this@SettingsActivity, 14)
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = Ui.dp(this@SettingsActivity, 12)
                }
                layoutParams = lp
            }
        }

        fun toggle(title: String, desc: String, initial: Boolean, onChange: (Boolean) -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, Ui.dp(this@SettingsActivity, 8))
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(theme.TEXT)
            })
            textCol.addView(TextView(this).apply {
                text = desc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(theme.MUTED)
            })
            val sw = Switch(this).apply {
                isChecked = initial
                setOnCheckedChangeListener { _, c -> onChange(c) }
            }
            row.addView(textCol)
            row.addView(sw)
            col.addView(row)
        }

        header("Appearance")
        val appearanceCard = cardLayout()
        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 4))
        }
        val themeTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        themeTextCol.addView(TextView(this).apply {
            text = "Dark Mode"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(theme.TEXT)
        })
        themeTextCol.addView(TextView(this).apply {
            text = if (prefs.isDarkMode) "Gemini Dark Mode active" else "Material 3 Light Mode active"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.MUTED)
        })
        val themeSwitch = Switch(this).apply {
            isChecked = prefs.isDarkMode
            setOnCheckedChangeListener { _, isDark ->
                prefs.isDarkMode = isDark
                recreate()
            }
        }
        themeRow.addView(themeTextCol)
        themeRow.addView(themeSwitch)
        appearanceCard.addView(themeRow)
        col.addView(appearanceCard)

        header(safeString(R.string.st_assistant, "Digital assistant"))
        val assistantCard = cardLayout()
        assistantStatus = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            text = safeString(R.string.st_assistant_on, "Active. Long-press Home (or use your assist gesture) to open it.")
        }
        assistantCard.addView(assistantStatus)
        val setBtn = Button(this).apply {
            text = safeString(R.string.st_set_assistant, "Choose default assistant")
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Open Settings > Default apps > Assistant", Toast.LENGTH_LONG).show()
                }
            }
        }
        assistantCard.addView(setBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })

        val openChatBtn = Button(this).apply {
            text = "Open Web Assistant (Gemini / AI Chat)"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, WebActivity::class.java))
            }
        }
        assistantCard.addView(openChatBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })

        val wizardBtn = Button(this).apply {
            text = "  Open Setup Wizard (First-Run Guide)"
            background = Ui.rounded(Color.parseColor("#4F46E5"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { showSetupWizardDialog() }
        }
        assistantCard.addView(wizardBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })
        col.addView(assistantCard)

        header("AI Web Assistant & Custom AI Services")
        val aiCard = cardLayout()
        val topAiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topAiRow.addView(TextView(this).apply {
            text = "Active Assistant:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
        })
        activeAiBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.pill(this@SettingsActivity, Color.parseColor("#1D4ED8"))
            val padH = Ui.dp(this@SettingsActivity, 10)
            val padV = Ui.dp(this@SettingsActivity, 4)
            setPadding(padH, padV, padH, padV)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = Ui.dp(this@SettingsActivity, 8)
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        topAiRow.addView(activeAiBadge)
        aiServiceSelectBtn = Button(this).apply {
            text = "Switch AI"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener { showAiServiceSelectorDialog() }
        }
        topAiRow.addView(aiServiceSelectBtn)
        aiCard.addView(topAiRow)

        customAiContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        aiCard.addView(customAiContainer)

        val aiActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        val addCustomAiBtn = Button(this).apply {
            text = "+ Add Custom Web Assistant"
            background = Ui.rounded(Color.parseColor("#2563EB"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener { showAddCustomAiDialog() }
        }
        aiActionsRow.addView(addCustomAiBtn)
        val openActiveAiBtn = Button(this).apply {
            text = "Launch Web AI"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, WebActivity::class.java))
            }
        }
        aiActionsRow.addView(openActiveAiBtn)
        aiCard.addView(aiActionsRow)
        col.addView(aiCard)

        header("Voice Input Engine & Whisper Local Mode")
        val engineCard = cardLayout()
        engineCard.addView(TextView(this).apply {
            text = "Active Speech Input Engine"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.TEXT)
        })
        engineStatusText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 8))
        }
        engineCard.addView(engineStatusText)
        engineSelectBtn = Button(this).apply {
            background = Ui.rounded(Color.parseColor("#1D4ED8"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { showEngineSelectorDialog() }
        }
        engineCard.addView(engineSelectBtn)
        col.addView(engineCard)

        header("Local Offline Whisper Models")
        val modelHeaderCard = cardLayout()
        val topModelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topModelRow.addView(TextView(this).apply {
            text = "Active Model:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
        })
        activeModelBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.pill(this@SettingsActivity, Color.parseColor("#2563EB"))
            val padH = Ui.dp(this@SettingsActivity, 10)
            val padV = Ui.dp(this@SettingsActivity, 4)
            setPadding(padH, padV, padH, padV)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = Ui.dp(this@SettingsActivity, 8)
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        topModelRow.addView(activeModelBadge)
        val cycleBtn = Button(this).apply {
            text = "Switch"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener { cycleToNextModel() }
        }
        topModelRow.addView(cycleBtn)
        modelHeaderCard.addView(topModelRow)

        localModelsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, 0)
        }
        modelHeaderCard.addView(localModelsContainer)

        val modelActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, 0)
        }
        val loadModelBtn = Button(this).apply {
            text = "  Import Model (.bin)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            setOnClickListener { pickCustomModel() }
        }
        modelActionsRow.addView(loadModelBtn)
        modelHeaderCard.addView(modelActionsRow)
        col.addView(modelHeaderCard)

        val modeCard = cardLayout()
        modeCard.addView(TextView(this).apply {
            text = "Whisper Processing Mode"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.TEXT)
        })
        val modeSwitchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        postProcBtn = Button(this).apply {
            text = "Post-Processing (Default)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                prefs.processingMode = Prefs.MODE_POST_PROCESSING
                updateModeButtonsUi()
                Toast.makeText(this@SettingsActivity, "Set to Post-Processing Mode (Default)", Toast.LENGTH_SHORT).show()
            }
        }
        realTimeBtn = Button(this).apply {
            text = "Real-Time Streaming"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener {
                prefs.processingMode = Prefs.MODE_REAL_TIME
                updateModeButtonsUi()
                Toast.makeText(this@SettingsActivity, "Set to Real-Time Streaming Mode", Toast.LENGTH_SHORT).show()
            }
        }
        modeSwitchRow.addView(postProcBtn)
        modeSwitchRow.addView(realTimeBtn)
        modeCard.addView(modeSwitchRow)
        col.addView(modeCard)

        subheader("Per-Language Model Binding")
        val langMapCard = cardLayout()
        langMappingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        langMapCard.addView(langMappingContainer)
        col.addView(langMapCard)

        header("Test Voice Dictation & Transcription")
        val testCard = cardLayout()
        val langScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 8))
        }
        langChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        langScroll.addView(langChipsRow)
        testCard.addView(langScroll)

        pipelineInfoText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
            background = Ui.rounded(if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF"), Ui.dp(this@SettingsActivity, 6).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
        }
        testCard.addView(pipelineInfoText)

        testStatusBanner = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#EF4444"))
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
            text = "  LIVE MIC ACTIVE   Listening..."
        }
        testCard.addView(testStatusBanner)

        testTextBox = EditText(this).apply {
            hint = "Recognized transcription will appear here in real time..."
            setHintTextColor(theme.MUTED)
            setTextColor(theme.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 10)
            setPadding(pad, pad, pad, pad)
        }
        val textLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = Ui.dp(this@SettingsActivity, 8)
            bottomMargin = Ui.dp(this@SettingsActivity, 10)
        }
        testCard.addView(testTextBox, textLp)

        val testControlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        testMicBtn = Button(this).apply {
            text = "Tap to Speak / Test"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener { toggleTestSpeech() }
        }
        val copyBtn = Button(this).apply {
            text = "Copy"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                val txt = testTextBox.text.toString()
                if (txt.isNotBlank()) {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("OpenAssist Test", txt))
                    Toast.makeText(this@SettingsActivity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val clearBtn = Button(this).apply {
            text = "Clear"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f)
            layoutParams = lp
            setOnClickListener { testTextBox.setText("") }
        }
        testControlsRow.addView(testMicBtn)
        testControlsRow.addView(copyBtn)
        testControlsRow.addView(clearBtn)
        testCard.addView(testControlsRow)
        col.addView(testCard)

        header(safeString(R.string.st_behaviour, "Behaviour"))
        toggle("Auto-Listen on Launch", "Immediately open microphone when overlay opens so you can speak right away.", prefs.autoStartMic) { prefs.autoStartMic = it }
        toggle(safeString(R.string.st_new_chat, "Start a new chat each time"), safeString(R.string.st_new_chat_desc, "Reloads the AI page on every assist so your question is not mixed into an old chat. Turn off to keep the current chat."), prefs.newChatOnAssist) { prefs.newChatOnAssist = it }
        toggle(safeString(R.string.st_auto_attach, "Attach circled image automatically"), safeString(R.string.st_auto_attach_desc, "Pastes the copied selection into the chat box after the page opens."), prefs.autoAttach) { prefs.autoAttach = it }
        toggle(safeString(R.string.st_auto_submit, "Send the question automatically"), safeString(R.string.st_auto_submit_desc, "After you type or dictate in the overlay, press the chat's send button for you."), prefs.autoSubmit) { prefs.autoSubmit = it }
        toggle(safeString(R.string.st_circle_compose, "Stay in the overlay after circling"), safeString(R.string.st_circle_compose_desc, "Lets you type or dictate your question right after circling. Off: jump straight to the AI so you can paste and ask there."), prefs.composeAfterCircle) { prefs.composeAfterCircle = it }

        header("Mascot & Aesthetics")
        val companionCard = cardLayout()
        companionCard.addView(TextView(this).apply {
            text = safeString(R.string.pref_mascot_companion_title, "Active Mascot Companion")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.TEXT)
        })

        val mascotsList = listOf(
            Triple("owl-orbital", "Ozzy", R.drawable.ic_mascot_owl_orbital),
            Triple("owl", "Ozzy Classic", R.drawable.ic_mascot_owl),
            Triple("fox", "Pip Nexus", R.drawable.ic_mascot_fox),
            Triple("bat", "Echo Bat", R.drawable.ic_mascot_bat),
            Triple("otter", "Nova Otter", R.drawable.ic_mascot_otter),
            Triple("chameleon", "Pico Chameleon", R.drawable.ic_mascot_chameleon),
            Triple("tanuki", "Taro Tanuki", R.drawable.ic_mascot_tanuki),
            Triple("sprite", "Ollie Sprite", R.drawable.ic_mascot_sprite),
            Triple("cat", "Byte Kitten", R.drawable.ic_mascot_kitten),
            Triple("firefly", "Lumi Firefly", R.drawable.ic_mascot_firefly),
            Triple("axolotl", "Kiko Axolotl", R.drawable.ic_mascot_axolotl),
            Triple("owl-talons", "Ozzy Kawaii Perch", R.drawable.ic_mascot_owl_talons),
            Triple("owl-radar", "Ozzy Radar", R.drawable.ic_mascot_owl_radar),
            Triple("owl-wings", "Ozzy Shield", R.drawable.ic_mascot_owl_wings),
            Triple("owl-monocle", "Ozzy Scholar", R.drawable.ic_mascot_owl_monocle),
            Triple("owl-minimal", "Ozzy Minimal", R.drawable.ic_mascot_owl_minimal),
            Triple("fox-orbital", "Pip Astro-Lasso", R.drawable.ic_mascot_fox_orbital),
            Triple("fox-cyber", "Pip Cyber-Visor", R.drawable.ic_mascot_fox_cyber),
            Triple("fox-paws", "Pip Paws", R.drawable.ic_mascot_fox_paws),
            Triple("fox-firefly", "Pip Radar-Lens", R.drawable.ic_mascot_fox_firefly),
            Triple("fox-halo", "Pip Cosmic-Halo", R.drawable.ic_mascot_fox_halo),
            Triple("fox-headset", "Pip Voice-Sentinel", R.drawable.ic_mascot_fox_headset),
            Triple("fox-monocle", "Pip Scout-Loupe", R.drawable.ic_mascot_fox_monocle),
            Triple("fox-minimal", "Pip Pure-Minimal", R.drawable.ic_mascot_fox_minimal),
            Triple("orbital", "Kube-Loop Iris", R.drawable.ic_mascot_orbital),
            Triple("whale", "Moby-Ray", R.drawable.ic_mascot_whale)
        )

        val mascotScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 4))
        }
        val mascotRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val mascotViews = ArrayList<Pair<String, View>>()

        fun updateMascotCardSelection() {
            val current = prefs.mascotStyle.lowercase()
            for ((key, v) in mascotViews) {
                val isSelected = current == key ||
                    (key == "fox" && (current == "pip" || current == "fennec")) ||
                    (key == "cat" && (current == "kitten" || current == "byte" || current == "luna")) ||
                    (key == "sprite" && (current == "ollie" || current == "bolt" || current == "spark")) ||
                    (key == "otter" && (current == "nova" || current == "pip_otter")) ||
                    (key == "tanuki" && (current == "taro" || current == "momo"))
                v.background = Ui.rounded(
                    if (isSelected) (if (theme.isDark) Color.parseColor("#312E81") else Color.parseColor("#EEF2FF")) else theme.SURFACE_HI,
                    Ui.dp(this@SettingsActivity, 12).toFloat()
                )
                if (v is LinearLayout) {
                    val tv = v.getChildAt(1) as? TextView
                    tv?.setTextColor(if (isSelected) (if (theme.isDark) Color.parseColor("#818CF8") else Color.parseColor("#4338CA")) else theme.TEXT)
                    tv?.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
            }
        }

        for ((key, name, resId) in mascotsList) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val padH = Ui.dp(this@SettingsActivity, 12)
                val padV = Ui.dp(this@SettingsActivity, 8)
                setPadding(padH, padV, padH, padV)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = Ui.dp(this@SettingsActivity, 8)
                }
                layoutParams = lp
                val img = ImageView(this@SettingsActivity).apply {
                    setImageResource(resId)
                    val s = Ui.dp(this@SettingsActivity, 36)
                    layoutParams = LinearLayout.LayoutParams(s, s)
                }
                addView(img)
                val label = TextView(this@SettingsActivity).apply {
                    text = name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(theme.TEXT)
                    setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, 0)
                }
                addView(label)
                setOnClickListener {
                    prefs.mascotStyle = key
                    updateMascotCardSelection()
                    LauncherAliasHelper.updateLauncherIcon(this@SettingsActivity, key)
                    Toast.makeText(this@SettingsActivity, "Active mascot set to $name!", Toast.LENGTH_SHORT).show()
                }
            }
            mascotViews.add(Pair(key, item))
            mascotRow.addView(item)
        }
        updateMascotCardSelection()
        mascotScroll.addView(mascotRow)
        companionCard.addView(mascotScroll)
        col.addView(companionCard)

        toggle(
            safeString(R.string.pref_mascot_top_chips_title, "Show Mascot Beside Top Chips"),
            safeString(R.string.pref_mascot_top_chips_desc, "Display your companion icon next to Copy, Whole, Circle, Fit"),
            prefs.showMascotTopChips
        ) { prefs.showMascotTopChips = it }

        toggle(
            safeString(R.string.pref_mascot_search_pill_title, "Show Mascot in Prompt Search Bar"),
            safeString(R.string.pref_mascot_search_pill_desc, "Display an optional companion icon inside the left edge of the prompt pill for aesthetic balance"),
            prefs.showMascotSearchPill
        ) { prefs.showMascotSearchPill = it }

        toggle(
            safeString(R.string.pref_mascot_bubble_title, "Mascot Speech Bubbles & Petting"),
            safeString(R.string.pref_mascot_bubble_desc, "Display cheerful responses, sweet phrases, and cute dialogue when you tap the mascot"),
            prefs.showMascotSpeechBubble
        ) { prefs.showMascotSpeechBubble = it }

        toggle(
            safeString(R.string.pref_haptic_title, "Haptic Petting Feedback"),
            safeString(R.string.pref_haptic_desc, "Subtle vibration pulse when petting or interacting with the mascot"),
            prefs.hapticFeedbackEnabled
        ) { prefs.hapticFeedbackEnabled = it }

        header(safeString(R.string.st_about, "About OpenAssist"))
        val aboutCard = cardLayout()
        aboutCard.addView(TextView(this).apply {
            text = "OpenAssist v0.2.0   Offline Multi-Model Whisper Engine\nPrivacy First: Audio stays on your device when using Local or FUTO modes."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
        })
        col.addView(aboutCard)

        try {
            updateModeButtonsUi()
            refreshAll()
            if (!prefs.firstRunDone) {
                col.post { showSetupWizardDialog() }
            }
        } catch (t: Throwable) {
            android.util.Log.e("SettingsActivity", "Error during init refresh", t)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (::assistantStatus.isInitialized) {
                assistantStatus.text = safeString(R.string.st_assistant_on, "Active. Long-press Home (or use your assist gesture) to open it.")
            }
            refreshAll()
        } catch (t: Throwable) {
            android.util.Log.e("SettingsActivity", "Error in onResume", t)
        }
    }

    private fun updateModeButtonsUi() {
        if (!::postProcBtn.isInitialized || !::realTimeBtn.isInitialized) return
        val isPost = prefs.isPostProcessing
        postProcBtn.background = Ui.rounded(if (isPost) Color.parseColor("#2563EB") else theme.SURFACE_HI, Ui.dp(this, 8).toFloat())
        postProcBtn.setTextColor(if (isPost) Color.WHITE else theme.MUTED)
        realTimeBtn.background = Ui.rounded(if (!isPost) Color.parseColor("#2563EB") else theme.SURFACE_HI, Ui.dp(this, 8).toFloat())
        realTimeBtn.setTextColor(if (!isPost) Color.WHITE else theme.MUTED)
        refreshPipelineInfo()
    }

    private fun refreshAll() {
        try { refreshAiServices() } catch (t: Throwable) {}
        try { refreshEngineViews() } catch (t: Throwable) {}
        try { refreshModelsList() } catch (t: Throwable) {}
        try { refreshLanguageChips() } catch (t: Throwable) {}
        try { refreshLangMappings() } catch (t: Throwable) {}
        try { refreshPipelineInfo() } catch (t: Throwable) {}
    }

    private fun refreshAiServices() {
        if (!::activeAiBadge.isInitialized || !::customAiContainer.isInitialized) return
        val current = AiServices.current(prefs)
        activeAiBadge.text = current.name + (if (current.isCustom) " (Custom)" else "")
        customAiContainer.removeAllViews()
        val customServices = prefs.getCustomServices()
        if (customServices.isEmpty()) {
            customAiContainer.addView(TextView(this).apply {
                text = "No custom web assistants added yet. Tap '+ Add Custom Web Assistant' below to connect Ollama, Open WebUI, or new AI models."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 4))
            })
            return
        }
        for (cs in customServices) {
            val isCurrent = cs.id == prefs.serviceId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(if (isCurrent) (if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF")) else theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                val pad = Ui.dp(this@SettingsActivity, 8)
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = Ui.dp(this@SettingsActivity, 4)
                }
                layoutParams = lp
            }
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(TextView(this).apply {
                text = (if (isCurrent) "  " else "") + cs.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (isCurrent) (if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8")) else theme.TEXT)
                typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            })
            infoCol.addView(TextView(this).apply {
                text = cs.url
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(theme.MUTED)
                maxLines = 1
            })
            row.addView(infoCol)
            val selectBtn = Button(this).apply {
                text = if (isCurrent) "Active" else "Select"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(if (isCurrent) Color.parseColor("#2563EB") else theme.SURFACE, Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(if (isCurrent) Color.WHITE else theme.TEXT)
                setOnClickListener {
                    prefs.serviceId = cs.id
                    refreshAiServices()
                    Toast.makeText(this@SettingsActivity, "Activated ${cs.name}", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(selectBtn)
            val deleteBtn = Button(this).apply {
                text = "X"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(theme.SURFACE, Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(Color.parseColor("#EF4444"))
                val lp = LinearLayout.LayoutParams(Ui.dp(this@SettingsActivity, 36), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = Ui.dp(this@SettingsActivity, 6)
                }
                layoutParams = lp
                setOnClickListener {
                    showThemedDialog(
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Remove ${cs.name}")
                            .setMessage("Remove this custom AI assistant URL from OpenAssist?")
                            .setPositiveButton("Remove") { _, _ ->
                                prefs.removeCustomService(cs.id)
                                refreshAiServices()
                                Toast.makeText(this@SettingsActivity, "Removed ${cs.name}", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                    )
                }
            }
            row.addView(deleteBtn)
            customAiContainer.addView(row)
        }
    }

    private fun showThemedDialog(builder: AlertDialog.Builder): AlertDialog {
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(Ui.rounded(theme.SURFACE, Ui.dp(this, 16).toFloat()))
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(theme.ACCENT)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(theme.MUTED)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(theme.MUTED)
        return dialog
    }

    private fun showAiServiceSelectorDialog() {
        val allServices = AiServices.all(prefs)
        val names = allServices.map {
            if (it.id == prefs.serviceId) "  ${it.name} (Active)" else it.name
        }.toTypedArray()
        showThemedDialog(
            AlertDialog.Builder(this)
                .setTitle("Select AI Web Assistant")
                .setItems(names) { _, which ->
                    val chosen = allServices[which]
                    prefs.serviceId = chosen.id
                    refreshAiServices()
                    Toast.makeText(this, "Active Assistant: ${chosen.name}", Toast.LENGTH_SHORT).show()
                }
                .setPositiveButton("+ Add Custom AI") { _, _ ->
                    showAddCustomAiDialog()
                }
                .setNegativeButton("Cancel", null)
        )
    }

    private fun showAddCustomAiDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        val nameInput = EditText(this).apply {
            hint = "AI Name (e.g. Open WebUI, Ollama, Grok 3)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
        }
        val urlInput = EditText(this).apply {
            hint = "Web URL (e.g. https://... or http://192.168.1.x:3000)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
        }
        layout.addView(nameInput)
        val sp = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@SettingsActivity, 8)) }
        layout.addView(sp)
        layout.addView(urlInput)
        showThemedDialog(
            AlertDialog.Builder(this)
                .setTitle("Add Custom AI Web Assistant")
                .setView(layout)
                .setPositiveButton("Add & Select") { _, _ ->
                    val name = nameInput.text.toString().trim()
                    val url = urlInput.text.toString().trim()
                    if (url.isNotBlank()) {
                        val newService = prefs.addCustomService(name, url)
                        refreshAiServices()
                        Toast.makeText(this, "Added and selected ${newService.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
        )
    }

    private fun refreshEngineViews() {
        if (!::engineSelectBtn.isInitialized || !::engineStatusText.isInitialized) return
        when (prefs.recognizer) {
            Prefs.ENGINE_LOCAL_MODEL -> {
                val currentModel = prefs.getActiveLocalModel()
                val modelName = currentModel?.name ?: "None"
                val modeLabel = if (prefs.isPostProcessing) "Post-Processing" else "Real-Time"
                engineSelectBtn.text = "Local Model Mode ($modelName - $modeLabel)"
                engineSelectBtn.background = Ui.rounded(Color.parseColor("#2563EB"), Ui.dp(this, 8).toFloat())
                engineStatusText.text = "Active: Offline Multi-Model Whisper ($modeLabel). Transcriptions run on-device with zero network latency."
            }
            Prefs.ENGINE_GOOGLE_TTS -> {
                engineSelectBtn.text = "Google Speech Recognition & Synthesis"
                engineSelectBtn.background = Ui.rounded(Color.parseColor("#059669"), Ui.dp(this, 8).toFloat())
                engineStatusText.text = "Active: Google Speech Recognition & Synthesis (Official Google engine for ColorOS / Oppo & Android)."
            }
            Prefs.ENGINE_FUTO_VOICE_INPUT -> {
                engineSelectBtn.text = "FUTO Voice Input / Keyboard (Offline Whisper)"
                engineSelectBtn.background = Ui.rounded(Color.parseColor("#7C3AED"), Ui.dp(this, 8).toFloat())
                engineStatusText.text = "Active: FUTO Voice Input (Free on-device open-source Whisper transcription)."
            }
            else -> {
                engineSelectBtn.text = "System Default Speech Recognizer"
                engineSelectBtn.background = Ui.rounded(Color.parseColor("#374151"), Ui.dp(this, 8).toFloat())
                engineStatusText.text = "Active: Android System Default Voice Input."
            }
        }
    }

    private fun refreshModelsList() {
        if (!::localModelsContainer.isInitialized || !::activeModelBadge.isInitialized) return
        localModelsContainer.removeAllViews()
        val models = modelStore.getModels()
        val activeModel = prefs.getActiveLocalModel()
        activeModelBadge.text = activeModel?.name ?: "No model active"
        if (models.isEmpty()) {
            localModelsContainer.addView(TextView(this).apply {
                text = "No custom Whisper models loaded yet. Tap 'Load Model Binary' to add your offline .bin model."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 6))
            })
            return
        }
        val defaultId = modelStore.getDefaultModelId()
        for (m in models) {
            val isDefault = m.id == defaultId || m.id == activeModel?.id
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(if (isDefault) (if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF")) else theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                val pad = Ui.dp(this@SettingsActivity, 8)
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = Ui.dp(this@SettingsActivity, 6)
                }
                layoutParams = lp
            }
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(TextView(this).apply {
                text = (if (isDefault) "  " else "") + m.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (isDefault) (if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8")) else theme.TEXT)
                typeface = if (isDefault) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            })
            row.addView(infoCol)
            val selectBtn = Button(this).apply {
                text = if (isDefault) "Active" else "Select"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(if (isDefault) Color.parseColor("#2563EB") else theme.SURFACE, Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(if (isDefault) Color.WHITE else theme.TEXT)
                setOnClickListener {
                    modelStore.setDefaultModelId(m.id)
                    modelStore.setModelIdForLang(prefs.voiceLang, m.id)
                    prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
                    refreshAll()
                    Toast.makeText(this@SettingsActivity, "Activated ${m.name}", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(selectBtn)
            localModelsContainer.addView(row)
        }
    }

    private fun cycleToNextModel() {
        val next = modelStore.cycleModel()
        if (next != null) {
            modelStore.setModelIdForLang(prefs.voiceLang, next.id)
            prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
            refreshAll()
            Toast.makeText(this, "Switched to: ${next.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshLangMappings() {
        if (!::langMappingContainer.isInitialized) return
        langMappingContainer.removeAllViews()
        val langs = listOf(
            Triple(VoiceLang.HINGLISH, "Hinglish (Default)", "English script for Hindi speech"),
            Triple(VoiceLang.HINDI, "Hindi (Secondary)", "Devanagari script"),
            Triple(VoiceLang.ENGLISH, "English", "Standard English speech dictation")
        )
        for ((lang, title, desc) in langs) {
            val assignedId = modelStore.getModelIdForLang(lang)
            val assignedModel = modelStore.getModel(assignedId) ?: modelStore.getDefaultModel()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = Ui.dp(this@SettingsActivity, 8)
                setPadding(0, pad, 0, pad)
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(theme.TEXT)
                typeface = Typeface.DEFAULT_BOLD
            })
            val modelLabel = TextView(this).apply {
                text = "Model: ${assignedModel?.name ?: "Default Model"}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
            }
            textCol.addView(modelLabel)
            row.addView(textCol)
            langMappingContainer.addView(row)
        }
    }

    private fun refreshLanguageChips() {
        if (!::langChipsRow.isInitialized) return
        langChipsRow.removeAllViews()
        for (lang in VoiceLang.values()) {
            val isCurrent = lang == prefs.voiceLang
            val chip = TextView(this).apply {
                text = lang.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isCurrent) Color.WHITE else theme.TEXT)
                background = Ui.pill(this@SettingsActivity, if (isCurrent) Color.parseColor("#2563EB") else theme.SURFACE_HI)
                val pH = Ui.dp(this@SettingsActivity, 12)
                val pV = Ui.dp(this@SettingsActivity, 6)
                setPadding(pH, pV, pH, pV)
                setOnClickListener {
                    prefs.voiceLang = lang
                    refreshLanguageChips()
                    refreshPipelineInfo()
                }
            }
            langChipsRow.addView(chip)
        }
    }

    private fun refreshPipelineInfo() {
        if (!::pipelineInfoText.isInitialized) return
        val lang = prefs.voiceLang
        val engineLabel = prefs.getEngineChipLabel()
        val assignedModel = modelStore.getModelForLang(lang)
        val modeStr = if (prefs.isPostProcessing) "Post-Processing" else "Real-Time"
        val modelText = if (prefs.isLocalModelMode) {
            "Model: ${assignedModel?.name ?: "None"} ($modeStr)"
        } else {
            "Engine: $engineLabel"
        }
        pipelineInfoText.text = "Active Pipeline: [${lang.label}] -> $modelText"
    }

    private fun showEngineSelectorDialog() {
        val names = arrayOf(
            "System Default Speech Recognizer",
            "Local Model Mode (Offline Multi-Model Whisper)",
            "Google Speech Recognition & Synthesis (Google TTS / Oppo)",
            "FUTO Voice Input / Keyboard (Offline Whisper)"
        )
        AlertDialog.Builder(this)
            .setTitle("Select Voice Input Source")
            .setItems(names) { _, which ->
                when (which) {
                    0 -> prefs.recognizer = Prefs.ENGINE_DEFAULT
                    1 -> prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
                    2 -> prefs.recognizer = Prefs.ENGINE_GOOGLE_TTS
                    3 -> prefs.recognizer = Prefs.ENGINE_FUTO_VOICE_INPUT
                }
                refreshAll()
            }
            .show()
    }

    private fun pickCustomModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, RC_MODEL_PICK)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleTestSpeech() {
        if (isTesting) {
            stopTestSpeech()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_MIC_PERMISSION)
            return
        }
        startTestSpeech()
    }

    private fun startTestSpeech() {
        testSpeech?.destroy()
        isTesting = true
        testMicBtn.text = "Stop Listening"
        testMicBtn.background = Ui.rounded(Color.parseColor("#EF4444"), Ui.dp(this, 8).toFloat())
        testStatusBanner.visibility = View.VISIBLE
        baseTestTextBeforeSpeech = testTextBox.text.toString().trim()

        val speech = Speech(this, prefs, object : Speech.Callback {
            override fun onStarting(engineName: String) {
                testStatusBanner.text = "CONNECTING MIC ($engineName)..."
            }
            override fun onReadyForSpeech(engineName: String) {
                testStatusBanner.text = "LIVE MIC ACTIVE ($engineName) -> Listening... Speak now!"
            }
            override fun onPartial(text: String) {
                val out = text.trim()
                if (out.isNotEmpty()) {
                    val prefix = baseTestTextBeforeSpeech.trim()
                    val combined = if (prefix.isEmpty()) out else "$prefix $out"
                    testTextBox.setText(combined)
                    testTextBox.setSelection(combined.length)
                }
            }
            override fun onFinal(text: String) {
                val out = text.trim()
                val prefix = baseTestTextBeforeSpeech.trim()
                val combined = if (out.isEmpty()) prefix else if (prefix.isEmpty()) out else "$prefix $out"
                testTextBox.setText(combined)
                testTextBox.setSelection(combined.length)
                stopTestSpeech()
            }
            override fun onError(message: String) {
                stopTestSpeech()
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
            }
            override fun onStateChanged(listening: Boolean, engineName: String) {
                if (!listening) stopTestSpeech()
            }
        })
        testSpeech = speech
        speech.start()
    }

    private fun stopTestSpeech() {
        isTesting = false
        testMicBtn.text = "Tap to Speak / Test"
        testMicBtn.background = Ui.rounded(theme.ACCENT, Ui.dp(this, 8).toFloat())
        testStatusBanner.visibility = View.GONE
        testSpeech?.stop()
        testSpeech = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_MIC_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startTestSpeech()
        }
    }

    private fun showSetupWizardDialog() {
        val steps = arrayOf(
            "1. Default Assistant & Screen Capture",
            "2. Choose Mascot Companion",
            "3. Select AI Service",
            "4. Speech Recognition Engine",
            "5. Finish & Explore"
        )
        fun showStep(step: Int) {
            val builder = AlertDialog.Builder(this)
            val view = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.rounded(theme.SURFACE, Ui.dp(this@SettingsActivity, 16).toFloat())
                setPadding(Ui.dp(this@SettingsActivity, 22), Ui.dp(this@SettingsActivity, 20), Ui.dp(this@SettingsActivity, 22), Ui.dp(this@SettingsActivity, 16))
            }
            val stepHeader = TextView(this).apply {
                text = "OpenAssist Setup Wizard (${step + 1}/${steps.size})"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(theme.ACCENT)
                typeface = Typeface.DEFAULT_BOLD
            }
            view.addView(stepHeader)
            val stepTitle = TextView(this).apply {
                text = steps[step]
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(theme.TEXT)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 8))
            }
            view.addView(stepTitle)

            builder.setView(view)
            if (step > 0) {
                builder.setNegativeButton("Back") { _, _ -> showStep(step - 1) }
            } else {
                builder.setNegativeButton("Skip") { d, _ ->
                    prefs.firstRunDone = true
                    d.dismiss()
                }
            }
            if (step < steps.size - 1) {
                builder.setPositiveButton("Next") { _, _ -> showStep(step + 1) }
            } else {
                builder.setPositiveButton("Done") { d, _ ->
                    prefs.firstRunDone = true
                    d.dismiss()
                    Toast.makeText(this@SettingsActivity, "Welcome to OpenAssist!", Toast.LENGTH_SHORT).show()
                }
            }
            val dialog = builder.create()
            dialog.window?.setBackgroundDrawable(Ui.rounded(theme.SURFACE, Ui.dp(this@SettingsActivity, 16).toFloat()))
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(theme.ACCENT)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(theme.MUTED)
        }
        showStep(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        testSpeech?.destroy()
        testSpeech = null
    }
}
