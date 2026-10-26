package com.whl.quickjs.wrapper

/**
 * JS 对象工厂。native 层通过 JNI 回调 newObject/newArray/newFunction
 * （方法签名必须与 .so 中缓存的一致）。
 */
interface JSObjectCreator {
    fun newObject(context: QuickJSContext, pointer: Long): JSObject
    fun newArray(context: QuickJSContext, pointer: Long): JSArray
    fun newFunction(context: QuickJSContext, pointer: Long, thisPointer: Long, thisPointerTag: Int): JSFunction
}
