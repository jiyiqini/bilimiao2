package com.a10miaomiao.bilimiao.page.video.comment

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bilibili.main.community.reply.v1.ReplyGrpc
import bilibili.main.community.reply.v1.ReplyOuterClass
import com.a10miaomiao.bilimiao.MainNavGraph
import com.a10miaomiao.bilimiao.comm.MiaoBindingUi
import com.a10miaomiao.bilimiao.comm.entity.MessageInfo
import com.a10miaomiao.bilimiao.comm.entity.ResultInfo
import com.a10miaomiao.bilimiao.comm.entity.comm.PaginationInfo
import com.a10miaomiao.bilimiao.comm.entity.video.SubmitVideosInfo
import com.a10miaomiao.bilimiao.comm.entity.video.VideoCommentInfo
import com.a10miaomiao.bilimiao.comm.entity.video.VideoCommentReplyInfo
import com.a10miaomiao.bilimiao.comm.navigation.MainNavArgs
import com.a10miaomiao.bilimiao.comm.network.BiliApiService
import com.a10miaomiao.bilimiao.comm.network.MiaoHttp.Companion.gson
import com.a10miaomiao.bilimiao.comm.network.request
import com.a10miaomiao.bilimiao.comm.store.UserStore
import com.a10miaomiao.bilimiao.comm.utils.DebugMiao
import com.a10miaomiao.bilimiao.comm.utils.NumberUtil
import com.a10miaomiao.bilimiao.comm.utils.UrlUtil
import com.a10miaomiao.bilimiao.commponents.comment.VideoCommentViewContent
import com.a10miaomiao.bilimiao.commponents.comment.VideoCommentViewInfo
import com.kongzue.dialogx.dialogs.PopTip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class VideoCommentListViewModel(
    override val di: DI,
) : ViewModel(), DIAware {

    val context: Context by instance()
    val ui: MiaoBindingUi by instance()
    val fragment: Fragment by instance()
    val userStore: UserStore by instance()

    val id by lazy { fragment.requireArguments().getString(MainNavArgs.id, "") }
    val title by lazy { fragment.requireArguments().getString(MainNavArgs.title, "") }
    val cover by lazy { fragment.requireArguments().getString(MainNavArgs.cover, "") }
    val name by lazy { fragment.requireArguments().getString(MainNavArgs.name, "") }

    var sortOrder = 3

    var triggered = false
    var list = PaginationInfo<VideoCommentViewInfo>()
    private var _cursor: ReplyOuterClass.CursorReply? = null

    init {
        loadData()
    }

    private fun loadData() = viewModelScope.launch(Dispatchers.IO) {
        try {
            ui.setState {
                list.loading = true
            }
            val req = ReplyOuterClass.MainListReq.newBuilder().apply {
                oid = id.toLong()
                type = 1
                rpid = 0
                cursor = ReplyOuterClass.CursorReq.newBuilder().apply {
                    modeValue = sortOrder
                    _cursor?.let {
                        next = it.next
//                        prev = it.prev
                    }
                }.build()
            }.build()
            val res = ReplyGrpc.getMainListMethod().request(req)
                .awaitCall()
            if (_cursor == null) {
                list.data = mutableListOf()
                when {
                    res.upTop != null && res.upTop.id != 0L -> {
                        list.data.add(
                            VideoCommentViewAdapter.convertToVideoCommentViewInfo(res.upTop)
                        )
                    }

                    res.adminTop != null && res.adminTop.id != 0L -> {
                        list.data.add(
                            VideoCommentViewAdapter.convertToVideoCommentViewInfo(res.adminTop)
                        )
                    }

                    res.voteTop != null && res.voteTop.id != 0L -> {
                        list.data.add(
                            VideoCommentViewAdapter.convertToVideoCommentViewInfo(res.voteTop)
                        )
                    }
                }
            }
            ui.setState {
                if (res.repliesList != null) {
                    list.data.addAll(res.repliesList.map(
                        VideoCommentViewAdapter::convertToVideoCommentViewInfo
                    ))
                }
                _cursor = res.cursor
                if (res.cursor.isEnd) {
                    list.finished = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ui.setState {
                list.fail = true
            }
        } finally {
            ui.setState {
                list.loading = false
                triggered = false
            }
        }
    }

    fun loadMode() {
        val (loading, finished) = this.list
        if (!finished && !loading) {
            loadData()
        }
    }

    fun refreshList() {
        ui.setState {
            list = PaginationInfo()
            _cursor = null
            triggered = true
            loadData()
        }
    }

    fun setLike(
        index: Int,
        updateView: (item: VideoCommentViewInfo) -> Unit,
    ) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val item = list.data[index]
            val newAction = if (item.isLike) {
                0
            } else {
                1
            }
            val res = BiliApiService.commentApi
                .action(1, item.oid.toString(), item.id.toString(), newAction)
                .awaitCall()
                .gson<MessageInfo>()
            if (res.isSuccess) {
                val newItem = item.copy(
                    isLike = newAction == 1
                )
                withContext(Dispatchers.Main) {
                    updateView(newItem)
                }
            } else {
                withContext(Dispatchers.Main) {
                    PopTip.show(res.message)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                PopTip.show("喵喵被搞坏了:" + e.message ?: e.toString())
            }
        }
    }

    fun isLogin() = userStore.isLogin()

}