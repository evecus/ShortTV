package com.whl.quickjs.wrapper

/** JSObject 默认实现。 */
internal class QuickJSObject(
    private val context: QuickJSContext,
    private val pointer: Long,
) : JSObject, HasPointer {

    private var refCount = 0

    init {
        refCount++
        context.onObjectCreated(this)
    }

    override fun getPointer(): Long = pointer

    override fun getContext(): QuickJSContext = context

    override fun getProperty(name: String): Any? = context.getProperty(this, name)

    override fun getStringProperty(name: String): String? = context.getProperty(this, name) as? String

    override fun getIntProperty(name: String): Int? = (context.getProperty(this, name) as? Number)?.toInt()

    override fun getBooleanProperty(name: String): Boolean? = context.getProperty(this, name) as? Boolean

    override fun getDoubleProperty(name: String): Double? = (context.getProperty(this, name) as? Number)?.toDouble()

    override fun getLongProperty(name: String): Long? = (context.getProperty(this, name) as? Number)?.toLong()

    override fun getJSObjectProperty(name: String): JSObject? = context.getProperty(this, name) as? JSObject

    override fun getJSArrayProperty(name: String): JSArray? = context.getProperty(this, name) as? JSArray

    override fun getJSFunctionProperty(name: String): JSFunction? = context.getProperty(this, name) as? JSFunction

    override fun setProperty(name: String, value: Any?) = context.setProperty(this, name, value)

    override fun stringify(): String = context.stringify(this)

    override fun hold() = context.hold(this)

    override fun release() {
        refCount--
        if (refCount <= 0) context.onObjectReleased(this)
    }

    override fun getRefCount(): Int = refCount

    override fun isAlive(): Boolean = context.isLiveObject(this)
}
