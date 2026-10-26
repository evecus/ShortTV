package com.whl.quickjs.wrapper

/**
 * ES Module 加载器（本工程仅用脚本模式，保留接口以满足 native 反射查找）。
 */
interface ModuleLoader {
    fun loadModule(name: String): String?
}
