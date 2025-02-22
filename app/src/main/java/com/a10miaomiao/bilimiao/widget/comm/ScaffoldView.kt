package com.a10miaomiao.bilimiao.widget.comm

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.a10miaomiao.bilimiao.comm.utils.DebugMiao
import com.a10miaomiao.bilimiao.config.config
import com.a10miaomiao.bilimiao.widget.comm.behavior.AppBarBehavior
import com.a10miaomiao.bilimiao.widget.comm.behavior.ContentBehavior
import com.a10miaomiao.bilimiao.widget.comm.behavior.DrawerBehavior
import com.a10miaomiao.bilimiao.widget.comm.behavior.MaskBehavior
import com.a10miaomiao.bilimiao.widget.comm.behavior.PlayerBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior
import splitties.dimensions.dip
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.material.hidden

class ScaffoldView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    companion object {
        const val HORIZONTAL = 2 // 横屏
        const val VERTICAL = 1 // 竖屏
    }

    var onPlayerChanged: ((show: Boolean) -> Unit)? = null
    var onDrawerStateChanged: ((state: Int) -> Unit)? = null

    var orientation = VERTICAL
        set(value) {
            if (field != value) {
                field = value
                this.appBar?.orientation = orientation
                requestLayout()
            }
        }

    var showPlayer = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                onPlayerChanged?.invoke(field)
            }
        }
    var fullScreenPlayer = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                onPlayerChanged?.invoke(true)
            }
        }

    var appBarHeight = config.appBarHeight
    var appBarWidth = config.appBarMenuWidth

    var smallModePlayerHeight = context.dip(200) // 小屏模式下的播放器高度
    var playerHeight = -3
    var playerWidth = -3

    var appBar: AppBarView? = null
    var appBarBehavior: AppBarBehavior? = null
    var drawerFragment: Fragment? = null

    var content: View? = null
    var contentBehavior: ContentBehavior? = null

    var player: View? = null
    var playerBehavior: PlayerBehavior? = null

    var bottomSheet: View? = null
    var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    var drawerView: View? = null
    var drawerBehavior: DrawerBehavior? = null

    var maskView: View? = null
    var maskBehavior: MaskBehavior? = null

    override fun addView(
        child: View?,
        index: Int,
        params: ViewGroup.LayoutParams?
    ) {
        if (params is LayoutParams) {
            when (val behavior = params.behavior) {
                is AppBarBehavior -> {
                    if (child is AppBarView) {
                        child.orientation = orientation
                        this.appBar = child
                        this.appBarBehavior = behavior
                    }
                }
                is ContentBehavior -> {
                    this.content = child
                    this.contentBehavior = behavior
                }
                is PlayerBehavior -> {
                    this.player = child
                    this.playerBehavior = behavior
                }
                is BottomSheetBehavior -> {
                    this.bottomSheet = child
                    this.bottomSheetBehavior = behavior
                }
                is DrawerBehavior -> {
                    this.drawerView = child
                    this.drawerBehavior = behavior
                }
                is MaskBehavior -> {
                    this.maskView = child
                    this.maskBehavior = behavior
                }
            }
        }
        super.addView(child, index, params)
    }

    fun bottomSheetState(): Int {
        return bottomSheetBehavior?.state ?: BottomSheetBehavior.STATE_HIDDEN
    }

    fun isDrawerOpen(): Boolean {
        return drawerBehavior?.isDrawerOpen() ?: false
    }

    fun openDrawer() {
        drawerBehavior?.openDrawer()
    }

    fun closeDrawer() {
        drawerBehavior?.closeDrawer()
    }

    fun changedDrawerState(state: Int) {
        onDrawerStateChanged?.invoke(state)
    }

    fun slideUpBottomAppBar() {
        if (orientation == VERTICAL) {
            appBar?.let {
                appBarBehavior?.slideUp(it)
            }
        }
    }

    fun setMaskViewVisibility(visibility: Int) {
        maskView?.visibility = visibility
    }

    fun setMaskViewAlpha(alpha: Float) {
        maskView?.alpha = alpha
    }

    fun slideDownBottomAppBar() {
        if (orientation == VERTICAL) {
            appBar?.let {
                appBarBehavior?.slideDown(it)
            }
        }
    }

    inline fun lParams(
        width: Int = wrapContent,
        height: Int = wrapContent,
        initParams: LayoutParams.() -> Unit = {}
    ): LayoutParams {
//        contract { callsInPlace(initParams, InvocationKind.EXACTLY_ONCE) }
        return LayoutParams(width, height).apply(initParams)
    }

    class LayoutParams(width: Int, height: Int) : CoordinatorLayout.LayoutParams(width, height) {

    }

}