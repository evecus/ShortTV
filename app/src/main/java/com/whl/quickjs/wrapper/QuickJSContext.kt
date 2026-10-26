package com.whl.quickjs.wrapper

import java.io.Closeable

/**
 * QuickJS 运行时上下文。
 *
 * **注意**：本类的包名/类名/native 方法名由 libquickjs-android-wrapper.so 的
 * JNI 动态绑定决定（Java_com_whl_quickjs_wrapper_QuickJSContext_*），
 * 严禁重命名或混淆。
 */
class QuickJSContext private constructor(
    private val creator: JSObjectCreator,
) : Closeable {

    private val runtime: Long
    private val context: Long
    private val currentThreadId: Long = Thread.currentThread().id
    private val callFunctionMap = HashMap<Int, JSCallFunction>()
    private val objectRecords = ArrayList<Any>()
    private var destroyed = false

    @Volatile
    var globalObject: JSObject? = null
        private set

    init {
        QuickJSLoaderHolder.load()
        runtime = createRuntime()
        context = createContext(runtime)
        globalObject = getGlobalObject(context)
    }

    // ---------------- native 绑定（签名不可改动） ----------------

    private external fun createRuntime(): Long
    private external fun createContext(runtime: Long): Long
    private external fun destroyContext(context: Long)
    private external fun evaluate(context: Long, script: String, fileName: String): Any?
    private external fun evaluateModule(context: Long, script: String, fileName: String): Any?
    private external fun execute(context: Long, bytecode: ByteArray): Any?
    private external fun compile(context: Long, script: String, fileName: String, module: Boolean): ByteArray
    private external fun getGlobalObject(context: Long): JSObject
    private external fun getProperty(context: Long, objPointer: Long, name: String): Any?
    private external fun setProperty(context: Long, objPointer: Long, name: String, value: Any?)
    private external fun get(context: Long, arrayPointer: Long, index: Int): Any?
    private external fun set(context: Long, arrayPointer: Long, value: Any?, index: Int)
    private external fun length(context: Long, arrayPointer: Long): Int
    private external fun stringify(context: Long, objPointer: Long): String
    private external fun parseJSON(context: Long, json: String): Any?
    private external fun call(context: Long, thisPointer: Long, funcPointer: Long, flag: Int, args: Array<out Any?>): Any?
    private external fun isLiveObject(context: Long, objPointer: Long): Boolean
    private external fun freeValue(context: Long, objPointer: Long)
    private external fun dupValue(context: Long, objPointer: Long)
    private external fun freeDupValue(context: Long, objPointer: Long)
    private external fun getOwnPropertyNames(context: Long, objPointer: Long): Any?
    private external fun runGC(context: Long)
    private external fun getMemoryUsedSize(context: Long): Long
    private external fun setMemoryLimit(context: Long, limit: Int)
    private external fun setGCThreshold(context: Long, threshold: Int)
    private external fun setMaxStackSize(context: Long, size: Int)
    private external fun dumpMemoryUsage(context: Long, message: String)
    private external fun dumpObjects(context: Long, message: String)

    /**
     * native -> Java 回调入口：JS 调用 Java 注册的函数时由 native 反射调用。
     * 方法名与签名由 .so 缓存，不可改动。
     */
    @Suppress("unused")
    fun callFunctionBack(id: Int, args: Array<out Any?>?): Any? {
        checkSameThread()
        val fn = callFunctionMap[id] ?: return null
        val result = fn.call(args ?: emptyArray())
        if (result is JSCallFunction) putCallFunction(result)
        return result
    }

    // ---------------- 公开 API ----------------

    fun evaluate(script: String): Any? = evaluate(script, "<eval>")

    fun evaluate(script: String, fileName: String): Any? {
        requireNotNull(script) { "Script cannot be null with $fileName" }
        checkSameThread()
        checkDestroyed()
        return evaluate(context, script, fileName)
    }

    fun parseJSON(json: String): JSObject? = parseJSON(context, json) as? JSObject

    fun getProperty(obj: JSObject, name: String): Any? {
        checkSameThread(); checkDestroyed()
        return getProperty(context, obj.getPointer(), name)
    }

    fun setProperty(obj: JSObject, name: String, value: Any?) {
        checkSameThread(); checkDestroyed()
        setProperty(context, obj.getPointer(), name, value)
    }

    fun get(array: JSArray, index: Int): Any? {
        checkSameThread(); checkDestroyed()
        return get(context, (array as HasPointer).getPointer(), index)
    }

    fun set(array: JSArray, value: Any?, index: Int) {
        checkSameThread(); checkDestroyed()
        set(context, (array as HasPointer).getPointer(), value, index)
    }

    fun length(array: JSArray): Int {
        checkSameThread(); checkDestroyed()
        return length(context, (array as HasPointer).getPointer())
    }

    fun stringify(obj: JSObject): String {
        checkSameThread(); checkDestroyed()
        return stringify(context, obj.getPointer())
    }

    /**
     * 调用 JS 函数。thisObj 作为 this，funcPointer 为函数指针，flag 为 this 指针标签。
     */
    fun callFunction(thisObj: JSFunction?, funcPointer: Long, flag: Int, args: Array<out Any?>): Any? {
        checkSameThread(); checkDestroyed()
        val thisPtr = (thisObj as? HasPointer)?.getPointer() ?: 0L
        return call(context, thisPtr, funcPointer, flag, args)
    }

    fun hold(obj: JSObject) {
        checkSameThread(); checkDestroyed()
        dupValue(context, obj.getPointer())
    }

    fun freeValue(obj: JSObject) {
        checkSameThread(); checkDestroyed()
        freeValue(context, obj.getPointer())
    }

    fun isLiveObject(obj: JSObject): Boolean {
        checkSameThread(); checkDestroyed()
        return isLiveObject(context, obj.getPointer())
    }

    fun getCallFunctionMapSize(): Int = synchronized(callFunctionMap) { callFunctionMap.size }

    fun removeCallFunction(id: Int) {
        synchronized(callFunctionMap) { callFunctionMap.remove(id) }
    }

    fun runGC() {
        checkSameThread(); checkDestroyed()
        runGC(context)
    }

    fun getMemoryUsedSize(): Long {
        checkSameThread(); checkDestroyed()
        return getMemoryUsedSize(context)
    }

    fun getObjectRecords(): List<Any> = objectRecords

    fun getCreator(): JSObjectCreator = creator

    fun getCurrentThreadId(): Long = currentThreadId

    fun setEnableStackTrace(enabled: Boolean) { /* 仅调试用 */ }

    fun getModuleLoader(): ModuleLoader? = null

    fun setConsole(console: Any?) { /* QuickJS 自带 console */ }

    override fun close() = destroy()

    fun destroy() {
        if (destroyed) return
        destroyed = true
        try {
            destroyContext(context)
        } catch (_: UnsatisfiedLinkError) {
        }
    }

    // ---------------- 内部实现 ----------------

    private fun checkDestroyed() {
        check(!destroyed) { "Can not called this after QuickJSContext was destroyed!" }
    }

    private fun checkSameThread() {
        check(Thread.currentThread().id == currentThreadId) {
            "Must be call same thread in QuickJSContext.create!"
        }
    }

    private fun putCallFunction(fn: JSCallFunction) {
        synchronized(callFunctionMap) { callFunctionMap[fn.hashCode()] = fn }
    }

    /** native 通过 JNI 回调创建 JS 包装对象。 */
    internal fun onObjectCreated(obj: Any) {
        synchronized(objectRecords) { objectRecords.add(obj) }
    }

    internal fun onObjectReleased(obj: Any) {
        synchronized(objectRecords) { objectRecords.remove(obj) }
    }

    companion object {
        /**
         * 创建上下文。使用默认 JSObjectCreator。
         * 注意：QuickJSContext 非线程安全，所有调用必须在同一线程。
         */
        @JvmStatic
        fun create(): QuickJSContext = QuickJSContext(DefaultCreator)

        @JvmStatic
        fun create(creator: JSObjectCreator): QuickJSContext = QuickJSContext(creator)

        private object QuickJSLoaderHolder {
            fun load() {
                try {
                    com.whl.quickjs.android.QuickJSLoader.init()
                } catch (_: UnsatisfiedLinkError) {
                    throw QuickJSException(
                        "The so library must be initialized before createContext! " +
                            "QuickJSLoader.init should be called on the Android platform.",
                    )
                }
            }
        }
    }
}

/** 默认对象工厂。 */
private object DefaultCreator : JSObjectCreator {
    override fun newObject(context: QuickJSContext, pointer: Long): JSObject =
        QuickJSObject(context, pointer)

    override fun newArray(context: QuickJSContext, pointer: Long): JSArray =
        QuickJSArray(context, pointer)

    override fun newFunction(
        context: QuickJSContext,
        pointer: Long,
        thisPointer: Long,
        thisPointerTag: Int,
    ): JSFunction = QuickJSFunction(context, pointer, thisPointer, thisPointerTag)
}
