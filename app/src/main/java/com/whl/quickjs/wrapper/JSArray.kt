package com.whl.quickjs.wrapper

/**
 * JS 数组抽象（JNI 绑定要求保留 com.whl.quickjs.wrapper 包名）。
 */
interface JSArray {
    fun get(index: Int): Any?
    fun length(): Int
    fun set(value: Any?, index: Int)
}
