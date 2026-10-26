package com.whl.quickjs.wrapper

/** JSArray 默认实现。 */
internal class QuickJSArray(
    private val context: QuickJSContext,
    private val pointer: Long,
) : JSArray, HasPointer {

    private var refCount = 0

    init {
        refCount++
        context.onObjectCreated(this)
    }

    override fun getPointer(): Long = pointer

    override fun get(index: Int): Any? = context.get(this, index)

    override fun length(): Int = context.length(this)

    override fun set(value: Any?, index: Int) = context.set(this, value, index)
}
