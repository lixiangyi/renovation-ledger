package com.renovation.ledger.ui.debug.netrecord

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject

class NetRecordDetailPagerAdapter(
    private val activity: FragmentActivity,
    private val record: NetRecordBean,
) : RecyclerView.Adapter<NetRecordDetailPagerAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.renovation.ledger.R.layout.layout_net_record_detail_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        when (position) {
            0 -> bindResponse(holder)
            1 -> bindHeaders(holder)
            2 -> if (NetRecordHelper.isGet(record)) bindQuery(holder) else bindBody(holder)
        }
    }

    override fun getItemCount(): Int = 3

    private fun bindResponse(holder: PageViewHolder) {
        val body = record.response.body
        val json = runCatching {
            JSONObject(body).toString(2)
        }.getOrElse {
            runCatching {
                JSONArray(body).toString(2)
            }.getOrDefault("""{"body":${JSONObject.quote(body)}}""")
        }
        bindPage(holder, json, body)
    }

    private fun bindHeaders(holder: PageViewHolder) {
        val plain = record.request.header
        bindPage(holder, NetRecordHelper.headersToJson(plain), plain)
    }

    private fun bindBody(holder: PageViewHolder) {
        val plain = record.request.postBody
        bindPage(holder, NetRecordHelper.bodyToJson(plain), plain)
    }

    private fun bindQuery(holder: PageViewHolder) {
        val plain = record.request.url.substringAfter("?", "")
        bindPage(holder, NetRecordHelper.queryToJson(record.request.url), plain)
    }

    private fun bindPage(holder: PageViewHolder, jsonString: String, plainText: String) {
        val tree = NetRecordHelper.attachTreeOrText(
            context = activity,
            container = holder.treeContainer,
            fallbackScroll = holder.fallbackScroll,
            fallbackText = holder.fallbackText,
            jsonString = jsonString,
            plainText = plainText,
        )
        NetRecordHelper.bindTreeActions(
            context = activity,
            tree = tree,
            actions = holder.actions,
            modify = holder.modify,
            expandAll = holder.expandAll,
            collapseAll = holder.collapseAll,
            fab = holder.fab,
            treeContainer = holder.treeContainer,
            fallbackScroll = holder.fallbackScroll,
        )
    }

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val treeContainer: FrameLayout = view.findViewById(com.renovation.ledger.R.id.fl_tree_container)
        val fallbackScroll: ScrollView = view.findViewById(com.renovation.ledger.R.id.scroll_plain)
        val fallbackText: TextView = view.findViewById(com.renovation.ledger.R.id.tv_plain)
        val actions: LinearLayout = view.findViewById(com.renovation.ledger.R.id.ll_actions)
        val modify: TextView = view.findViewById(com.renovation.ledger.R.id.tv_modify)
        val expandAll: TextView = view.findViewById(com.renovation.ledger.R.id.tv_expand_all)
        val collapseAll: TextView = view.findViewById(com.renovation.ledger.R.id.tv_collapse_all)
        val fab: FloatingActionButton = view.findViewById(com.renovation.ledger.R.id.fab_switch)
    }
}
