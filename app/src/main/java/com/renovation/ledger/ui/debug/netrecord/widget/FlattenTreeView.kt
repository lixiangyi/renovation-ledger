package com.renovation.ledger.ui.debug.netrecord.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.renovation.ledger.R
import com.renovation.ledger.ui.debug.netrecord.DebugDrawables
import com.renovation.ledger.ui.debug.netrecord.NetRecordHelper
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * JSON 树形展示（对齐 beike FlattenTreeView 的调试用法：展平树 + 搜索 + 折叠）。
 */
class FlattenTreeView @JvmOverloads constructor(
    context: Context,
    jsonStr: String,
    private val needShowSearchBar: Boolean = true,
    private val ignoreCase: Boolean = true,
    allTreeNodesExpanded: Boolean = false,
) : FrameLayout(context) {

    private val searchContainer: View
    private val editText: EditText
    private val clearIcon: ImageView
    private val recyclerView: RecyclerView
    private val emptyView: TextView

    private var rootNodes: List<TreeNode>
    private var sourceJson: String = jsonStr
    private val expandedKeys = mutableSetOf<String>()
    private var keyword: String = ""
    private var expandCallback: ((Boolean) -> Unit)? = null

    private val indentPx = resources.getDimensionPixelSize(R.dimen.dimen_8)
    private val adapter = TreeAdapter()
    private val decoration = TreeViewItemDecoration(indentPx)

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_flatten_tree_view, this, true)
        searchContainer = findViewById(R.id.ll_search_container)
        editText = findViewById(R.id.edit_text)
        clearIcon = findViewById(R.id.iv_clear)
        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.tv_no_data)

        searchContainer.isVisible = needShowSearchBar
        searchContainer.background = DebugDrawables.strokeRound(context, R.color.debug_search_stroke)
        rootNodes = TreeNodesParser.parse(jsonStr)
        if (allTreeNodesExpanded) {
            collectExpandableKeys(rootNodes).forEach { expandedKeys += it }
        } else {
            rootNodes.forEach { node -> expandedKeys += node.key }
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(decoration)
        refreshList()

        editText.setOnFocusChangeListener { _, hasFocus ->
            searchContainer.background = DebugDrawables.strokeRound(
                context,
                if (hasFocus) R.color.debug_primary else R.color.debug_search_stroke,
            )
        }
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                keyword = s?.toString().orEmpty().trim()
                clearIcon.isVisible = keyword.isNotEmpty()
                refreshList()
            }
        })
        clearIcon.setOnClickListener { editText.setText("") }
    }

    fun setVoidCallback(callback: (Boolean) -> Unit) {
        expandCallback = callback
    }

    fun expandAll() {
        collectExpandableKeys(rootNodes).forEach { expandedKeys += it }
        refreshList()
        expandCallback?.invoke(true)
    }

    fun collapseAll() {
        expandedKeys.clear()
        rootNodes.forEach { expandedKeys += it.key }
        refreshList()
        expandCallback?.invoke(false)
    }

    fun updateTreeNodes(jsonStr: String) {
        sourceJson = jsonStr
        rootNodes = TreeNodesParser.parse(jsonStr)
        expandedKeys.clear()
        rootNodes.forEach { expandedKeys += it.key }
        refreshList()
    }

    fun currentJson(): String = sourceJson

    private fun refreshList() {
        val flat = mutableListOf<TreeNode>()
        rootNodes.forEach { appendVisible(it, flat) }
        val filtered = if (keyword.isEmpty()) {
            flat
        } else {
            flat.filter { node ->
                node.searchText.contains(keyword, ignoreCase = ignoreCase)
            }
        }
        adapter.submit(filtered)
        emptyView.isVisible = filtered.isEmpty()
        recyclerView.isVisible = filtered.isNotEmpty()
    }

    private fun appendVisible(node: TreeNode, out: MutableList<TreeNode>) {
        out += node
        if (!node.isLeaf && expandedKeys.contains(node.key)) {
            node.children.forEach { appendVisible(it, out) }
        }
    }

    private fun collectExpandableKeys(nodes: List<TreeNode>): Set<String> {
        val keys = mutableSetOf<String>()
        fun walk(node: TreeNode) {
            if (!node.isLeaf) {
                keys += node.key
                node.children.forEach(::walk)
            }
        }
        nodes.forEach(::walk)
        return keys
    }

    private inner class TreeAdapter : RecyclerView.Adapter<TreeViewHolder>() {
        private var items: List<TreeNode> = emptyList()

        fun submit(list: List<TreeNode>) {
            items = list
            notifyDataSetChanged()
        }

        fun getItemLevel(position: Int): Int = items.getOrNull(position)?.level ?: 0

        fun isLastChild(position: Int): Boolean {
            val node = items.getOrNull(position) ?: return false
            val siblings = node.parent?.children ?: return false
            return siblings.lastOrNull() === node
        }

        fun getParentPosition(position: Int): Int {
            val parent = items.getOrNull(position)?.parent ?: return -1
            return items.indexOfFirst { it === parent }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_flatten_tree_node, parent, false)
            return TreeViewHolder(view)
        }

        override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
            val node = items[position]
            bindPrefix(holder, node)
            bindContent(holder.content, node)
            holder.suffix.setOnClickListener {
                showNodePreview(node)
            }
        }

        override fun getItemCount(): Int = items.size

        private fun bindPrefix(holder: TreeViewHolder, node: TreeNode) {
            if (node.isLeaf || node.isRoot) {
                holder.prefix.visibility = View.GONE
                holder.itemView.setOnClickListener(null)
                holder.itemView.isClickable = false
                return
            }
            holder.prefix.visibility = View.VISIBLE
            val expanded = expandedKeys.contains(node.key)
            holder.prefix.setImageResource(
                if (expanded) R.drawable.ic_debug_collapse else R.drawable.ic_debug_expand,
            )
            holder.itemView.isClickable = true
            holder.itemView.setOnClickListener {
                if (expandedKeys.contains(node.key)) {
                    expandedKeys.remove(node.key)
                } else {
                    expandedKeys += node.key
                }
                refreshList()
                expandCallback?.invoke(expandedKeys.contains(node.key))
            }
        }

        private fun bindContent(tv: TextView, node: TreeNode) {
            val keyColor = ContextCompat.getColor(context, R.color.debug_json_key)
            val valueColor = when (node.type) {
                NodeType.NUMBER -> ContextCompat.getColor(context, R.color.debug_json_number)
                NodeType.BOOL -> ContextCompat.getColor(context, R.color.debug_json_bool)
                NodeType.STRING, NodeType.UNKNOWN -> ContextCompat.getColor(context, R.color.debug_json_string)
                else -> keyColor
            }
            val highlightColor = ContextCompat.getColor(context, R.color.debug_json_highlight)
            val textSizePx = resources.getDimensionPixelSize(R.dimen.dimen_15)
            val builder = SpannableStringBuilder()
            val nameStart = builder.length
            builder.append(node.displayName)
            builder.setSpan(ForegroundColorSpan(keyColor), nameStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(AbsoluteSizeSpan(textSizePx), nameStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (node.isRoot) {
                builder.setSpan(StyleSpan(Typeface.BOLD), nameStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (node.isLeaf && node.value.isNotEmpty()) {
                val sepStart = builder.length
                builder.append(" : ")
                builder.setSpan(ForegroundColorSpan(keyColor), sepStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val valueStart = builder.length
                builder.append(node.value)
                builder.setSpan(ForegroundColorSpan(valueColor), valueStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(AbsoluteSizeSpan(textSizePx), valueStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (keyword.isNotEmpty()) {
                val haystack = builder.toString()
                var index = haystack.indexOf(keyword, startIndex = 0, ignoreCase = ignoreCase)
                while (index >= 0) {
                    val end = index + keyword.length
                    builder.setSpan(ForegroundColorSpan(highlightColor), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(StyleSpan(Typeface.BOLD), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    index = haystack.indexOf(keyword, startIndex = end, ignoreCase = ignoreCase)
                }
            }
            tv.text = builder
        }

        private fun showNodePreview(node: TreeNode) {
            val preview = if (node.isLeaf) {
                if (node.value.isEmpty()) node.displayName else "${node.displayName} : ${node.value}"
            } else {
                node.toJsonPreview()
            }
            AlertDialog.Builder(context)
                .setTitle(node.displayName)
                .setMessage(preview)
                .setPositiveButton("复制") { _, _ ->
                    NetRecordHelper.copyContent(context, preview)
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private inner class TreeViewItemDecoration(private val indent: Int) : RecyclerView.ItemDecoration() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.debug_tree_line)
            strokeWidth = resources.getDimension(R.dimen.dimen_2)
            style = Paint.Style.FILL
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            outRect.left = indent * adapter.getItemLevel(position)
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val childCount = parent.childCount
            val layoutManager = parent.layoutManager ?: return
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val leftMargin = layoutManager.getLeftDecorationWidth(child)
                if (leftMargin == 0) continue
                c.drawLine(
                    leftMargin.toFloat(),
                    child.top + child.height / 2f,
                    (leftMargin - indent).toFloat(),
                    child.top + child.height / 2f,
                    paint,
                )
                var startX = leftMargin - indent
                var position = parent.getChildAdapterPosition(child)
                while (startX >= 0) {
                    if (position !in 0 until adapter.itemCount) break
                    val isLastChild = adapter.isLastChild(position)
                    if (startX == leftMargin - indent || !isLastChild) {
                        c.drawLine(
                            startX.toFloat(),
                            child.top.toFloat(),
                            startX.toFloat(),
                            if (isLastChild) child.top + child.height / 2f else child.bottom.toFloat(),
                            paint,
                        )
                    }
                    startX -= indent
                    position = adapter.getParentPosition(position)
                }
            }
        }
    }

    private class TreeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val prefix: ImageView = view.findViewById(R.id.iv_prefix_icon)
        val content: TextView = view.findViewById(R.id.tv_content)
        val suffix: ImageView = view.findViewById(R.id.iv_suffix_icon)
    }
}

internal enum class NodeType {
    OBJECT, ARRAY, STRING, NUMBER, BOOL, UNKNOWN
}

internal class TreeNode(
    val key: String,
    val parent: TreeNode?,
    val level: Int,
    val displayName: String,
    val value: String,
    val type: NodeType,
    val isRoot: Boolean = false,
    val children: MutableList<TreeNode> = mutableListOf(),
) {
    val isLeaf: Boolean get() = children.isEmpty()
    val searchText: String
        get() = if (value.isEmpty()) displayName else "$displayName : $value"

    fun toJsonPreview(): String {
        return runCatching {
            when (type) {
                NodeType.ARRAY -> toJsonArray().toString(2)
                else -> toJsonObject().toString(2)
            }
        }.getOrDefault(searchText)
    }

    private fun toJsonObject(): JSONObject {
        val json = JSONObject()
        children.forEach { child ->
            json.put(child.displayName, child.jsonValue())
        }
        return json
    }

    private fun toJsonArray(): JSONArray {
        val json = JSONArray()
        children.forEach { child -> json.put(child.jsonValue()) }
        return json
    }

    private fun jsonValue(): Any {
        return when {
            !isLeaf && type == NodeType.ARRAY -> toJsonArray()
            !isLeaf -> toJsonObject()
            type == NodeType.NUMBER -> value.toDoubleOrNull() ?: value
            type == NodeType.BOOL -> value.toBooleanStrictOrNull() ?: value
            else -> value
        }
    }
}

private object TreeNodesParser {
    fun parse(jsonStr: String): List<TreeNode> {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching {
            when (val value = JSONTokener(trimmed).nextValue()) {
                is JSONObject -> {
                    val root = TreeNode(
                        key = "root",
                        parent = null,
                        level = 0,
                        displayName = "JSON",
                        value = "",
                        type = NodeType.OBJECT,
                        isRoot = true,
                    )
                    parseObject(value, root, level = 1)
                    listOf(root)
                }
                is JSONArray -> {
                    val root = TreeNode(
                        key = "root",
                        parent = null,
                        level = 0,
                        displayName = "JSON",
                        value = "[${value.length()}]",
                        type = NodeType.ARRAY,
                        isRoot = true,
                    )
                    parseArray(value, root, level = 1)
                    listOf(root)
                }
                else -> listOf(
                    TreeNode(
                        key = "root",
                        parent = null,
                        level = 0,
                        displayName = "JSON",
                        value = value.toString(),
                        type = primitiveType(value),
                        isRoot = true,
                    ),
                )
            }
        }.getOrElse {
            listOf(
                TreeNode(
                    key = "root",
                    parent = null,
                    level = 0,
                    displayName = "JSON",
                    value = trimmed,
                    type = NodeType.STRING,
                    isRoot = true,
                ),
            )
        }
    }

    private fun parseObject(obj: JSONObject, parent: TreeNode, level: Int) {
        obj.keys().forEach { childKey ->
            attachChild(parent, level, "${parent.key}.$childKey", childKey, obj.get(childKey))
        }
    }

    private fun parseArray(array: JSONArray, parent: TreeNode, level: Int) {
        for (index in 0 until array.length()) {
            attachChild(parent, level, "${parent.key}[$index]", "[$index]", array.get(index))
        }
    }

    private fun attachChild(parent: TreeNode, level: Int, key: String, name: String, value: Any?) {
        when (value) {
            is JSONObject -> {
                val child = TreeNode(
                    key = key,
                    parent = parent,
                    level = level,
                    displayName = name,
                    value = "",
                    type = NodeType.OBJECT,
                )
                parent.children += child
                parseObject(value, child, level + 1)
            }
            is JSONArray -> {
                val child = TreeNode(
                    key = key,
                    parent = parent,
                    level = level,
                    displayName = name,
                    value = "",
                    type = NodeType.ARRAY,
                )
                parent.children += child
                parseArray(value, child, level + 1)
            }
            else -> {
                parent.children += TreeNode(
                    key = key,
                    parent = parent,
                    level = level,
                    displayName = name,
                    value = formatPrimitive(value),
                    type = primitiveType(value),
                )
            }
        }
    }

    private fun primitiveType(value: Any?): NodeType = when (value) {
        is Boolean -> NodeType.BOOL
        is Number -> NodeType.NUMBER
        is String -> NodeType.STRING
        else -> NodeType.UNKNOWN
    }

    private fun formatPrimitive(value: Any?): String = when (value) {
        null -> "null"
        JSONObject.NULL -> "null"
        else -> value.toString()
    }
}

class JsonParseException(message: String) : RuntimeException(message)
