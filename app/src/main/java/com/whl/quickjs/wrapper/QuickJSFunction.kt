package com.whl.quickjs.wrapper

/** JSFunction 默认实现。 */
internal class QuickJSFunction(
    private val context: QuickJSContext,
    private val pointer: Long,
    private val thisPointer: Long,
    private val thisPointerTag: Int,
) : JSFunction, HasPointer {

    private var refCount = 0

    init {
        refCount++
        context.onObjectCreated(this)
    }

    override fun getPointer(): Long = pointer

    override fun call(vararg args: Any?): Any? =
        context.callFunction(this, thisPointer, thisPointerTag, args)

    override fun callVoid(vararg args: Any?) {
        call(*args)
    }
}
