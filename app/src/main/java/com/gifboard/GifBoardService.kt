package com.gifboard

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream

/**
 * Main InputMethodService for the GIF IME.
 * Provides GIF search with minimal on-screen keyboard.
 */
class GifBoardService : InputMethodService() {

    companion object {
        private const val TAG = "GifBoardService"
        private const val PREFETCH_THRESHOLD = 8
        private const val DOUBLE_TAP_DELAY_MS = 300L
    }

    enum class KeyboardMode {
        NORMAL,      // lowercase letters
        SHIFTED,     // uppercase for one character
        CAPS_LOCK,   // uppercase until toggled off
        SYMBOLS      // numbers and special characters
    }

    private lateinit var searchInput: EditText
    private lateinit var clearButton: ImageButton
    private lateinit var searchButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var gifRecycler: RecyclerView
    private lateinit var rootView: View

    // History UI
    private lateinit var historyContainer: View
    private lateinit var historyRecycler: RecyclerView
    private lateinit var clearAllButton: Button
    private lateinit var historyAdapter: SearchHistoryAdapter
    private lateinit var historyDb: SearchHistoryDbHelper

    // Keyboard mode state
    private var currentMode = KeyboardMode.NORMAL
    private var lastShiftTapTime = 0L

    // Key button references for updating display
    private val letterButtons = mutableMapOf<Int, Button>()
    private lateinit var shiftButton: ImageButton
    private lateinit var symbolsButton: Button
    private lateinit var keyboardContainer: View
    private lateinit var searchBarContainer: View
    private lateinit var searchOverlay: View
    private var isSearchBarActive = false

    private lateinit var adapter: GifAdapter
    private val fetcher = GoogleGifFetcher()

    // Backspace repeat handling
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRunnable: Runnable? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentQuery = ""
    private var currentPage = 0
    private var isLoadingPage = false
    private var hasMorePages = true
    private var searchJob: Job? = null

    // Vibration
    private var vibrator: Vibrator? = null
    private var vibrationStrength: String = "medium"

    override fun onCreate() {
        super.onCreate()
        GifImageLoader.initialize(this)
        historyDb = SearchHistoryDbHelper(this)

        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun performKeyHaptic() {
        if (vibrationStrength == "off") return
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (vibrationStrength) {
                    "light" -> 30
                    "medium" -> 80
                    "strong" -> 150
                    else -> 80
                }
                v.vibrate(VibrationEffect.createOneShot(5, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(5)
            }
        }
    }

    private fun performClickHaptic() {
        if (vibrationStrength == "off") return
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (vibrationStrength) {
                    "light" -> 50
                    "medium" -> 120
                    "strong" -> 200
                    else -> 120
                }
                v.vibrate(VibrationEffect.createOneShot(10, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(10)
            }
        }
    }

    private fun performHeavyHaptic() {
        if (vibrationStrength == "off") return
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (vibrationStrength) {
                    "light" -> 80
                    "medium" -> 180
                    "strong" -> 255
                    else -> 180
                }
                v.vibrate(VibrationEffect.createOneShot(20, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(20)
            }
        }
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Reset keyboard mode to normal when keyboard opens, but keep search text and results
        if (::shiftButton.isInitialized) {
            currentMode = KeyboardMode.NORMAL
            lastShiftTapTime = 0L
            updateKeyboardDisplay()
        }

        if (::gifRecycler.isInitialized) {
             val prefs = PreferenceManager.getDefaultSharedPreferences(this)
             val columns = prefs.getInt("gif_columns", 2).coerceAtMost(4)
             val layoutManager = gifRecycler.layoutManager as? StaggeredGridLayoutManager
             if (layoutManager != null && layoutManager.spanCount != columns) {
                 layoutManager.spanCount = columns
             }

              val livePreviews = prefs.getBoolean("live_previews", true)
              val insertLink = prefs.getBoolean("link_on_long_press", false)
              adapter.setPreferences(livePreviews, insertLink)

              vibrationStrength = prefs.getString("vibration_strength", "medium") ?: "medium"
         }
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.input_view, null)
        rootView = view

        searchInput = view.findViewById(R.id.search_input)
        clearButton = view.findViewById(R.id.clear_button)
        searchButton = view.findViewById(R.id.key_search)
        settingsButton = view.findViewById(R.id.settings_button)
        progressBar = view.findViewById(R.id.progress_bar)
        gifRecycler = view.findViewById(R.id.gif_recycler)
        shiftButton = view.findViewById(R.id.key_shift)
        symbolsButton = view.findViewById(R.id.key_symbols)

        // History UI
        historyContainer = view.findViewById(R.id.history_container)
        historyRecycler = view.findViewById(R.id.history_recycler)
        clearAllButton = view.findViewById(R.id.clear_all_button)

        // Setup history adapter
        historyAdapter = SearchHistoryAdapter(
            onItemClick = { query ->
                performClickHaptic()
                searchInput.setText(query)
                performSearch(query)
            },
            onDismissClick = { query ->
                performClickHaptic()
                historyDb.removeSearch(query)
                historyAdapter.removeItem(query)
                updateHistoryVisibility()
            }
        )
        historyRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        historyRecycler.adapter = historyAdapter

        // Hide keyboard when scrolling history
        historyRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if ((dy != 0 || dx != 0) && isSearchBarActive) {
                    deactivateSearchBar()
                }
            }
        })

        // Clear all button
        clearAllButton.setOnClickListener {
            performKeyHaptic()
            historyDb.clearAll()
            historyAdapter.clear()
            updateHistoryVisibility()
        }

        // Setup GIF adapter
        adapter = GifAdapter(
            onGifClick = { url ->
                performClickHaptic()
                commitGif(url)
            },
            onGifLongClick = { url ->
                performHeavyHaptic()
                commitGifUrl(url)
            }
        )

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        vibrationStrength = prefs.getString("vibration_strength", "medium") ?: "medium"
        val columns = prefs.getInt("gif_columns", 2).coerceAtMost(4)
        val layoutManager = StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL)
        gifRecycler.layoutManager = layoutManager
        gifRecycler.adapter = adapter
        val livePreviews = prefs.getBoolean("live_previews", true)
        val insertLink = prefs.getBoolean("link_on_long_press", false)
        adapter.setPreferences(livePreviews, insertLink)

        gifRecycler.setItemViewCacheSize(20)

        // Infinite scroll + hide keyboard on scroll
        gifRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Hide keyboard when scrolling
                if ((dy != 0 || dx != 0) && isSearchBarActive) {
                    deactivateSearchBar()
                }

                // Load more GIFs when near bottom
                if (dy > 0 && !isLoadingPage && hasMorePages) {
                    val totalItemCount = layoutManager.itemCount
                    val lastVisiblePositions = layoutManager.findLastVisibleItemPositions(null)
                    val lastVisiblePosition = lastVisiblePositions.maxOrNull() ?: 0

                    if (lastVisiblePosition >= totalItemCount - PREFETCH_THRESHOLD) {
                        loadMoreGifs()
                    }
                }
            }
        })

        // Search input text watcher - toggle history/GIF visibility
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                updateHistoryVisibility()
            }
        })

        // Clear button
        clearButton.setOnClickListener {
            performKeyHaptic()
            searchInput.text.clear()
            adapter.clearGifs()
            currentQuery = ""
            updateHistoryVisibility()
        }

        // Search button
        searchButton.setOnClickListener {
            performKeyHaptic()
            performSearch(searchInput.text.toString())
        }

        // Settings button
        settingsButton.setOnClickListener {
            performKeyHaptic()
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        // Switch keyboard button (in search bar)
        view.findViewById<ImageButton>(R.id.switch_button)?.setOnClickListener {
            performKeyHaptic()
            // Try to switch to previous IME, if not available show IME picker
            if (!switchToPreviousInputMethod()) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        // Wire up keyboard keys
        setupKeyboard(view)

        // Keyboard container and search bar
        keyboardContainer = view.findViewById(R.id.keyboard_container)
        searchBarContainer = view.findViewById(R.id.search_bar_container)
        searchOverlay = view.findViewById(R.id.search_overlay)

        // Tap on search bar to activate keyboard
        searchBarContainer.setOnClickListener {
            performKeyHaptic()
            activateSearchBar()
        }

        // Overlay click listener
        searchOverlay.setOnClickListener {
            performKeyHaptic()
            activateSearchBar()
        }

        // Ensure clicks on the non-clickable EditText also activate via parent,
        // but just in case focus changes directly:
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activateSearchBar()
            } else {
                // If we lost focus but search is active, decide if we should keep it active?
                // For now, let deactivateSearchBar handle explicit deactivation.
            }
        }

        // Tap on GIF results area to deactivate search bar
        gifRecycler.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN && isSearchBarActive) {
                deactivateSearchBar()
            }
            false // Don't consume the event
        }

        // Initial history visibility
        updateHistoryVisibility()

        // Start with keyboard hidden
        deactivateSearchBar()

        return view
    }

    private fun activateSearchBar() {
        if (!isSearchBarActive) {
            isSearchBarActive = true
            updateKeyboardVisibility()

            // Hide overlay to allow interaction with EditText
            searchOverlay.visibility = View.GONE

            // Enable focus and request it
            searchInput.isFocusable = true
            searchInput.isFocusableInTouchMode = true
            searchInput.requestFocus()

            // Move cursor to end ONLY on initial activation
            searchInput.setSelection(searchInput.text.length)

            // Notify tutorial
            TutorialEventBus.emit(TutorialEvent.SearchBarActivated)
        } else {
            // Already active. User might be moving cursor, so DO NOT force it to end.
            // Just ensure we have focus (e.g. if they tapped the container but not the text)
            if (!searchInput.hasFocus()) {
                searchInput.requestFocus()
            }
        }
    }

    private fun deactivateSearchBar() {
        if (isSearchBarActive) {
            isSearchBarActive = false
            updateKeyboardVisibility()

            // Disable focus and clear it
            searchInput.clearFocus()
            searchInput.isFocusable = false
            searchInput.isFocusableInTouchMode = false

            // Show overlay to capture clicks
            searchOverlay.visibility = View.VISIBLE
        }
    }

    private fun updateKeyboardVisibility() {
        keyboardContainer.visibility = if (isSearchBarActive) View.VISIBLE else View.GONE
        searchBarContainer.isActivated = isSearchBarActive
    }

    private fun updateHistoryVisibility() {
        val isEmpty = searchInput.text.isNullOrEmpty()
        if (isEmpty) {
            // Show history if there are items
            val history = historyDb.getHistory()
            if (history.isNotEmpty()) {
                historyAdapter.setHistory(history)
                historyContainer.visibility = View.VISIBLE
                gifRecycler.visibility = View.GONE
            } else {
                historyContainer.visibility = View.GONE
                gifRecycler.visibility = View.VISIBLE
            }
        } else {
            // Hide history, show GIF results
            historyContainer.visibility = View.GONE
            gifRecycler.visibility = View.VISIBLE
        }
    }

    private fun setupKeyboard(view: View) {
        // Helper function to set up key touch listeners that fire on ACTION_DOWN
        // Always returns true to fully consume touch events and prevent parent interference
        fun View.setKeyTouchListener(action: () -> Unit) {
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        action()
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL,
                    android.view.MotionEvent.ACTION_POINTER_UP -> {
                        v.isPressed = false
                    }
                }
                true // Always consume to prevent parent from stealing events
            }
        }

        // Letters mapping for normal and symbols mode
        val letterIds = listOf(
            R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r, R.id.key_t,
            R.id.key_y, R.id.key_u, R.id.key_i, R.id.key_o, R.id.key_p,
            R.id.key_a, R.id.key_s, R.id.key_d, R.id.key_f, R.id.key_g,
            R.id.key_h, R.id.key_j, R.id.key_k, R.id.key_l,
            R.id.key_z, R.id.key_x, R.id.key_c, R.id.key_v,
            R.id.key_b, R.id.key_n, R.id.key_m
        )

        val normalLetters = listOf(
            "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
            "a", "s", "d", "f", "g", "h", "j", "k", "l",
            "z", "x", "c", "v", "b", "n", "m"
        )

        val symbolsRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val symbolsRow2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")")
        val symbolsRow3 = listOf("*", "\"", "'", ":", ";", "!", "?")
        val symbolsChars = symbolsRow1 + symbolsRow2 + symbolsRow3

        // Store button references
        for (id in letterIds) {
            view.findViewById<Button>(id)?.let { letterButtons[id] = it }
        }

        // Setup letter key touch listeners - fire on ACTION_DOWN for fast response
        for ((index, id) in letterIds.withIndex()) {
            letterButtons[id]?.setKeyTouchListener {
                performKeyHaptic()
                val char = when (currentMode) {
                    KeyboardMode.SYMBOLS -> symbolsChars.getOrNull(index) ?: ""
                    KeyboardMode.SHIFTED, KeyboardMode.CAPS_LOCK -> normalLetters[index].uppercase()
                    KeyboardMode.NORMAL -> normalLetters[index]
                }
                // Insert char at cursor position
                val start = Math.max(searchInput.selectionStart, 0)
                val end = Math.max(searchInput.selectionEnd, 0)
                searchInput.text.replace(Math.min(start, end), Math.max(start, end), char)

                // Auto-return to normal after typing in shifted mode
                if (currentMode == KeyboardMode.SHIFTED) {
                    currentMode = KeyboardMode.NORMAL
                    updateKeyboardDisplay()
                }
            }
        }

        // Space key
        view.findViewById<Button>(R.id.key_space)?.setKeyTouchListener {
            performKeyHaptic()
            val start = Math.max(searchInput.selectionStart, 0)
            val end = Math.max(searchInput.selectionEnd, 0)
            searchInput.text.replace(Math.min(start, end), Math.max(start, end), " ")
        }

        // Comma key
        view.findViewById<Button>(R.id.key_comma)?.setKeyTouchListener {
            performKeyHaptic()
            val start = Math.max(searchInput.selectionStart, 0)
            val end = Math.max(searchInput.selectionEnd, 0)
            searchInput.text.replace(Math.min(start, end), Math.max(start, end), ",")
        }

        // Period key
        view.findViewById<Button>(R.id.key_period)?.setKeyTouchListener {
            performKeyHaptic()
            val start = Math.max(searchInput.selectionStart, 0)
            val end = Math.max(searchInput.selectionEnd, 0)
            searchInput.text.replace(Math.min(start, end), Math.max(start, end), ".")
        }

        // Shift key - tap for shift, double-tap for caps lock
        shiftButton.setKeyTouchListener {
            performKeyHaptic()
            val now = System.currentTimeMillis()

            when (currentMode) {
                KeyboardMode.NORMAL -> {
                    if (now - lastShiftTapTime < DOUBLE_TAP_DELAY_MS) {
                        // Double tap -> caps lock
                        currentMode = KeyboardMode.CAPS_LOCK
                    } else {
                        // Single tap -> shifted
                        currentMode = KeyboardMode.SHIFTED
                    }
                }
                KeyboardMode.SHIFTED -> {
                    if (now - lastShiftTapTime < DOUBLE_TAP_DELAY_MS) {
                        // Double tap from shifted -> caps lock
                        currentMode = KeyboardMode.CAPS_LOCK
                    } else {
                        // Tap again -> back to normal
                        currentMode = KeyboardMode.NORMAL
                    }
                }
                KeyboardMode.CAPS_LOCK -> {
                    // Tap to exit caps lock
                    currentMode = KeyboardMode.NORMAL
                }
                KeyboardMode.SYMBOLS -> {
                    // In symbols mode, shift shows more symbols (not implemented, go to normal)
                    currentMode = KeyboardMode.NORMAL
                }
            }

            lastShiftTapTime = now
            updateKeyboardDisplay()
        }

        // Symbols key - toggles between letters and symbols
        symbolsButton.setKeyTouchListener {
            performKeyHaptic()
            currentMode = if (currentMode == KeyboardMode.SYMBOLS) {
                KeyboardMode.NORMAL
            } else {
                KeyboardMode.SYMBOLS
            }
            updateKeyboardDisplay()
        }

        // Backspace key with long-press repeat
        view.findViewById<ImageButton>(R.id.key_backspace)?.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    performKeyHaptic()
                    deleteCharacter() // Delete immediately on press

                    // Start repeating delete after initial delay
                    backspaceRunnable = object : Runnable {
                        override fun run() {
                            performKeyHaptic()
                            deleteCharacter()
                            backspaceHandler.postDelayed(this, 50) // Repeat every 50ms
                        }
                    }
                    backspaceHandler.postDelayed(backspaceRunnable!!, 500) // Initial delay 500ms
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL,
                android.view.MotionEvent.ACTION_POINTER_UP -> {
                    v.isPressed = false
                    // Stop repeating
                    backspaceRunnable?.let { backspaceHandler.removeCallbacks(it) }
                    backspaceRunnable = null
                }
            }
            true
        }

        // Initialize display
        updateKeyboardDisplay()
    }

    private fun deleteCharacter() {
        val text = searchInput.text
        if (text.isNotEmpty()) {
            val start = Math.max(searchInput.selectionStart, 0)
            val end = Math.max(searchInput.selectionEnd, 0)

            if (start != end) {
                // Delete selection
                text.delete(Math.min(start, end), Math.max(start, end))
            } else if (start > 0) {
                // Delete character before cursor
                text.delete(start - 1, start)
            }
        }
    }

    private fun updateKeyboardDisplay() {
        val normalLetters = listOf(
            "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
            "a", "s", "d", "f", "g", "h", "j", "k", "l",
            "z", "x", "c", "v", "b", "n", "m"
        )

        val symbolsRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val symbolsRow2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")")
        val symbolsRow3 = listOf("*", "\"", "'", ":", ";", "!", "?")
        val symbolsChars = symbolsRow1 + symbolsRow2 + symbolsRow3

        val letterIds = listOf(
            R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r, R.id.key_t,
            R.id.key_y, R.id.key_u, R.id.key_i, R.id.key_o, R.id.key_p,
            R.id.key_a, R.id.key_s, R.id.key_d, R.id.key_f, R.id.key_g,
            R.id.key_h, R.id.key_j, R.id.key_k, R.id.key_l,
            R.id.key_z, R.id.key_x, R.id.key_c, R.id.key_v,
            R.id.key_b, R.id.key_n, R.id.key_m
        )

        // Update letter button labels
        for ((index, id) in letterIds.withIndex()) {
            letterButtons[id]?.text = when (currentMode) {
                KeyboardMode.SYMBOLS -> symbolsChars.getOrNull(index) ?: ""
                KeyboardMode.SHIFTED, KeyboardMode.CAPS_LOCK -> normalLetters[index].uppercase()
                KeyboardMode.NORMAL -> normalLetters[index]
            }
        }

        // Update shift button icon
        val shiftIcon = when (currentMode) {
            KeyboardMode.CAPS_LOCK -> R.drawable.ic_keyboard_shift_locked
            KeyboardMode.SHIFTED -> R.drawable.ic_keyboard_shift_filled
            else -> R.drawable.ic_keyboard_shift
        }
        shiftButton.setImageResource(shiftIcon)

        // Update symbols button text
        symbolsButton.text = if (currentMode == KeyboardMode.SYMBOLS) "ABC" else "?123"

        // Hide/show shift in symbols mode
        shiftButton.visibility = if (currentMode == KeyboardMode.SYMBOLS) View.INVISIBLE else View.VISIBLE
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return

        // Save to history
        historyDb.addSearch(query)

        // Hide keyboard after search
        deactivateSearchBar()

        searchJob?.cancel()
        currentQuery = query
        currentPage = 0
        hasMorePages = true

        adapter.clearAndReset()
        progressBar.visibility = View.VISIBLE
        isLoadingPage = true

        // Notify tutorial
        TutorialEventBus.emit(TutorialEvent.SearchPerformed)

        searchJob = scope.launch(Dispatchers.IO) {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@GifBoardService)
                val safeSearch = prefs.getString("safe_search", "active") ?: "active"

                val response = fetcher.fetchGifs(GoogleGifFetcher.GifSearchRequest(query, 0, safeSearch))
                val gifItems = GifAdapter.parseGifs(response)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    isLoadingPage = false

                    if (gifItems.isEmpty()) {
                        hasMorePages = false
                        adapter.setEndOfList(true)
                    } else {
                        currentPage = 1
                        adapter.setGifs(gifItems)

                        // Auto-prefetch if content doesn't fill the view (can't scroll)
                        gifRecycler.post {
                            checkAndPrefetchIfNeeded()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    isLoadingPage = false
                }
            }
        }
    }

    /**
     * Check if RecyclerView can scroll vertically. If not, auto-load more pages
     * until it can scroll or there are no more results.
     */
    private fun checkAndPrefetchIfNeeded() {
        if (!isLoadingPage && hasMorePages && !gifRecycler.canScrollVertically(1)) {
            loadMoreGifs()
        }
    }

    private fun loadMoreGifs() {
        if (currentQuery.isBlank() || isLoadingPage || !hasMorePages) return

        isLoadingPage = true
        adapter.setLoading(true)

        scope.launch(Dispatchers.IO) {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@GifBoardService)
                val safeSearch = prefs.getString("safe_search", "active") ?: "active"

                val response = fetcher.fetchGifs(GoogleGifFetcher.GifSearchRequest(currentQuery, currentPage, safeSearch))
                val gifItems = GifAdapter.parseGifs(response)

                withContext(Dispatchers.Main) {
                    adapter.setLoading(false)

                    if (gifItems.isEmpty()) {
                        hasMorePages = false
                        adapter.setEndOfList(true)
                    } else {
                        currentPage++
                        adapter.addGifs(gifItems)

                        // Continue prefetching if still not scrollable
                        gifRecycler.post {
                            checkAndPrefetchIfNeeded()
                        }
                    }

                    isLoadingPage = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load more failed", e)
                withContext(Dispatchers.Main) {
                    adapter.setLoading(false)
                    isLoadingPage = false
                }
            }
        }
    }

    private fun commitGif(contentUri: String) {
        val request = Request.Builder().url(contentUri).build()
        OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to download GIF", e)
                window.window?.decorView?.post { commitGifUrl(contentUri) }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful || response.body == null) {
                    Log.e(TAG, "Failed to download GIF: $response")
                    window.window?.decorView?.post { commitGifUrl(contentUri) }
                    return
                }

                val imagesDir = File(cacheDir, "images")
                if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                    Log.e(TAG, "Failed to create images directory")
                    return
                }
                val file = File(imagesDir, "${System.currentTimeMillis()}.gif")

                try {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: java.io.IOException) {
                    Log.e(TAG, "Failed to save GIF", e)
                    window.window?.decorView?.post { commitGifUrl(contentUri) }
                    return
                }

                val linkUri = Uri.parse(contentUri)
                window.window?.decorView?.post { doCommitContent("GIF", "image/gif", file, linkUri) }
            }
        })
    }

    private fun doCommitContent(description: String, mimeType: String, file: File, linkUri: Uri) {
        val editorInfo = currentInputEditorInfo ?: return
        val contentUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        val flag: Int
        if (Build.VERSION.SDK_INT >= 25) {
            flag = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        } else {
            flag = 0
            try {
                grantUriPermission(editorInfo.packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.e(TAG, "grantUriPermission failed", e)
            }
        }

        val inputContentInfo = InputContentInfoCompat(
            contentUri,
            ClipDescription(description, arrayOf(mimeType)),
            linkUri
        )

        var committed = false
        currentInputConnection?.let { ic ->
            committed = InputConnectionCompat.commitContent(ic, editorInfo, inputContentInfo, flag, null)
        }

        if (!committed) {
             commitGifUrl(linkUri.toString())
        }
    }

    private fun commitGifUrl(url: String) {
        currentInputConnection?.commitText(url, 1)
    }

    override fun onFinishInput() {
        super.onFinishInput()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
