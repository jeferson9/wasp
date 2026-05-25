package com.waspbrowser.app

import android.content.Context
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * WaspDialogs - centralized dialog and toast utilities.
 * Eliminates copy-paste across 4+ Activities.
 */
object WaspDialogs {

    fun confirm(
        context: Context,
        title: String,
        message: String,
        confirmText: String = "Confirmar",
        isDangerous: Boolean = true,
        onConfirm: () -> Unit
    ) {
        val inflater = android.view.LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_wasp_confirm, null)

        val txtTitle   = dialogView.findViewById<TextView>(R.id.txtDialogTitle)
        val txtMessage = dialogView.findViewById<TextView>(R.id.txtDialogMessage)
        val btnCancel  = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        txtTitle.text   = title
        txtMessage.text = message
        btnConfirm.text = confirmText

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Dangerous action = confirm button is red (delete, remove)
        // Safe action = confirm is normal
        if (isDangerous) {
            btnConfirm.background = context.getDrawable(R.drawable.bg_wasp_button_danger)
            btnCancel.background  = context.getDrawable(R.drawable.bg_wasp_button_cancel)
        } else {
            btnConfirm.background = context.getDrawable(R.drawable.bg_wasp_button_cancel)
            btnCancel.background  = context.getDrawable(R.drawable.bg_wasp_button_cancel)
        }
        btnConfirm.backgroundTintList = null
        btnCancel.backgroundTintList  = null

        btnCancel.setOnClickListener  { dialog.dismiss() }
        btnConfirm.setOnClickListener { dialog.dismiss(); onConfirm() }

        dialog.show()
    }

    fun toast(context: Context, message: String) {
        WaspToast.show(context, message)
    }
}
