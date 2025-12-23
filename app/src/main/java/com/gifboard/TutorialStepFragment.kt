package com.gifboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.facebook.drawee.backends.pipeline.Fresco
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * Fragment for displaying a single tutorial step.
 */
class TutorialStepFragment : Fragment() {

    companion object {
        private const val ARG_STEP = "step"

        const val STEP_ENABLE = 0
        const val STEP_TRY = 1
        const val STEP_COMPLETE = 2

        fun newInstance(step: Int): TutorialStepFragment {
            return TutorialStepFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_STEP, step)
                }
            }
        }
    }

    /** Interactive sub-steps for the Try It Out step */
    private enum class InteractiveStep {
        TAP_INPUT,
        SWITCH_TO_GIFBOARD,
        TAP_SEARCH,
        SEARCH,
        TAP_GIF,
        SWITCH_BACK,
        SUCCESS
    }

    private var step: Int = STEP_ENABLE
    private var currentInteractiveStep = InteractiveStep.TAP_INPUT
    private val handler = Handler(Looper.getMainLooper())
    private var imeCheckRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = arguments?.getInt(ARG_STEP, STEP_ENABLE) ?: STEP_ENABLE
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tutorial_step, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val iconView = view.findViewById<ImageView>(R.id.step_icon)
        val titleView = view.findViewById<TextView>(R.id.step_title)
        val descriptionView = view.findViewById<TextView>(R.id.step_description)
        val actionButton = view.findViewById<MaterialButton>(R.id.step_action_button)
        val testInputLayout = view.findViewById<TextInputLayout>(R.id.test_input_layout)
        val testInput = view.findViewById<RichEditText>(R.id.test_input)
        val gifPreview = view.findViewById<AspectRatioDraweeView>(R.id.gif_preview)

        when (step) {
            STEP_ENABLE -> {
                iconView.setImageResource(R.mipmap.ic_launcher)
                titleView.setText(R.string.tutorial_enable_title)
                descriptionView.setText(R.string.tutorial_enable_desc)
                actionButton.visibility = View.VISIBLE
                actionButton.setText(R.string.tutorial_enable_action)
                actionButton.setOnClickListener {
                    openInputMethodSettings()
                }
                testInputLayout.visibility = View.GONE
                gifPreview.visibility = View.GONE
            }
            STEP_TRY -> {
                iconView.visibility = View.GONE
                titleView.visibility = View.GONE
                actionButton.visibility = View.GONE
                testInputLayout.visibility = View.VISIBLE
                gifPreview.visibility = View.GONE

                // Setup interactive tutorial
                setupInteractiveTutorial(descriptionView, testInput, gifPreview)
            }
            STEP_COMPLETE -> {
                iconView.setImageResource(R.drawable.ic_check_circle)
                titleView.setText(R.string.tutorial_complete_title)
                val completeText = getString(R.string.tutorial_complete_desc)
                descriptionView.text = IconSpanHelper.replaceIconPlaceholders(requireContext(), completeText)
                actionButton.visibility = View.GONE
                testInputLayout.visibility = View.GONE
                gifPreview.visibility = View.GONE
            }
        }
    }

    private fun setupInteractiveTutorial(
        descriptionView: TextView,
        testInput: RichEditText,
        gifPreview: AspectRatioDraweeView
    ) {
        // Reset state
        currentInteractiveStep = InteractiveStep.TAP_INPUT
        TutorialEventBus.isActive = true
        TutorialEventBus.reset()
        updatePrompt(descriptionView)

        // Step 1: Detect input focus
        testInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus && currentInteractiveStep == InteractiveStep.TAP_INPUT) {
                advanceTo(InteractiveStep.SWITCH_TO_GIFBOARD, descriptionView)
                startImePolling(descriptionView, gifPreview)
            } else if (!hasFocus && currentInteractiveStep.ordinal > InteractiveStep.TAP_INPUT.ordinal 
                       && currentInteractiveStep != InteractiveStep.SUCCESS) {
                // Focus lost after step 1 (e.g., during IME switch) - re-request focus
                v.post { v.requestFocus() }
            }
        }

        // Step 5: Detect GIF received (allow multiple insertions)
        testInput.onContentReceived = { contentUri, linkUri ->
            displayReceivedGif(gifPreview, contentUri, linkUri)
            if (currentInteractiveStep == InteractiveStep.TAP_GIF) {
                advanceTo(InteractiveStep.SWITCH_BACK, descriptionView)
            }
        }

        // Observe events from GifBoard
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TutorialEventBus.events.collect { event ->
                    when (event) {
                        is TutorialEvent.SearchBarActivated -> {
                            if (currentInteractiveStep == InteractiveStep.TAP_SEARCH) {
                                advanceTo(InteractiveStep.SEARCH, descriptionView)
                            }
                        }
                        is TutorialEvent.SearchPerformed -> {
                            if (currentInteractiveStep == InteractiveStep.SEARCH) {
                                advanceTo(InteractiveStep.TAP_GIF, descriptionView)
                            }
                        }
                        null -> { /* ignore */ }
                    }
                }
            }
        }
    }

    private fun startImePolling(descriptionView: TextView, gifPreview: AspectRatioDraweeView) {
        imeCheckRunnable = object : Runnable {
            override fun run() {
                val ctx = context ?: return
                val isGifIme = InputMethodUtils.isThisImeCurrent(ctx)

                when (currentInteractiveStep) {
                    InteractiveStep.SWITCH_TO_GIFBOARD -> {
                        if (isGifIme) {
                            advanceTo(InteractiveStep.TAP_SEARCH, descriptionView)
                        }
                    }
                    InteractiveStep.SWITCH_BACK -> {
                        if (!isGifIme) {
                            advanceTo(InteractiveStep.SUCCESS, descriptionView)
                            TutorialEventBus.isActive = false
                            return // Stop polling
                        }
                    }
                    else -> { /* Continue polling */ }
                }

                // Continue polling every 500ms
                if (currentInteractiveStep != InteractiveStep.SUCCESS) {
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.post(imeCheckRunnable!!)
    }

    private fun advanceTo(newStep: InteractiveStep, descriptionView: TextView) {
        currentInteractiveStep = newStep
        updatePrompt(descriptionView)
        
        // Notify activity when tutorial is complete
        if (newStep == InteractiveStep.SUCCESS) {
            (activity as? TutorialActivity)?.onInteractiveTutorialComplete()
        }
    }

    private fun updatePrompt(descriptionView: TextView) {
        val promptRes = when (currentInteractiveStep) {
            InteractiveStep.TAP_INPUT -> R.string.tutorial_step_tap_input
            InteractiveStep.SWITCH_TO_GIFBOARD -> R.string.tutorial_step_switch_to_gifboard
            InteractiveStep.TAP_SEARCH -> R.string.tutorial_step_tap_search
            InteractiveStep.SEARCH -> R.string.tutorial_step_search
            InteractiveStep.TAP_GIF -> R.string.tutorial_step_tap_gif
            InteractiveStep.SWITCH_BACK -> R.string.tutorial_step_switch_back
            InteractiveStep.SUCCESS -> R.string.tutorial_step_success
        }
        val text = getString(promptRes)
        descriptionView.text = IconSpanHelper.replaceIconPlaceholders(requireContext(), text)
    }

    private fun displayReceivedGif(gifPreview: AspectRatioDraweeView, contentUri: Uri, linkUri: Uri?) {
        gifPreview.visibility = View.VISIBLE
        val displayUri = linkUri ?: contentUri

        val listener = object : com.facebook.drawee.controller.BaseControllerListener<com.facebook.imagepipeline.image.ImageInfo>() {
            override fun onFinalImageSet(
                id: String?,
                imageInfo: com.facebook.imagepipeline.image.ImageInfo?,
                animatable: android.graphics.drawable.Animatable?
            ) {
                imageInfo?.let {
                    gifPreview.aspectRatio = it.width.toFloat() / it.height.toFloat()
                }
                animatable?.start()
            }
        }

        val controller = Fresco.newDraweeControllerBuilder()
            .setUri(displayUri)
            .setAutoPlayAnimations(true)
            .setOldController(gifPreview.controller)
            .setControllerListener(listener)
            .build()
        gifPreview.controller = controller

        Toast.makeText(requireContext(), "GIF received! ✓", Toast.LENGTH_SHORT).show()
    }

    private fun openInputMethodSettings() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up
        imeCheckRunnable?.let { handler.removeCallbacks(it) }
        TutorialEventBus.isActive = false
        TutorialEventBus.reset()
    }
}
