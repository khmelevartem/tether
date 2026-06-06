// Adapted from FileKit (MIT, github.com/vinceglb/FileKit), whose Foundation bridge derives from IntelliJ Community (Apache-2.0).
@file:Suppress("ktlint:standard:function-naming", "FunctionName")

package com.tubetoast.tether.transfer.mac

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Pointer

internal interface FoundationLibrary : Library {
    fun objc_getClass(className: String?): ID?

    fun objc_allocateClassPair(supercls: ID?, name: String?, extraBytes: Int): ID?

    fun objc_registerClassPair(cls: ID?)

    fun sel_registerName(selectorName: String?): Pointer?

    fun class_addMethod(cls: ID?, selName: Pointer?, imp: Callback?, types: String?): Boolean

    fun CFStringGetCString(theString: ID?, buffer: ByteArray?, bufferSize: Int, encoding: Int): Byte

    fun CFStringGetLength(theString: ID?): Int

    fun CFStringConvertEncodingToNSStringEncoding(cfEncoding: Long): Long

    @Suppress("ktlint:standard:property-naming", "ConstPropertyName")
    companion object {
        const val kCFStringEncodingUTF8: Int = 0x08000100
        const val kCFStringEncodingUTF16LE: Int = 0x14000100
    }
}
