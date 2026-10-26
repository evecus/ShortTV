package com.whl.quickjs.wrapper

/**
 * JS 函数抽象（JNI 绑定要求保留 com.whl.quickjs.wrapper 包名）。
 */
interface JSFunction {
    fun call(vararg args: Any?): Any?
    fun callVoid(vararg args: Any?)
}
