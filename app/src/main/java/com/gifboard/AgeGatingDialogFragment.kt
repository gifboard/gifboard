package com.gifboard

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.DatePicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.util.*

class AgeGatingDialogFragment : DialogFragment() {

    interface AgeGatingListener {
        fun onAgeVerified(isAdult: Boolean)
    }

    private var listener: AgeGatingListener? = null

    fun setListener(listener: AgeGatingListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_age_gating, null)
        
        val datePicker = view.findViewById<DatePicker>(R.id.date_picker)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnVerify = view.findViewById<Button>(R.id.btn_verify)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()

        btnCancel.setOnClickListener {
            listener?.onAgeVerified(false)
            dismiss()
        }

        btnVerify.setOnClickListener {
            val isAdult = checkAge(datePicker.year, datePicker.month, datePicker.dayOfMonth)
            listener?.onAgeVerified(isAdult)
            dismiss()
        }

        return dialog
    }

    private fun checkAge(year: Int, month: Int, day: Int): Boolean {
        val today = Calendar.getInstance()
        val birthDate = Calendar.getInstance()
        birthDate.set(year, month, day)

        var age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR)

        if (today.get(Calendar.DAY_OF_YEAR) < birthDate.get(Calendar.DAY_OF_YEAR)) {
            age--
        }

        return age >= 18
    }

    companion object {
        const val TAG = "AgeGatingDialog"
        fun newInstance() = AgeGatingDialogFragment()
    }
}
