package com.a10miaomiao.bilimiao

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.DisplayCutout
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.a10miaomiao.bilimiao.activity.SearchActivity
import com.a10miaomiao.bilimiao.comm.delegate.helper.StatusBarHelper
import com.a10miaomiao.bilimiao.comm.delegate.helper.SupportHelper
import com.a10miaomiao.bilimiao.comm.delegate.player.BasePlayerDelegate
import com.a10miaomiao.bilimiao.comm.delegate.player.PlayerDelegate2
import com.a10miaomiao.bilimiao.comm.delegate.sheet.BottomSheetDelegate
import com.a10miaomiao.bilimiao.comm.delegate.theme.ThemeDelegate
import com.a10miaomiao.bilimiao.comm.mypage.MyPage
import com.a10miaomiao.bilimiao.comm.mypage.MyPageConfigInfo
import com.a10miaomiao.bilimiao.comm.navigation.MainNavArgs
import com.a10miaomiao.bilimiao.comm.utils.DebugMiao
import com.a10miaomiao.bilimiao.config.config
import com.a10miaomiao.bilimiao.page.MainBackPopupMenu
import com.a10miaomiao.bilimiao.page.search.SearchResultFragment
import com.a10miaomiao.bilimiao.page.start.StartFragment
import com.a10miaomiao.bilimiao.service.PlayerService
import com.a10miaomiao.bilimiao.store.*
import com.a10miaomiao.bilimiao.widget.comm.*
import com.a10miaomiao.bilimiao.widget.comm.behavior.PlayerBehavior
import com.baidu.mobstat.StatService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bindSingleton


class MainActivity
    : AppCompatActivity(),
    DIAware,
    NavController.OnDestinationChangedListener,
    FragmentOnAttachListener {

    lateinit var ui: MainUi

    override val di: DI = DI.lazy {
        bindSingleton { this@MainActivity }
        store.loadStoreModules(this)
        bindSingleton { basePlayerDelegate }
        bindSingleton { themeDelegate }
        bindSingleton { statusBarHelper }
        bindSingleton { supportHelper }
    }

    private val store by lazy { Store(this, di) }
    private val themeDelegate by lazy { ThemeDelegate(this, di) }
    private val basePlayerDelegate: BasePlayerDelegate by lazy { PlayerDelegate2(this, di) }
    private val bottomSheetDelegate by lazy { BottomSheetDelegate(this, ui) }
    private val statusBarHelper by lazy { StatusBarHelper(this) }
    private val supportHelper by lazy { SupportHelper(this) }

    private lateinit var leftFragment: StartFragment
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navController: NavController

    var pageConfig: MyPageConfigInfo? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getCustomDensityDpi()
        themeDelegate.onCreate(savedInstanceState)
        ui = MainUi(this)
        setContentView(ui.root)
        basePlayerDelegate.onCreate(savedInstanceState)
        bottomSheetDelegate.onCreate(savedInstanceState)
        store.onCreate(savedInstanceState)
        ui.root.showPlayer = basePlayerDelegate.isPlaying()
        ui.root.onDrawerStateChanged = ::onDrawerStateChanged
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ui.root.rootWindowInsets?.let {
                setWindowInsets(it)
            }
            ui.root.setOnApplyWindowInsetsListener { v, insets ->
                setWindowInsets(insets)
                insets
            }
            ui.root.onPlayerChanged = {
                statusBarHelper.isLightStatusBar =
                    !it || (ui.root.orientation == ScaffoldView.HORIZONTAL && !ui.root.fullScreenPlayer)
                setWindowInsets(ui.root.rootWindowInsets)
            }
        } else {
            setWindowInsetsAndroidL()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            fullScreenUseStatus()
        }

        navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        MainNavGraph.createGraph(navController, MainNavGraph.dest.main)
        navController.addOnDestinationChangedListener(this)
        navHostFragment.childFragmentManager.addFragmentOnAttachListener(this)

        (supportFragmentManager.findFragmentByTag(getString(R.string.tag_left_fragment)) as? StartFragment)?.let {
            leftFragment = it
            ui.root.drawerFragment = it
//            if (leftFragment.isVisible) {
//                supportFragmentManager.beginTransaction()
//                    .hide(leftFragment)
//                    .commit()
//            }
        }

        ui.mAppBar.onBackClick = this.onBackClick
        ui.mAppBar.onBackLongClick = this.onBackLongClick
        ui.mAppBar.onMenuItemClick = {
            val fragment = navHostFragment.childFragmentManager.primaryNavigationFragment
            if (fragment is MyPage) {
                fragment.onMenuItemClick(it, it.prop)
            }
        }

//        ui.mContainerView.addDrawerListener(onDrawer)
//        lifecycleScope.launch(Dispatchers.IO){
//            val refreshToken = Bilimiao.commApp.loginInfo!!.token_info.refresh_token
//            DebugMiao.log(Bilimiao.commApp.loginInfo)
//            val res = BiliApiService.authApi.refreshToken(refreshToken).awaitCall()
//            DebugMiao.log("oauth2", res.body()?.string())
//        }

        // 百度统计
        StatService.setAuthorizedState(this, false)
        StatService.start(this)

        navController.handleDeepLink(intent)

        val intentFilter = IntentFilter().apply {
            addAction(PlayerService.ACTION_CREATED)
        }
        ContextCompat.registerReceiver(this, broadcastReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (PlayerService.selfInstance == null) {
            startService(Intent(this, PlayerService::class.java))
        }

        // 安卓13开始手动申请通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                //2、申请权限: 参数二：权限的数组；参数三：请求码
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }

    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            val navOptions = NavOptions.Builder()
                .setEnterAnim(R.anim.miao_fragment_open_enter)
                .setExitAnim(R.anim.miao_fragment_open_exit)
                .setPopEnterAnim(R.anim.miao_fragment_close_enter)
                .setPopExitAnim(R.anim.miao_fragment_close_exit)
                .build()
            try {
                navController.navigate(uri, navOptions)
                true
            } catch (e: IllegalArgumentException) {
            }
        }
    }


    override fun onAttachFragment(fragmentManager: FragmentManager, fragment: Fragment) {
        if (fragment is MyPage) {
            val config = fragment.pageConfig
            config.setConfig = this::setMyPageConfig
        }
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        ui.mAppBar.canBack = destination.id != MainNavGraph.dest.main
//        ui.mAppBar.cleanProp()
    }

    fun setMyPageConfig(config: MyPageConfigInfo) {
        pageConfig = config
        ui.mAppBar.setProp {
            title = config.title
            menus = config.menus
        }
        ui.root.slideUpBottomAppBar()
        leftFragment.setConfig(config.search)
    }

    private fun goBackHome(): Boolean {
        val nav = findNavController(R.id.nav_host_fragment)
        return nav.popBackStack(MainNavGraph.dest.main, false)
    }

    val onBackClick = View.OnClickListener {
        onBackPressed()
    }

    val onBackLongClick = View.OnLongClickListener {
        if (ui.root.showPlayer) {
            MainBackPopupMenu(
                this@MainActivity,
                it,
                basePlayerDelegate
            ).show()
            true
        } else {
            goBackHome()
        }
    }

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PlayerService.ACTION_CREATED -> {
                    PlayerService.selfInstance?.videoPlayerView = findViewById(R.id.video_player)
                }
            }
        }
    }

    fun onDrawerStateChanged(state: Int) {
        // 太麻烦
//        supportFragmentManager.beginTransaction().also {
//            if (state == AppBarBehaviorDelegate.STATE_COLLAPSED
//                && leftFragment.isVisible) {
//                leftFragment.hideSoftInput()
//                it.hide(leftFragment)
//            } else if (leftFragment.isHidden) {
//                it.show(leftFragment)
//            }
//        }.commit()
    }

    fun searchSelfPage(keyword: String) {
        val fragment = navHostFragment.childFragmentManager.primaryNavigationFragment
        if (fragment is MyPage) {
            fragment.onSearchSelfPage(this, keyword)
        }
    }

    fun setWindowInsetsAndroidL() {
        val rectangle = Rect()
        val displayMetrics = DisplayMetrics()
        window.decorView.getWindowVisibleDisplayFrame(rectangle)
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val top = statusBarHelper.getStatusBarHeight()
        val bottom = displayMetrics.heightPixels - rectangle.bottom - rectangle.top
        val right = displayMetrics.widthPixels - rectangle.right
        setWindowInsets(0, top, right, bottom, null)
    }

    fun setWindowInsets(insets: WindowInsets) {
        val left = insets.systemWindowInsetLeft
        val top = insets.systemWindowInsetTop
        val right = insets.stableInsetRight
        val bottom = insets.systemWindowInsetBottom
        var displayCutout: DisplayCutout? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            displayCutout = insets.displayCutout
        }
        setWindowInsets(left, top, right, bottom, displayCutout)
    }

    fun setWindowInsets(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        displayCutout: DisplayCutout?
    ) {
        val windowStore = store.windowStore
        windowStore.setWindowInsets(
            left, top, right, bottom,
        )
        windowStore.setBottomSheetContentInsets(
            0, config.bottomSheetTitleHeight, 0, bottom
        )
        val playerLP = ui.mPlayerLayout.layoutParams
        if (playerLP is ScaffoldView.LayoutParams) {
            val behavior = playerLP.behavior
            if (behavior is PlayerBehavior) {
                behavior.setWindowInsets(left, top, right, bottom)
            }
        }
        ui.mAppBar.setWindowInsets(left, top, right, bottom)
        ui.mBottomSheetLayout.setPadding(left, top, right, 0)
        val showPlayer = ui.root.showPlayer
        val fullScreenPlayer = ui.root.fullScreenPlayer
        if (ui.root.orientation == ScaffoldView.VERTICAL) {
            if (showPlayer) {
                windowStore.setContentInsets(
                    left, 0, right, bottom + top + config.appBarTitleHeight + ui.root.smallModePlayerHeight,
                )
            } else {
                windowStore.setContentInsets(
                    left, top, right, bottom + config.appBarTitleHeight,
                )
            }
            windowStore.setBottomAppBarHeight(config.appBarMenuHeight)
            ui.mPlayerLayout.setPadding(
                0, if (fullScreenPlayer) 0 else top, 0, 0
            )
        } else {
            windowStore.setContentInsets(
                left, top, right, bottom,
            )
            windowStore.setBottomAppBarHeight(0)
            ui.mPlayerLayout.setPadding(
                0, 0, 0, 0
            )
        }
        basePlayerDelegate.setWindowInsets(left, top, right, bottom, displayCutout)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun fullScreenUseStatus() {
        val attributes = window.attributes
        attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = attributes
    }

    override fun onResume() {
        super.onResume()
        basePlayerDelegate.onResume()

        // 百度移动统计埋点
        StatService.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        basePlayerDelegate.onPause()

        // 百度移动统计埋点
        StatService.onPause(this)
    }

    override fun onDestroy() {
        basePlayerDelegate.onDestroy()
        bottomSheetDelegate.onDestroy()
        store.onDestroy()
        navController.removeOnDestinationChangedListener(this)
        navHostFragment.childFragmentManager.removeFragmentOnAttachListener(this)
        unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        basePlayerDelegate.onStart()
    }

    override fun onStop() {
        super.onStop()
        basePlayerDelegate.onStop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            SearchActivity.REQUEST_CODE -> {
                val arguments = data?.extras ?: Bundle()
                if (arguments.containsKey(SearchActivity.KEY_URL)) {
                    val pageUrl = arguments.getString(SearchActivity.KEY_URL)
                    val navOptions = NavOptions.Builder()
                        .setEnterAnim(R.anim.miao_fragment_open_enter)
                        .setExitAnim(R.anim.miao_fragment_open_exit)
                        .setPopEnterAnim(R.anim.miao_fragment_close_enter)
                        .setPopExitAnim(R.anim.miao_fragment_close_exit)
                        .build()
                    navController.navigate(Uri.parse(pageUrl), navOptions)
                    return
                }
                val mode = arguments.getInt(SearchActivity.KEY_MODE)
                val keyword = arguments.getString(SearchActivity.KEY_KEYWORD, "")
                if (mode == 0) {
                    navController.navigate(
                        SearchResultFragment.actionId,
                        SearchResultFragment.createArguments(keyword),
                    )
                } else {
                    searchSelfPage(keyword)
                }
            }
        }
    }

    /**
     * 通知权限设置界面跳转
     */
    private fun jumpNotificationSetting() {
        val intent = Intent()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
                intent.putExtra("app_package", packageName)
                intent.putExtra("app_uid", applicationInfo.uid)
            } else {
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.addCategory(Intent.CATEGORY_DEFAULT)
                intent.data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
    }

    /**
     * 通知权限授权提示
     */
    private fun showNotificationPermissionTips() {
        MaterialAlertDialogBuilder(this).apply {
            setTitle("请求授权”通知权限“")
            setMessage("从Android13开始，需要您授予通知权限，在您向该应用授予该权限之前，该应用都将无法发送通知。\n受影响的功能：通知栏播放器控制器、下载进度通知")
            setCancelable(false)
            setPositiveButton("去授权") { dialog, _ ->
                jumpNotificationSetting()
            }
            setNegativeButton("拒绝", null)
        }.show()
    }

    /**
     * 判断授权的方法  授权成功直接调用写入方法  这是监听的回调
     * 参数  上下文   授权结果的数组   申请授权的数组
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            val i = permissions.indexOf(Manifest.permission.POST_NOTIFICATIONS)
            if (i != -1 && grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                showNotificationPermissionTips()
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        basePlayerDelegate.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) { // 进入画中画模式，则隐藏其它控件

        } else {

        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        getCustomDensityDpi()
        super.onConfigurationChanged(newConfig)
        basePlayerDelegate.onConfigurationChanged(newConfig)
        ui.root.orientation = newConfig.orientation
        statusBarHelper.isLightStatusBar =
            !ui.root.showPlayer || (ui.root.orientation == ScaffoldView.HORIZONTAL && !ui.root.fullScreenPlayer)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            setWindowInsetsAndroidL()
        }
    }

    private fun getCustomDensityDpi() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val dpi = prefs.getInt("app_dpi", 0)
        if (dpi != 0) {
            Bilimiao.app.setCustomDensityDpi(this, dpi)
        }
    }

    override fun onBackPressed() {
        if (leftFragment.onBackPressed()) {
            return
        }
        if (ui.root.isDrawerOpen()) {
            ui.root.closeDrawer()
            return
        }
        if (bottomSheetDelegate.onBackPressed()) {
            return
        }
        if (basePlayerDelegate.onBackPressed()) {
            return
        }
        super.onBackPressed()
    }

}