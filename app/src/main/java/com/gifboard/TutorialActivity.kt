package com.gifboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

class TutorialActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnSkip: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var stepIndicators: LinearLayout

    private val totalSteps = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply Material You dynamic colors
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        
        // Initialize Fresco BEFORE inflating views that use SimpleDraweeView
        GifImageLoader.initialize(this)
        
        setContentView(R.layout.activity_tutorial)

        viewPager = findViewById(R.id.tutorial_viewpager)
        btnSkip = findViewById(R.id.btn_skip)
        btnNext = findViewById(R.id.btn_next)
        stepIndicators = findViewById(R.id.step_indicators)

        setupViewPager()
        setupStepIndicators()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        // Recheck IME status when returning from settings
        updateNextButtonState()
    }

    private fun setupViewPager() {
        viewPager.adapter = TutorialPagerAdapter(this)
        // Disable swipe to prevent skipping required steps
        viewPager.isUserInputEnabled = false
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUI(position)
            }
        })
    }

    private fun setupStepIndicators() {
        stepIndicators.removeAllViews()
        for (i in 0 until totalSteps) {
            val indicator = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                    marginStart = 8
                    marginEnd = 8
                }
                setImageResource(R.drawable.indicator_dot)
                alpha = if (i == 0) 1.0f else 0.3f
            }
            stepIndicators.addView(indicator)
        }
    }

    private fun updateStepIndicators(position: Int) {
        for (i in 0 until stepIndicators.childCount) {
            val indicator = stepIndicators.getChildAt(i) as ImageView
            indicator.alpha = if (i == position) 1.0f else 0.3f
        }
    }

    private fun setupButtons() {
        btnSkip.setOnClickListener {
            completeTutorial()
        }

        btnNext.setOnClickListener {
            val currentItem = viewPager.currentItem
            if (currentItem < totalSteps - 1) {
                viewPager.currentItem = currentItem + 1
            } else {
                completeTutorial()
            }
        }
    }

    private fun updateUI(position: Int) {
        updateStepIndicators(position)

        // Update button text and visibility based on position
        when (position) {
            TutorialStepFragment.STEP_ENABLE -> {
                // First step: no skip (IME must be enabled)
                btnNext.setText(R.string.tutorial_next)
                btnSkip.visibility = View.GONE
            }
            TutorialStepFragment.STEP_COMPLETE -> {
                // Last step: settings button, no skip
                btnNext.setText(R.string.tutorial_settings)
                btnSkip.visibility = View.GONE
            }
            else -> {
                // Middle steps: show skip
                btnNext.setText(R.string.tutorial_next)
                btnSkip.visibility = View.VISIBLE
            }
        }

        updateNextButtonState()
    }

    /** Called by TutorialStepFragment when interactive tutorial is complete */
    fun onInteractiveTutorialComplete() {
        interactiveTutorialComplete = true
        updateNextButtonState()
    }

    private var interactiveTutorialComplete = false

    private fun updateNextButtonState() {
        val currentPosition = viewPager.currentItem

        when (currentPosition) {
            TutorialStepFragment.STEP_ENABLE -> {
                // Must enable IME before proceeding
                val isEnabled = InputMethodUtils.isThisImeEnabled(this)
                btnNext.isEnabled = isEnabled
                btnNext.alpha = if (isEnabled) 1.0f else 0.5f
            }
            TutorialStepFragment.STEP_TRY -> {
                // Must complete interactive tutorial before proceeding
                btnNext.isEnabled = interactiveTutorialComplete
                btnNext.alpha = if (interactiveTutorialComplete) 1.0f else 0.5f
            }
            else -> {
                btnNext.isEnabled = true
                btnNext.alpha = 1.0f
            }
        }
    }

    private fun completeTutorial() {
        TutorialPreferences.setTutorialCompleted(this, true)
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }

    private inner class TutorialPagerAdapter(activity: AppCompatActivity) : 
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = totalSteps

        override fun createFragment(position: Int): Fragment {
            return TutorialStepFragment.newInstance(position)
        }
    }
}
