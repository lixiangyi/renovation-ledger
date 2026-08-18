package com.renovation.ledger.ui.debug.netrecord

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.renovation.ledger.R
import com.renovation.ledger.ui.debug.netrecord.widget.FlattenTreeView
import com.renovation.ledger.ui.debug.netrecord.widget.JsonParseException
import org.json.JSONException
import org.json.JSONObject

object NetRecordHelper {
    fun isGet(record: NetRecordBean): Boolean = record.request.method.equals("GET", ignoreCase = true)

    fun copyContent(context: Context, content: String) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("net-record", content))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun showPopupMenu(activity: Activity, record: NetRecordBean) {
        AlertDialog.Builder(activity)
            .setTitle("操作")
            .setItems(arrayOf("复制 cURL", "复制响应", "复制 URL")) { _, which ->
                when (which) {
                    0 -> copyContent(activity, record.request.curl)
                    1 -> copyContent(activity, record.response.body)
                    2 -> copyContent(activity, record.request.url)
                }
            }
            .show()
    }

    fun showDetailRecord(activity: FragmentActivity, record: NetRecordBean) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_net_record_detail, null)
        val dialog = AlertDialog.Builder(activity, R.style.Theme_RenovationLedger_Debug_FullScreenDialog)
            .setView(dialogView)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        dialogView.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<TextView>(R.id.tv_title).text = detailTitle(record.request.url)

        val tabLayout = dialogView.findViewById<TabLayout>(R.id.tabs)
        val viewPager = dialogView.findViewById<ViewPager2>(R.id.view_pager)
        viewPager.adapter = NetRecordDetailPagerAdapter(activity, record)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Response"
                1 -> "Headers"
                2 -> if (isGet(record)) "Query" else "Body"
                else -> ""
            }
        }.attach()
        dialog.show()
    }

    fun detailTitle(url: String): String {
        val path = runCatching { Uri.parse(url).path.orEmpty() }.getOrDefault("")
        val segments = path.split("/").filter { it.isNotEmpty() }
        val title = when {
            segments.size >= 2 -> segments.takeLast(2).joinToString("/")
            segments.isNotEmpty() -> segments.last()
            else -> ""
        }
        return if (title.isEmpty()) "日志详情" else "$title-日志"
    }

    fun headersToJson(headers: String): String = queryStringToJson(headers, "\n", ":")

    fun queryToJson(url: String): String {
        val json = JSONObject()
        val uri = Uri.parse(url)
        uri.queryParameterNames.forEach { key ->
            json.put(key, uri.getQueryParameter(key).orEmpty())
        }
        return json.toString(2)
    }

    fun bodyToJson(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "{}"
        return runCatching { JSONObject(trimmed).toString(2) }
            .getOrElse {
                runCatching {
                    queryStringToJson(trimmed, "&", "=")
                }.getOrDefault(trimmed)
            }
    }

    fun queryStringToJson(
        queryString: String,
        pairDelimiter: String = "&",
        keyValueDelimiter: String = "=",
    ): String {
        val jsonObject = JSONObject()
        queryString.split(pairDelimiter).forEach { param ->
            val pair = param.split(keyValueDelimiter, limit = 2)
            if (pair.size != 2) return@forEach
            val key = pair[0].trim()
            val value = pair[1].trim()
            when {
                key.equals("cookie", ignoreCase = true) -> {
                    jsonObject.put(key, nestedHeaderObject(param, ";"))
                }
                key.equals("extension", ignoreCase = true) -> {
                    jsonObject.put(key, nestedHeaderObject(param, "&"))
                }
                else -> jsonObject.put(key, value)
            }
        }
        return jsonObject.toString(2)
    }

    private fun nestedHeaderObject(raw: String, delimiter: String): JSONObject {
        val content = raw.substringAfter(":", "").trim()
        val nested = JSONObject()
        content.split(delimiter)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { part ->
                val pieces = part.split("=", limit = 2)
                nested.put(pieces[0].trim(), if (pieces.size > 1) pieces[1].trim() else "")
            }
        return nested
    }

    fun attachTreeOrText(
        context: Context,
        container: FrameLayout,
        fallbackScroll: ScrollView,
        fallbackText: TextView,
        jsonString: String,
        plainText: String,
    ): FlattenTreeView? {
        container.removeAllViews()
        fallbackText.text = plainText
        if (jsonString.isBlank()) {
            container.isVisible = false
            fallbackScroll.isVisible = true
            return null
        }
        return runCatching {
            FlattenTreeView(
                context = context,
                jsonStr = jsonString,
                needShowSearchBar = true,
                ignoreCase = true,
                allTreeNodesExpanded = false,
            ).also { tree ->
                container.addView(
                    tree,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                container.isVisible = true
                fallbackScroll.isVisible = false
            }
        }.getOrElse {
            container.isVisible = false
            fallbackScroll.isVisible = true
            null
        }
    }

    fun bindTreeActions(
        context: Context,
        tree: FlattenTreeView?,
        actions: LinearLayout,
        modify: TextView,
        expandAll: TextView,
        collapseAll: TextView,
        fab: FloatingActionButton,
        treeContainer: FrameLayout,
        fallbackScroll: ScrollView,
    ) {
        val btnBg = DebugDrawables.solidRound(context, R.color.debug_primary)
        modify.background = btnBg
        expandAll.background = btnBg
        collapseAll.background = btnBg
        actions.isVisible = tree != null
        fab.isVisible = tree != null
        if (tree == null) return

        expandAll.setOnClickListener { tree.expandAll() }
        collapseAll.setOnClickListener { tree.collapseAll() }
        modify.setOnClickListener { showModifyDialog(context, tree) }
        fab.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.debug_fab_cyan),
        )
        fab.setOnClickListener {
            val showPlain = !fallbackScroll.isVisible
            fallbackScroll.isVisible = showPlain
            tree.isVisible = !showPlain
            treeContainer.isVisible = !showPlain
            fab.backgroundTintList = ColorStateList.valueOf(
                if (showPlain) {
                    ContextCompat.getColor(context, R.color.debug_primary)
                } else {
                    Color.CYAN
                },
            )
        }
    }

    private fun showModifyDialog(context: Context, tree: FlattenTreeView) {
        val input = LayoutInflater.from(context).inflate(R.layout.dialog_net_record_modify, null) as EditText
        input.setText(tree.currentJson())
        AlertDialog.Builder(context)
            .setTitle("Modify")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val content = input.text.toString().trim()
                if (content.isEmpty()) {
                    Toast.makeText(context, "Invalid json format: Empty String.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                runCatching {
                    tree.updateTreeNodes(content)
                }.onFailure { error ->
                    if (error is JSONException || error is JsonParseException) {
                        Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
