package com.renovation.ledger.dsl

import android.util.Log
import androidx.annotation.IntDef
import com.renovation.ledger.BuildConfig

inline fun <R> logTime(
    methodName: String,
    tag: String = "TimeLogger",
    @LogLevel level: Int = LogLevel.INFO,
    crossinline block: () -> R,
): R {
    val startTime = System.currentTimeMillis()
    val res = block()
    val costTime = System.currentTimeMillis() - startTime
    LogUtil.log("Method $methodName cost $costTime ms", tag, level)
    return res
}

inline fun <R> measureTimeMillis(crossinline block: () -> R): Pair<R, Long> {
    val startTime = System.currentTimeMillis()
    val res = block()
    val costTime = System.currentTimeMillis() - startTime
    return Pair(res, costTime)
}

@IntDef(
    LogLevel.VERBOSE,
    LogLevel.DEBUG,
    LogLevel.INFO,
    LogLevel.WARN,
    LogLevel.ERROR,
    LogLevel.NOTHING,
)
@Retention(AnnotationRetention.SOURCE)
annotation class LogLevel {
    companion object {
        const val VERBOSE = 1
        const val DEBUG = 2
        const val INFO = 3
        const val WARN = 4
        const val ERROR = 5
        const val NOTHING = 6
    }
}

object LogUtil {
    private var level = LogLevel.VERBOSE
    private const val TAG = "TimeLogger"

    fun setLevel(@LogLevel level: Int) {
        this.level = level
    }

    fun log(msg: String, tag: String = TAG, @LogLevel level: Int = LogLevel.INFO) {
        when (level) {
            LogLevel.VERBOSE -> v(tag = tag, msg = msg)
            LogLevel.DEBUG -> d(tag = tag, msg = msg)
            LogLevel.INFO -> i(tag = tag, msg = msg)
            LogLevel.WARN -> w(tag = tag, msg = msg)
            LogLevel.ERROR -> e(tag = tag, msg = msg)
            LogLevel.NOTHING -> {}
        }
    }

    fun v(msg: String, tag: String = TAG) {
        if (level <= LogLevel.VERBOSE) Log.v(tag, msg)
    }

    fun d(msg: String, tag: String = TAG) {
        if (level <= LogLevel.DEBUG) Log.d(tag, msg)
    }

    fun i(msg: String, tag: String = TAG) {
        if (level <= LogLevel.INFO) Log.i(tag, msg)
    }

    fun w(msg: String, tag: String = TAG) {
        if (level <= LogLevel.WARN) Log.w(tag, msg)
    }

    fun e(msg: String, tag: String = TAG) {
        if (level <= LogLevel.ERROR) Log.e(tag, msg)
    }
}

inline fun logV(tag: String, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.v(tag, msg()) }
}

inline fun logV(tag: String, tr: Throwable, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.v(tag, msg(), tr) }
}

inline fun logD(tag: String, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.d(tag, msg()) }
}

inline fun logD(tag: String, tr: Throwable, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.d(tag, msg(), tr) }
}

inline fun logI(tag: String, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.i(tag, msg()) }
}

inline fun logI(tag: String, tr: Throwable, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.i(tag, msg(), tr) }
}

inline fun logW(tag: String, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.w(tag, msg()) }
}

inline fun logW(tag: String, tr: Throwable, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.w(tag, msg(), tr) }
}

inline fun logE(tag: String, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.e(tag, msg()) }
}

inline fun logE(tag: String, tr: Throwable, msg: Supplier<String>) {
    BuildConfig.DEBUG.invoke { Log.e(tag, msg(), tr) }
}
