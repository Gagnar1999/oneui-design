package dev.oneuiproject.oneuiexample.ui.fragment.contacts

import android.content.Context
import android.content.res.Configuration
import android.database.MatrixCursor
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.appcompat.view.menu.SeslMenuItem
import androidx.core.view.MenuProvider
import androidx.indexscroll.widget.SeslCursorIndexer
import androidx.indexscroll.widget.SeslIndexScrollView
import androidx.indexscroll.widget.SeslIndexScrollView.OnIndexBarEventListener
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sec.sesl.tester.R
import dev.oneuiproject.oneui.utils.IndexScrollUtils
import dev.oneuiproject.oneuiexample.data.ContactsRepo
import dev.oneuiproject.oneuiexample.ui.core.base.BaseFragment
import dev.oneuiproject.oneuiexample.ui.fragment.contacts.adapter.ContactsAdapter

class ContactsFragment : BaseFragment() {
    private var mCurrentSectionIndex = 0
    private var mIndexScrollView: SeslIndexScrollView? = null
    private var mContactsListRv: RecyclerView? = null

    private var mIsTextModeEnabled = false
    private var mIsIndexBarPressed = false
    private val mHideIndexBar = Runnable { IndexScrollUtils.animateVisibility(mIndexScrollView!!, false) }

    private lateinit var contactsAdapter: ContactsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contactsAdapter = ContactsAdapter(mContext)
        mIndexScrollView = view.findViewById(R.id.indexscroll_view)
        initRecyclerView(view)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) {
            requireActivity().addMenuProvider(
                menuProvider,
                viewLifecycleOwner,
                Lifecycle.State.STARTED
            )
        } else {
            requireActivity().removeMenuProvider(menuProvider)
        }
    }

    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_contacts_list, menu)

            val menuItem = menu.findItem(R.id.menu_indexscroll_text)
            (menuItem as SeslMenuItem).badgeText = "1"
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menu_indexscroll_text) {
                mIsTextModeEnabled = !mIsTextModeEnabled
                if (mIsTextModeEnabled) {
                    menuItem.setTitle("Hide letters")
                } else {
                    menuItem.setTitle("Show letters")
                }
                (menuItem as SeslMenuItem).badgeText = null
                mIndexScrollView!!.apply {
                    setIndexBarTextMode(mIsTextModeEnabled)
                    invalidate()
                }
                return true
            }
            return false
        }
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val isRtl = newConfig
            .layoutDirection == View.LAYOUT_DIRECTION_RTL
        if (mIndexScrollView != null) {
            mIndexScrollView!!.setIndexBarGravity(
                if (isRtl)
                    SeslIndexScrollView.GRAVITY_INDEX_BAR_LEFT
                else
                    SeslIndexScrollView.GRAVITY_INDEX_BAR_RIGHT
            )
        }
    }

    override fun getLayoutResId(): Int = R.layout.fragment_contacts

    override fun getIconResId(): Int = dev.oneuiproject.oneui.R.drawable.ic_oui_contact_outline

    override fun getTitle(): CharSequence = "Contacts"

    private fun initRecyclerView(view: View) {
        val contactsList = ContactsRepo().contactsList

        mContactsListRv = view.findViewById<RecyclerView?>(R.id.contacts_list).apply {
            setLayoutManager(LinearLayoutManager(mContext))
            setAdapter(contactsAdapter.apply { submitList(contactsList) })
            addItemDecoration(ItemDecoration(mContext))
            setItemAnimator(null)
            seslSetFillBottomEnabled(true)
            seslSetLastRoundedCorner(true)
            seslSetIndexTipEnabled(true)
            seslSetGoToTopEnabled(true)
            seslSetSmoothScrollEnabled(true)
        }
    }

    private fun initIndexScroll() {
        val isRtl = resources.configuration
            .layoutDirection == View.LAYOUT_DIRECTION_RTL

        mIndexScrollView!!.setIndexBarGravity(
            if (isRtl)
                SeslIndexScrollView.GRAVITY_INDEX_BAR_LEFT
            else
                SeslIndexScrollView.GRAVITY_INDEX_BAR_RIGHT
        )

        val contactsList = ContactsRepo().contactsList

        val cursor = MatrixCursor(arrayOf("item"))
        for (item in contactsList) {
            cursor.addRow(arrayOf(item))
        }

        cursor.moveToFirst()

        val indexer = SeslCursorIndexer(cursor, 0,
            "A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,Б".split(",".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray(), 0
        )
        indexer.setGroupItemsCount(1)
        indexer.setMiscItemsCount(3)

        mIndexScrollView!!.setIndexer(indexer)
        mIndexScrollView!!.setOnIndexBarEventListener(
            object : OnIndexBarEventListener {
                override fun onIndexChanged(sectionIndex: Int) {
                    if (mCurrentSectionIndex != sectionIndex) {
                        mCurrentSectionIndex = sectionIndex
                        if (mListView!!.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                            mListView!!.stopScroll()
                        }
                        (mListView!!.layoutManager as LinearLayoutManager)
                            .scrollToPositionWithOffset(sectionIndex, 0)
                    }
                }

                override fun onPressed(v: Float) {
                    mIsIndexBarPressed = true
                    mListView!!.removeCallbacks(mHideIndexBar)
                }

                override fun onReleased(v: Float) {
                    mIsIndexBarPressed = false
                    if (mListView!!.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                        mListView!!.postDelayed(mHideIndexBar, 1500)
                    }
                }
            })
        mIndexScrollView!!.attachToRecyclerView(mListView)
        mListView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE
                    && !mIsIndexBarPressed
                ) {
                    recyclerView.postDelayed(mHideIndexBar, 1500)
                } else {
                    mListView!!.removeCallbacks(mHideIndexBar)
                    IndexScrollUtils.animateVisibility(mIndexScrollView!!, true)
                }
            }
        })
    }


    private inner class ItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val mDivider: Drawable?
        private val mRoundedCorner: SeslSubheaderRoundedCorner

        init {
            val outValue = TypedValue()
            context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, outValue, true)

            mDivider = context.getDrawable(
                if (outValue.data == 0)
                    androidx.appcompat.R.drawable.sesl_list_divider_dark
                else
                    androidx.appcompat.R.drawable.sesl_list_divider_light
            )

            mRoundedCorner = SeslSubheaderRoundedCorner(mContext)
            mRoundedCorner.roundedCorners = SeslRoundedCorner.ROUNDED_CORNER_ALL
        }

        override fun onDraw(
            c: Canvas, parent: RecyclerView,
            state: RecyclerView.State
        ) {
            super.onDraw(c, parent, state)

            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val holder = mContactsListRv!!.getChildViewHolder(child) as ContactsAdapter.ViewHolder
                if (!holder.isSeparator) {
                    val top = (child.bottom
                            + (child.layoutParams as MarginLayoutParams).bottomMargin)
                    val bottom = mDivider!!.intrinsicHeight + top

                    mDivider.setBounds(parent.left, top, parent.right, bottom)
                    mDivider.draw(c)
                }
            }
        }

        override fun seslOnDispatchDraw(
            c: Canvas,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val holder = mContactsListRv!!.getChildViewHolder(child) as ContactsAdapter.ViewHolder
                if (holder.isSeparator) {
                    mRoundedCorner.drawRoundedCorner(child, c)
                }
            }
        }
    }

}
