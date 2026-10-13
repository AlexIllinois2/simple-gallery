package com.simplemobiletools.gallery.pro.activities

import android.os.Bundle
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.adapters.ManageFoldersAdapter
import com.simplemobiletools.gallery.pro.databinding.ActivityManageFoldersBinding
import com.simplemobiletools.gallery.pro.dialogs.DbPasswordDialog
import com.simplemobiletools.gallery.pro.extensions.config
import com.simplemobiletools.gallery.pro.helpers.FolderToDbConverter

class IncludedFoldersActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageFoldersBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateFolders()
        setupOptionsMenu()
        binding.manageFoldersToolbar.title = getString(R.string.include_folders)

        updateMaterialActivityViews(binding.manageFoldersCoordinator, binding.manageFoldersList, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.manageFoldersList, binding.manageFoldersToolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.manageFoldersToolbar, NavigationIcon.Arrow)
    }

    private fun updateFolders() {
        val folders = ArrayList<String>()
        config.includedFolders.mapTo(folders) { it }
        binding.manageFoldersPlaceholder.apply {
            text = getString(R.string.included_activity_placeholder)
            beVisibleIf(folders.isEmpty())
            setTextColor(getProperTextColor())
        }

        val adapter = ManageFoldersAdapter(this, folders, false, this, binding.manageFoldersList, allowConvertToDb = true, convertListener = { folder ->
            convertFolderToDb(folder)
        }) {}
        binding.manageFoldersList.adapter = adapter
    }

    private fun setupOptionsMenu() {
        binding.manageFoldersToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_folder -> addFolder()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun refreshItems() {
        updateFolders()
    }

    private fun addFolder() {
        showAddIncludedFolderDialog {
            updateFolders()
        }
    }

    // converts the given folder (with any nesting) into a sqlite db file, the original folder is kept
    private fun convertFolderToDb(folder: String) {
        ConfirmationDialog(this, getString(R.string.convert_folder_to_db_confirmation)) {
            DbPasswordDialog(this, showEncryptionOption = true) { password ->
                val progressDialog = getAlertDialogBuilder()
                    .setCancelable(false)
                    .setMessage(getString(R.string.converting_folder))
                    .create()
                progressDialog.show()

                ensureBackgroundThread {
                    FolderToDbConverter.convert(this, folder, password, onProgress = { processed, total ->
                        runOnUiThread {
                            progressDialog.setMessage("$processed / $total")
                        }
                    }) { success, dbPath ->
                        runOnUiThread {
                            progressDialog.dismiss()
                            if (success) {
                                ConfirmationDialog(this, getString(R.string.add_converted_db_to_included)) {
                                    config.addIncludedDB(dbPath)
                                }
                            } else {
                                toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                            }
                        }
                    }
                }
            }
        }
    }
}
