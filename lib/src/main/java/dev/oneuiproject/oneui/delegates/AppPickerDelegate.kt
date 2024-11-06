@file:Suppress("NOTHING_TO_INLINE")

package dev.oneuiproject.oneui.delegates

import android.annotation.SuppressLint
import android.content.Context
import android.widget.CompoundButton
import android.widget.ImageButton
import androidx.apppickerview.widget.AbsAdapter
import androidx.apppickerview.widget.AppPickerView
import androidx.collection.mutableScatterSetOf
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import dev.oneuiproject.oneui.design.R
import dev.oneuiproject.oneui.ktx.MultiSelectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Convenience class to handle [AppPickerView] ops.
 */
class AppPickerDelegate : AppPickerOp, AppPickerView.OnBindListener{

    private var mListType: Int? = null
    private lateinit var mGetCurrentList: (() -> ArrayList<String>)
    private val mSelectedItems = mutableScatterSetOf<CharSequence>()
    private var mIsInitialized = AtomicBoolean(false)
    private var mOnItemClicked: ((pos: Int, packageName: CharSequence, appLabel:CharSequence) -> Unit)? = null
    private var mOnItemCheckChange: ((pos: Int, packageName: CharSequence, appLabel:CharSequence, isChecked: Boolean) -> Unit)? = null
    private var mOnItemActionClicked: ((pos: Int, packageName: CharSequence, appLabel:CharSequence) -> Unit)? = null


    private var mOnSelectAllChange: ((selectAllButton: CompoundButton, isChecked: Boolean) -> Unit)? = null
    private var mOnLongClicked: ((pos: Int, packageName: CharSequence, appLabel:CharSequence) -> Unit)? = null
    private lateinit var mContext: Context
    private lateinit var mAppPickerView: AppPickerView

    override fun AppPickerView.configure(
        onGetCurrentList: (() -> ArrayList<String>)?,
        onItemClicked: ((position: Int, packageName: CharSequence, appLabel: CharSequence) -> Unit)?,
        onItemCheckChanged: ((position: Int, packageName: CharSequence, appLabel: CharSequence, isChecked: Boolean) -> Unit)?,
        onSelectAllChanged: ((selectAllButton: CompoundButton, isChecked: Boolean) -> Unit)?,
        onItemActionClicked: ((pos: Int, packageName: CharSequence, appLabel: CharSequence) -> Unit)?,
        onLongClick: ((position: Int, packageName: CharSequence, appLabel: CharSequence) -> Unit)?
    ) {
        mAppPickerView = this@configure
        mContext = mAppPickerView.context
        mOnItemClicked = onItemClicked
        mOnItemCheckChange = onItemCheckChanged
        mOnItemActionClicked = onItemActionClicked
        mOnLongClicked = onLongClick
        mOnSelectAllChange = onSelectAllChanged
        mGetCurrentList = onGetCurrentList ?: { AppPickerView.getInstalledPackages(mContext) as ArrayList<String>}
    }


    @SuppressLint("RestrictedApi")
    override fun refreshAppList() {
        with(mAppPickerView) {
            if (!mIsInitialized.getAndSet(true) || mAppPickerView.adapter == null) {
                clearItemDecorations()
                setAppPickerView(
                    mListType ?: AppPickerView.TYPE_LIST,
                    mGetCurrentList(),
                    AppPickerView.ORDER_ASCENDING_IGNORE_CASE
                )
                setOnBindListener(this@AppPickerDelegate)
            } else {
                mAppPickerView.resetPackages(mGetCurrentList())
            }
        }
    }


    override fun setListType(@AppPickerView.AppPickerType listType: Int) {
        if (mListType == listType) return
        mListType = listType
        mIsInitialized.set(false)
        refreshAppList()
    }

    override fun clearSelectedItems() {
        mSelectedItems.clear()
    }

    /**
     * Called internally by AppPickerView adapter
     */
    override fun onBindViewHolder(
        holder: AppPickerView.ViewHolder,
        position: Int, packageName: String
    ) {
        when (mListType) {
            AppPickerView.TYPE_LIST -> doOnBind( position, packageName, holder, null)
            AppPickerView.TYPE_LIST_ACTION_BUTTON -> doOnBind( position, packageName, holder, holder.actionButton)
            AppPickerView.TYPE_GRID -> doOnBind( position, packageName, holder, null)
            AppPickerView.TYPE_LIST_CHECKBOX -> doOnBindCheckable( holder.checkBox!!, position, packageName, holder)
            AppPickerView.TYPE_LIST_CHECKBOX_WITH_ALL_APPS -> doOnBindCheckableWithAll(holder.checkBox!!, position, packageName, holder)
            AppPickerView.TYPE_LIST_RADIOBUTTON -> doOnBindCheckable( holder.radioButton!!, position, packageName, holder)
            AppPickerView.TYPE_LIST_SWITCH -> doOnBindCheckable( holder.switch!!, position, packageName, holder)
            AppPickerView.TYPE_LIST_SWITCH_WITH_ALL_APPS -> doOnBindCheckableWithAll(holder.switch!!, position, packageName, holder)
            AppPickerView.TYPE_GRID_CHECKBOX -> doOnBindCheckOnClick( holder.checkBox!!, position, packageName, holder)
        }
    }

    private inline fun AppPickerView.clearItemDecorations(){
        if (itemDecorationCount > 0) {
            for (i in 0 until itemDecorationCount) {
                removeItemDecorationAt(i)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun getAbsAdapter(): AbsAdapter{
        return (mAppPickerView.adapter!! as AbsAdapter)
    }


    private fun doOnBindCheckableWithAll(switchWidget: CompoundButton, position: Int,
                                                packageName: CharSequence, holder: AppPickerView.ViewHolder) {
        if (position == 0) {
            if (AppPickerView.ALL_APPS_STRING != packageName) return
            holder.appLabel.text = mContext.getString(R.string.oui_action_mode_all_checkbox)
            switchWidget.apply {
                isChecked = mSelectedItems.size == mGetCurrentList().size
                holder.itemView.setOnClickListener {
                    isChecked = !isChecked
                    if (isChecked) {
                        mSelectedItems.addAll(mGetCurrentList())
                    } else {
                        mSelectedItems.clear()
                    }
                    mAppPickerView.refreshUI()
                    mOnSelectAllChange?.invoke(switchWidget, isChecked)
                }
            }
        } else {
            with(switchWidget) {
                isChecked = mSelectedItems.contains(packageName)
                setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                    mOnItemCheckChange?.invoke(
                        position,
                        packageName,
                        holder.appLabel.text.toString(),
                        isChecked
                    )
                }
                holder.itemView.setOnClickListener {
                    isChecked = !isChecked
                    if (isChecked) {
                        if (mSelectedItems.add(packageName)) {
                            if (mSelectedItems.size == mGetCurrentList().size) {
                                mAppPickerView.refreshUI(0)
                            }
                        }
                    } else {
                        val refreshAll = mSelectedItems.size == mGetCurrentList().size
                        if (mSelectedItems.remove(packageName)){
                            if (refreshAll) mAppPickerView.refreshUI(0)
                        }
                    }
                }
                updateAllSelectorState()
            }

        }
    }

    private fun doOnBindCheckable(switchWidget: CompoundButton, position: Int,
                                         packageName: CharSequence, holder: AppPickerView.ViewHolder) {
        with(switchWidget) {
            isChecked = mSelectedItems.contains(packageName)
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    mSelectedItems.add(packageName)
                } else {
                    mSelectedItems.remove(packageName)
                }
                mAppPickerView.refreshUI(position)
                mOnItemCheckChange?.invoke(
                    position,
                    packageName,
                    holder.appLabel.text.toString(),
                    isChecked
                )
            }
        }
    }

    private inline fun doOnBind(position: Int, packageName: CharSequence, holder: AppPickerView.ViewHolder,
                                actionButton: ImageButton?) {
        holder.itemView.setOnClickListener {
            mOnItemClicked?.invoke(position, packageName, holder.appLabel.text.toString())
        }
        actionButton?.setOnClickListener {
            mOnItemActionClicked?.invoke(position, packageName, holder.appLabel.text.toString())
        }
    }

    private fun doOnBindCheckOnClick(switchWidget: CompoundButton, position: Int,
                                            packageName: CharSequence, holder: AppPickerView.ViewHolder) {
        with(switchWidget) {
            isChecked = mSelectedItems.contains(packageName)
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    mSelectedItems.add(packageName)
                } else {
                    mSelectedItems.remove(packageName)
                }
                mAppPickerView.refreshUI(position)
                mOnItemCheckChange?.invoke(
                    position,
                    packageName,
                    holder.appLabel.text.toString(),
                    isChecked
                )
            }
            holder.itemView.setOnClickListener {
                isChecked = !isChecked
            }
        }
    }

    private var firstPosition: Int = NO_POSITION
    private var lastPosition: Int = NO_POSITION
    private var sessionMax = NO_POSITION
    private var sessionMin = NO_POSITION
    private var allSelectedStateFlowUpdaterJob: Job? = null

    private val _allSelectedStateFlow = MutableStateFlow(AllSelectorState())
    /**
     * StateFlow holding the latest [AllSelectorState]. This can be subscribed for updating
     * UI components outside [AppPickerView]
     */
    val allSelectedStateFlow = _allSelectedStateFlow.asStateFlow()

    @SuppressLint("RestrictedApi")
    private fun onStateChanged(state: MultiSelectionState, position: Int) {
        when (state) {
            MultiSelectionState.STARTED -> {
                firstPosition = position
                lastPosition = position
                sessionMax = position
                sessionMin = position
                val packageName = getAbsAdapter().getAppInfo(position).packageName
                if (mSelectedItems.add(packageName)) {
                    mAppPickerView.adapter!!.notifyItemChanged(position)
                    updateAllSelectorState()
                }
            }

            MultiSelectionState.ENDED -> {
                firstPosition = NO_POSITION
                lastPosition = NO_POSITION
                sessionMax = NO_POSITION
                sessionMin = NO_POSITION
            }
        }
    }


    @SuppressLint("RestrictedApi")
    private inline fun onSelectItem(position: Int) {
        //Note: Avoid using minOf()/maxOf() with 4 or more params -
        //https://www.romainguy.dev/posts/2024/micro-optimizations-in-kotlin-3/
        sessionMax = maxOf(lastPosition, firstPosition, sessionMax)
        sessionMin = minOf(firstPosition, lastPosition, sessionMin)

        lastPosition = position

        val absAdapter = getAbsAdapter()
        val packageName = absAdapter.getAppInfo(position).packageName

        if (position in sessionMin..sessionMax) {
            if (!mSelectedItems.remove(packageName)) {
                mSelectedItems.add(packageName)
            }
            mAppPickerView.adapter!!.notifyItemChanged(position)
            if (AppPickerView.ALL_APPS_STRING == absAdapter.getAppInfo(0).packageName) {
                mAppPickerView.refreshUI(0)
            }
            updateAllSelectorState()
        } else {
            if (mSelectedItems.add(packageName)) {
                mAppPickerView.adapter!!.notifyItemChanged(position)
                if (AppPickerView.ALL_APPS_STRING == absAdapter.getAppInfo(0).packageName) {
                    mAppPickerView.refreshUI(0)
                }
                updateAllSelectorState()
            }
        }
    }

    private fun updateAllSelectorState(){
        val toJoin = allSelectedStateFlowUpdaterJob?.apply { cancel() }
        allSelectedStateFlowUpdaterJob = CoroutineScope(Dispatchers.Main).launch {
            toJoin?.join()// Don't let two copies of this run concurrently
            _allSelectedStateFlow.value = getActionModeAllSelectorState()
        }
    }

    private fun getActionModeAllSelectorState(): AllSelectorState {
        val currentList = mGetCurrentList()
        val currentDataSetCount = currentList.size
        val isEnabled = currentDataSetCount > 0
        val selectedIds = mSelectedItems
        val allSelected = if (currentDataSetCount == 0) null else {
            selectedIds.count { it in currentList } >= currentDataSetCount
        }
        return AllSelectorState(
            selectedIds.size,
            isChecked = allSelected,
            isEnabled = isEnabled
        )
    }
}


interface AppPickerOp{
    /**
     * Should be called on [Activity.onCreate] or [Fragment.onViewCreated]
     *
     * @param onGetCurrentList Lambda function to provide own app list.
     * If not set or set to `null`, it will use the [default implementation][AppPickerView.getInstalledPackages].
     * @param onItemClicked Lambda function to handle item click.
     * @param onItemCheckChanged Lambda function to handle item check change.
     * @param onItemActionClicked Lambda function to handle item action click.
     * @param onLongClick Lambda function to handle item long click.
     * @param enableLongPressMultiSelection Enable long press multi selection.
     */
    fun AppPickerView.configure(
        /**
         * Set lambda to provide own app list. If not set or set to `null`, it will use the [default implementation]
         * [AppPickerView.getInstalledPackages]
         */
        onGetCurrentList: (() -> ArrayList<String>)? = null,
        onItemClicked: ((position: Int, packageName: CharSequence, appLabel:CharSequence) -> Unit)? = null,
        onItemCheckChanged: ((position: Int, packageName: CharSequence, appLabel:CharSequence, isChecked: Boolean) -> Unit)? = null,
        onSelectAllChanged: ((selectAllButton: CompoundButton, isChecked: Boolean) -> Unit)? = null,
        onItemActionClicked: ((pos: Int, packageName: CharSequence, appLabel:CharSequence) -> Unit)? = null,
        onLongClick: ((position: Int, packageName: CharSequence, appLabel: CharSequence) -> Unit)? = null
    )
    /**
     * Updates the apps list. This is also called internally when [AppPickerView.AppPickerType] is updated by [setListType].
     */
    fun refreshAppList()
    /**
     * Sets the [AppPickerView.AppPickerType] to use.
     */
    fun setListType(listType: Int)

    fun clearSelectedItems()
}