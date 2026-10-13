package com.simplemobiletools.gallery.pro.activities

import android.os.Bundle
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.adapters.ManageDBsAdapter
import com.simplemobiletools.gallery.pro.databases.GalleryDbStore
import com.simplemobiletools.gallery.pro.databinding.ActivityManageFoldersBinding
import com.simplemobiletools.gallery.pro.dialogs.DbPasswordDialog
import com.simplemobiletools.gallery.pro.extensions.config

// lets the user pick sqlite db files that should act like regular gallery folders
class IncludedDBsActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageFoldersBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateDbs()
        setupOptionsMenu()
        binding.manageFoldersToolbar.title = getString(R.string.manage_included_dbs)

        updateMaterialActivityViews(binding.manageFoldersCoordinator, binding.manageFoldersList, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.manageFoldersList, binding.manageFoldersToolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.manageFoldersToolbar, NavigationIcon.Arrow)
    }

    private fun updateDbs() {
        val dbs = ArrayList<String>()
        config.includedDBs.mapTo(dbs) { it }
        binding.manageFoldersPlaceholder.apply {
            text = getString(R.string.included_dbs_activity_placeholder)
            beVisibleIf(dbs.isEmpty())
            setTextColor(getProperTextColor())
        }

        val adapter = ManageDBsAdapter(this, dbs, this, binding.manageFoldersList) {}
        binding.manageFoldersList.adapter = adapter
    }

    private fun setupOptionsMenu() {
        binding.manageFoldersToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_folder -> addDb()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun refreshItems() {
        updateDbs()
    }

    private fun addDb() {
        FilePickerDialog(this, config.internalStoragePath, pickFile = true, showHidden = false, showFAB = false, canAddShowHiddenButton = false) { path ->
            tryAddDb(path)
        }
    }

    private fun tryAddDb(path: String) {
        ensureBackgroundThread {
            if (GalleryDbStore.isValidGalleryDb(applicationContext, path, "")) {
                config.addIncludedDB(path)
                runOnUiThread {
                    updateDbs()
                }
            } else {
                runOnUiThread {
                    DbPasswordDialog(this) { password ->
                        ensureBackgroundThread {
                            if (GalleryDbStore.isValidGalleryDb(applicationContext, path, password)) {
                                config.setDBPassword(path, password)
                                config.addIncludedDB(path)
                                runOnUiThread {
                                    updateDbs()
                                }
                            } else {
                                toast(R.string.invalid_db_file)
                            }
                        }
                    }
                }
            }
        }
    }
}
