package com.a10miaomiao.bilimiao.page.video.comment

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bilibili.main.community.reply.v1.ReplyGrpc
import bilibili.main.community.reply.v1.ReplyOuterClass
import com.a10miaomiao.bilimiao.MainNavGraph
import com.a10miaomiao.bilimiao.comm.MiaoBindingUi
import com.a10miaomiao.bilimiao.comm.entity.comm.PaginationInfo
import com.a10miaomiao.bilimiao.comm.network.request
import com.a10miaomiao.bilimiao.comm.store.UserStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class VideoCommentDetailViewModel(
    override val di: DI,
) : ViewModel(), DIAware {

    val context: Context by instance()
    val ui: MiaoBindingUi by instance()
    val fragment: Fragment by instance()
    val userStore: UserStore by instance()

    val id by lazy { fragment.requireArguments().getString(MainNavGraph.args.id, "") }
    val reply by lazy { fragment.requireArguments().getParcelable<VideoCommentDetailParame>("reply")!! }

    // 0：按时间，1：按点赞数，2：按回复数
    var sortOrder = 2

    var triggered = false
    var list = PaginationInfo<ReplyOuterClass.ReplyInfo>()
    private var _cursor: ReplyOuterClass.CursorReply? = null

    init {
        loadData()
    }

    private fun loadData(
        pageNum: Int = list.pageNum
    ) = viewModelScope.launch(Dispatchers.IO){
        try {
            ui.setState {
                list.loading = true
            }
            val req = ReplyOuterClass.DetailListReq.newBuilder().apply {
                oid = reply.oid
                root = reply.rpid
                type = 1
                scene = ReplyOuterClass.DetailListScene.REPLY
                _cursor?.let {
                    cursor = ReplyOuterClass.CursorReq.newBuilder()
                        .setNext(it.prev)
                        .setNext(it.next)
                        .setMode(it.mode)
                        .setModeValue(it.modeValue)
                        .build()
                }
            }.build()
            val res = ReplyGrpc.getDetailListMethod().request(req)
                .awaitCall()
            if (_cursor == null){
                list.data = mutableListOf()
            }
            ui.setState {
                list.data.addAll(res.root.repliesList)
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

    private fun _loadData() {
        loadData()
    }

    fun loadMode () {
        val (loading, finished, pageNum) = this.list
        if (!finished && !loading) {
            loadData(pageNum = pageNum + 1)
        }
    }

    fun refreshList() {
        ui.setState {
            list = PaginationInfo()
            triggered = true
            loadData()
            _cursor = null
        }
    }



}