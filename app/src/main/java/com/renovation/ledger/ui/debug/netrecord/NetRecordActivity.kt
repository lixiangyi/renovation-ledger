package com.renovation.ledger.ui.debug.netrecord

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.renovation.ledger.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 接口请求监听（参考 beike NetRecordActivity；详情页用 FlattenTreeView 展示 JSON）。
 */
class NetRecordActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var searchContainer: LinearLayout
    private lateinit var searchEdit: EditText
    private lateinit var searchClear: ImageView
    private lateinit var searchFab: FloatingActionButton

    private val adapter = NetRecordListAdapter()
    private var originalList: List<NetRecordBean> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_net_record)

        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.rv_net_record)
        emptyView = findViewById(R.id.empty_view)
        searchContainer = findViewById(R.id.ll_search_container)
        searchEdit = findViewById(R.id.edit_text)
        searchClear = findViewById(R.id.iv_clear)
        searchFab = findViewById(R.id.fab_search)

        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.menu_net_record)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear) {
                NetRecordStore.clear()
                true
            } else {
                false
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        adapter.onClick = { record ->
            NetRecordHelper.showDetailRecord(this, record)
        }
        adapter.onLongClick = { record ->
            NetRecordHelper.showPopupMenu(this, record)
        }

        searchFab.setOnClickListener {
            searchContainer.isVisible = !searchContainer.isVisible
        }
        searchClear.setOnClickListener { searchEdit.setText("") }
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filterRecords(s?.toString().orEmpty().trim())
            }
        })

        lifecycleScope.launch {
            NetRecordStore.records.collect { list ->
                originalList = list.asReversed()
                filterRecords(searchEdit.text?.toString().orEmpty().trim())
            }
        }
    }

    private fun filterRecords(keyword: String) {
        val filtered = if (keyword.isEmpty()) {
            originalList
        } else {
            originalList.filter { it.request.url.contains(keyword, ignoreCase = true) }
                .map { it.copy(request = it.request.copy(searchKey = keyword)) }
        }
        toolbar.title = if (filtered.isEmpty()) {
            "接口请求监听"
        } else {
            "接口请求监听 (${filtered.size})"
        }
        adapter.submit(filtered)
        val hasData = filtered.isNotEmpty()
        recyclerView.isVisible = hasData
        emptyView.isVisible = !hasData
    }

    private class NetRecordListAdapter : RecyclerView.Adapter<NetRecordViewHolder>() {
        private var items: List<NetRecordBean> = emptyList()
        var onClick: ((NetRecordBean) -> Unit)? = null
        var onLongClick: ((NetRecordBean) -> Unit)? = null

        fun submit(list: List<NetRecordBean>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetRecordViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_net_record, parent, false)
            return NetRecordViewHolder(view)
        }

        override fun onBindViewHolder(holder: NetRecordViewHolder, position: Int) {
            val item = items[position]
            holder.num.text = (position + 1).toString()
            holder.url.text = item.request.url
            holder.method.text = item.request.method
            holder.status.text = "Status:${item.response.statusCode}"
            holder.status.setTextColor(
                if (item.response.statusCode in 200..299) Color.parseColor("#00AE66") else Color.RED,
            )
            holder.duration.text = "用时：${item.response.durationMs}ms"
            holder.size.text = "${item.response.bodySizeKb}KB"
            holder.time.text = "发起时间：${TIME_FORMAT.format(Date(item.request.startTimeMs))}"
            holder.container.setOnClickListener { onClick?.invoke(item) }
            holder.container.setOnLongClickListener {
                onLongClick?.invoke(item)
                true
            }
        }

        override fun getItemCount(): Int = items.size

        companion object {
            private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        }
    }

    private class NetRecordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.ll_container)
        val num: TextView = view.findViewById(R.id.tv_num)
        val url: TextView = view.findViewById(R.id.tv_request_url)
        val method: TextView = view.findViewById(R.id.tv_method)
        val status: TextView = view.findViewById(R.id.tv_status_code)
        val duration: TextView = view.findViewById(R.id.tv_duration)
        val size: TextView = view.findViewById(R.id.tv_size)
        val time: TextView = view.findViewById(R.id.tv_req_time)
    }
}
