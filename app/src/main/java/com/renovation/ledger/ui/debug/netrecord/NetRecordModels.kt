package com.renovation.ledger.ui.debug.netrecord

data class NetRecordBean(
    val request: RequestBean,
    val response: ResponseBean,
) {
    data class RequestBean(
        val method: String,
        val url: String,
        val header: String,
        val postBody: String,
        val curl: String,
        val startTimeMs: Long,
        var searchKey: String = "",
    )

    data class ResponseBean(
        val body: String,
        val statusCode: Int,
        val bodySizeKb: String,
        val durationMs: Long,
    )
}
