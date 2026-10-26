package com.whl.quickjs.wrapper

/**
 * QuickJS 封装异常（JNI 绑定要求保留 com.whl.quickjs.wrapper 包名）。
 */
class QuickJSException : RuntimeException {
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
