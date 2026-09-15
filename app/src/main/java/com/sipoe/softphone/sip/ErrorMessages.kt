package com.sipoe.softphone.sip

fun mapRegistrationError(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val text = raw.lowercase()
    return when {
        text.contains("401") || text.contains("403") ||
            text.contains("unauthorized") || text.contains("forbidden") ->
            "认证失败,请检查用户名和密码"

        text.contains("404") || text.contains("not found") -> "服务器未找到该账号"
        text.contains("408") || text.contains("timeout") -> "服务器无响应,请检查网络和代理地址"
        text.contains("500") -> "服务器内部错误"
        text.contains("502") || text.contains("bad gateway") -> "网关错误"
        text.contains("503") || text.contains("unavailable") -> "服务暂不可用,请稍后重试"
        text.contains("407") || text.contains("proxy authentication") -> "代理要求认证"
        text.contains("dns") || text.contains("host") -> "无法解析服务器地址"
        text.contains("ioerror") || text.contains("io error") -> "网络连接失败"
        text.contains("unreachable") -> "网络不可达"
        text.contains("transport") || text.contains("network") -> "网络连接异常"
        text.contains("registration failed") -> "注册失败,请检查服务器地址与账号配置"
        else -> raw
    }
}

fun mapCallError(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val text = raw.lowercase()
    return when {
        text.contains("busy") -> "对方忙"
        text.contains("declined") || text.contains("486") -> "对方已拒接"
        text.contains("not found") || text.contains("404") -> "号码不存在"
        text.contains("timeout") || text.contains("408") -> "呼叫超时"
        text.contains("forbidden") || text.contains("403") -> "呼叫被拒绝"
        text.contains("unavailable") || text.contains("503") -> "服务不可用"
        text.contains("unauthorized") || text.contains("401") -> "认证失败"
        else -> raw
    }
}
