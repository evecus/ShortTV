package com.whl.quickjs.wrapper

/**
 * 内部指针访问接口：JSObject / JSArray / JSFunction 包装类共用。
 * native 层只认 JSObjectCreator 产出的具体类型，Java 侧通过本接口取指针。
 */
internal interface HasPointer {
    fun getPointer(): Long
}
