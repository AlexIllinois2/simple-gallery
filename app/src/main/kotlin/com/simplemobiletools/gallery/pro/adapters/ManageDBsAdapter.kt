package com.simplemobiletools.gallery.pro.adapters

import android.view.*
import android.widget.PopupMenu
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.getPopupMenuTheme
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.gallery.pro.databases.GalleryDbStore
import com.simplemobiletools.gallery.pro.databinding.ItemManageFolderBinding
import com.simplemobiletools.gallery.pro.extensions.config

class ManageDBsAdapter(
    activity: BaseSimpleActivity, var dbs: ArrayList<String>, val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private val config = activity.config

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = com.simplemobiletools.commons.R.menu.cab_remove_only

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        when (id) {
            com.simplemobiletools.commons.R.id.cab_remove -> removeSelection()
        }
    }

    override fun getSelectableItemCount() = dbs.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = dbs.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = dbs.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemManageFolderBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val db = dbs[position]
        holder.bindView(db, true, true) { itemView, adapterPosition ->
            setupView(itemView, db)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = dbs.size

    private fun getSelectedItems() = dbs.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<String>

    private fun setupView(view: View, db: String) {
        ItemManageFolderBinding.bind(view).apply {
            root.setupViewBackground(activity)
            manageFolderHolder.isSelected = selectedKeys.contains(db.hashCode())
            manageFolderTitle.apply {
                text = db
                setTextColor(context.getProperTextColor())
            }

            overflowMenuIcon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }

            overflowMenuIcon.setOnClickListener {
                showPopupMenu(overflowMenuAnchor, db)
            }
        }
    }

    private fun showPopupMenu(view: View, db: String) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            setOnMenuItemClickListener { item ->
                val eventTypeId = db.hashCode()
                when (item.itemId) {
                    com.simplemobiletools.commons.R.id.cab_remove -> {
                        executeItemMenuOperation(eventTypeId) {
                            removeSelection()
                        }
                    }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(eventTypeId: Int, callback: () -> Unit) {
        selectedKeys.clear()
        selectedKeys.add(eventTypeId)
        callback()
    }

    private fun removeSelection() {
        val removeDbs = ArrayList<String>(selectedKeys.size)
        val positions = getSelectedItemPositions()

        getSelectedItems().forEach {
            removeDbs.add(it)
            GalleryDbStore.close(it)
            config.removeIncludedDB(it)
        }

        dbs.removeAll(removeDbs)
        removeSelectedItems(positions)
        if (dbs.isEmpty()) {
            listener?.refreshItems()
        }
    }
}
