package com.simplemobiletools.gallery.pro.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.value
import com.simplemobiletools.gallery.pro.databinding.DialogDbPasswordBinding

/**
 * Password prompt used in two modes:
 *  - default: ask for the password of an existing encrypted sqlite db
 *  - showEncryptionOption = true: let the user pick between an unencrypted db and a password encrypted one
 *    (used by the folder -> db conversion), the callback gets an empty password when encryption is unchecked
 */
class DbPasswordDialog(
    val activity: BaseSimpleActivity,
    val showEncryptionOption: Boolean = false,
    val callback: (password: String) -> Unit
) {
    init {
        val binding = DialogDbPasswordBinding.inflate(activity.layoutInflater).apply {
            dbPasswordCheckbox.beVisibleIf(showEncryptionOption)
            dbPasswordCheckbox.setOnCheckedChangeListener { _, checked ->
                dbPasswordHint.isEnabled = checked
                if (checked) {
                    dbPasswordValue.requestFocus()
                }
            }
            if (showEncryptionOption) {
                dbPasswordHint.isEnabled = false
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    alertDialog.showKeyboard(binding.dbPasswordValue)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val password = binding.dbPasswordValue.value
                        if (showEncryptionOption && !binding.dbPasswordCheckbox.isChecked) {
                            callback("")
                            alertDialog.dismiss()
                            return@setOnClickListener
                        }

                        if (password.isEmpty()) {
                            activity.toast(com.simplemobiletools.commons.R.string.empty_password)
                            return@setOnClickListener
                        }

                        callback(password)
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
