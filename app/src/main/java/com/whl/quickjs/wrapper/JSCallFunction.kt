package com.whl.quickjs.wrapper

/**
 * Java 回调函数（供 JS 调用）。
 */
fun interface JSCallFunction {
    fun call(args: Array<out Any?>?): Any?
}
