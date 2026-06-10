// Adapted from FileKit (MIT, github.com/vinceglb/FileKit), whose Foundation bridge derives from IntelliJ Community (Apache-2.0).
@file:Suppress("ktlint:standard:function-naming", "FunctionName", "ktlint:standard:property-naming")

package com.tubetoast.tether.platform.mac

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.util.Collections

internal object Foundation {
    private val myFoundationLibrary: FoundationLibrary = Native.load(
        "Foundation",
        FoundationLibrary::class.java,
        Collections.singletonMap("jna.encoding", "UTF8"),
    )
    private val myObjcMsgSend: Function by lazy {
        val nativeLibrary = (Proxy.getInvocationHandler(myFoundationLibrary) as Library.Handler).nativeLibrary
        nativeLibrary.getFunction("objc_msgSend")
    }

    fun getObjcClass(className: String?): ID? = myFoundationLibrary.objc_getClass(className)

    fun createSelector(s: String?): Pointer? = myFoundationLibrary.sel_registerName(s)

    private fun prepInvoke(id: ID?, selector: Pointer?, args: Array<out Any?>): Array<Any?> {
        val invokArgs = arrayOfNulls<Any>(args.size + 2)
        invokArgs[0] = id
        invokArgs[1] = selector
        System.arraycopy(args, 0, invokArgs, 2, args.size)
        return invokArgs
    }

    // arm64-safe: fixed args via invokeLong, NOT a vararg Library method
    fun invoke(id: ID?, selector: Pointer?, vararg args: Any?): ID =
        ID(myObjcMsgSend.invokeLong(prepInvoke(id, selector, args)))

    fun invoke(cls: String?, selector: String?, vararg args: Any?): ID =
        invoke(getObjcClass(cls), createSelector(selector), *args)

    fun invoke(id: ID?, selector: String?, vararg args: Any?): ID =
        invoke(id, createSelector(selector), *args)

    fun isNil(id: ID?): Boolean = id == null || ID.NIL == id

    private fun convertCFEncodingToNS(cfEncoding: Long): Long =
        myFoundationLibrary.CFStringConvertEncodingToNSStringEncoding(cfEncoding) and 0xffffffffffL

    fun nsString(s: String?): ID = if (s == null) ID.NIL else NSString.create(s)

    fun toStringViaUTF8(cfString: ID?): String? {
        if (ID.NIL == cfString) return null
        val lengthInChars = myFoundationLibrary.CFStringGetLength(cfString)
        val potentialLengthInBytes = 3 * lengthInChars + 1
        val buffer = ByteArray(potentialLengthInBytes)
        val ok = myFoundationLibrary.CFStringGetCString(
            cfString,
            buffer,
            buffer.size,
            FoundationLibrary.kCFStringEncodingUTF8,
        )
        if (ok.toInt() == 0) throw RuntimeException("Could not convert string")
        return Native.toString(buffer)
    }

    private fun allocateObjcClassPair(superCls: ID?, name: String?): ID? =
        myFoundationLibrary.objc_allocateClassPair(superCls, name, 0)

    private fun registerObjcClassPair(cls: ID?) = myFoundationLibrary.objc_registerClassPair(cls)

    private fun addMethod(cls: ID?, selectorName: Pointer?, impl: Callback?, types: String?): Boolean =
        myFoundationLibrary.class_addMethod(cls, selectorName, impl, types)

    private var ourRunnableCallback: Callback? = null
    private var runnableSupportFailed = false
    private val ourMainThreadRunnables: MutableMap<Long, RunnableInfo> = HashMap()
    private var ourCurrentRunnableCount: Long = 0
    private val RUNNABLE_LOCK = Any()

    fun executeOnMainThread(withAutoreleasePool: Boolean, waitUntilDone: Boolean, runnable: Runnable) {
        val info = RunnableInfo(runnable, withAutoreleasePool)
        val count: Long
        synchronized(RUNNABLE_LOCK) {
            initRunnableSupport()
            count = ++ourCurrentRunnableCount
            ourMainThreadRunnables[count] = info
        }
        val runnableClass = getObjcClass("TetherObjcRunnable")
        val runnableObject = invoke(invoke(runnableClass, "alloc"), "init")
        val keyObject = invoke(invoke("NSNumber", "numberWithLong:", count), "retain")
        invoke(
            runnableObject,
            "performSelectorOnMainThread:withObject:waitUntilDone:",
            createSelector("run:"),
            keyObject,
            waitUntilDone,
        )
        invoke(runnableObject, "release")
        info.thrown?.let { throw it }
    }

    // NOT THREAD-SAFE — call under RUNNABLE_LOCK only.
    private fun initRunnableSupport() {
        if (ourRunnableCallback != null) return
        if (runnableSupportFailed) {
            throw RuntimeException("TetherObjcRunnable registration failed on a previous attempt")
        }
        val runnableClass = allocateObjcClassPair(getObjcClass("NSObject"), "TetherObjcRunnable")
        registerObjcClassPair(runnableClass)
        val callback: Callback = object : Callback {
            fun callback(self: ID?, selector: String?, keyObject: ID?) {
                val key: Long
                try {
                    key = myObjcMsgSend.invokeLong(prepInvoke(keyObject, createSelector("longValue"), emptyArray()))
                } finally {
                    invoke(keyObject, "release")
                }
                val info: RunnableInfo? = synchronized(RUNNABLE_LOCK) { ourMainThreadRunnables.remove(key) }
                if (info == null) return
                var pool: ID? = null
                try {
                    if (info.myUseAutoreleasePool) pool = invoke("NSAutoreleasePool", "new")
                    info.myRunnable.run()
                } catch (t: Throwable) {
                    info.thrown = t
                } finally {
                    if (pool != null) invoke(pool, "release")
                }
            }
        }
        if (!addMethod(runnableClass, createSelector("run:"), callback, "v@:*")) {
            runnableSupportFailed = true
            throw RuntimeException("Unable to add method to TetherObjcRunnable objc class")
        }
        ourRunnableCallback = callback
    }

    private class RunnableInfo(
        val myRunnable: Runnable,
        val myUseAutoreleasePool: Boolean,
    ) {
        @Volatile
        var thrown: Throwable? = null
    }

    private object NSString {
        private val nsStringCls = getObjcClass("NSString")
        private val stringSel = createSelector("string")
        private val allocSel = createSelector("alloc")
        private val autoreleaseSel = createSelector("autorelease")
        private val initSel = createSelector("initWithBytes:length:encoding:")
        private val nsEncodingUTF16LE = convertCFEncodingToNS(FoundationLibrary.kCFStringEncodingUTF16LE.toLong())

        fun create(s: String): ID {
            if (s.isEmpty()) return invoke(nsStringCls, stringSel)
            val utf16 = s.toByteArray(StandardCharsets.UTF_16LE)
            val empty = invoke(nsStringCls, allocSel)
            val init = invoke(empty, initSel, utf16, utf16.size, nsEncodingUTF16LE)
            return invoke(init, autoreleaseSel)
        }
    }

    class NSAutoreleasePool {
        private val myDelegate = invoke(invoke("NSAutoreleasePool", "alloc"), "init")

        fun drain() {
            invoke(myDelegate, "drain")
        }
    }
}
