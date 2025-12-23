package com.gifboard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("advanced_settings")?.setOnPreferenceClickListener {
            showAgeGatingDialog()
            true
        }

        findPreference<Preference>("restart_tutorial")?.setOnPreferenceClickListener {
            TutorialPreferences.resetTutorial(requireContext())
            startActivity(Intent(requireContext(), TutorialActivity::class.java))
            requireActivity().finish()
            true
        }

        findPreference<Preference>("check_updates")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/gifboard/gifboard/releases/latest"))
            startActivity(intent)
            true
        }
    }

    private fun showAgeGatingDialog() {
        val dialog = AgeGatingDialogFragment.newInstance()
        dialog.setListener(object : AgeGatingDialogFragment.AgeGatingListener {
            override fun onAgeVerified(isAdult: Boolean) {
                if (isAdult) {
                    navigateToAdvancedSettings()
                } else {
                    Toast.makeText(context, "You must be 18+ to access Advanced Settings.", Toast.LENGTH_LONG).show()
                }
            }
        })
        dialog.show(childFragmentManager, AgeGatingDialogFragment.TAG)
    }

    private fun navigateToAdvancedSettings() {
        parentFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, AdvancedSettingsFragment())
            .addToBackStack(null)
            .commit()
    }
}

