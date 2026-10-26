package com.whl.quickjs.wrapper

/**
 * JS 对象抽象（JNI 绑定要求保留 com.whl.quickjs.wrapper 包名）。
 */
interface JSObject {
    fun getPointer(): Long
    fun getContext(): QuickJSContext
    fun getProperty(name: String): Any?
    fun getStringProperty(name: String): String?
    fun getIntProperty(name: String): Int?
    fun getBooleanProperty(name: String): Boolean?
    fun getDoubleProperty(name: String): Double?
    fun getLongProperty(name: String): Long?
    fun getJSObjectProperty(name: String): JSObject?
    fun getJSArrayProperty(name: String): JSArray?
    fun getJSFunctionProperty(name: String): JSFunction?
    fun setProperty(name: String, value: Any?)
    fun stringify(): String
    fun hold()
    fun release()
    fun getRefCount(): Int
    fun isAlive(): Boolean
}
