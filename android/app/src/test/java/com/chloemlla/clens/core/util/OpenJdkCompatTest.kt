package com.chloemlla.clens.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.Arrays
import java.util.IllegalFormatException
import java.util.Locale

/**
 * Guards for Android 15 / OpenJDK behavior changes called out by vivo doc 797 §1.3.
 * Production code must avoid %0$ format indices and (T[]) list.toArray() casts.
 */
class OpenJdkCompatTest {
    @Test
    fun zeroIndexedFormatArgumentIsRejected() {
        try {
            String.format(Locale.US, "Hello, %0\$s", "Alice")
            fail("expected IllegalFormatException for %0$ index")
        } catch (_: IllegalFormatException) {
            // Android 15+ / modern OpenJDK: argument indices are 1-based and validated.
        }
    }

    @Test
    fun oneIndexedFormatArgumentStillWorks() {
        val message = String.format(Locale.US, "Hello, %1\$s", "Alice")
        assertEquals("Hello, Alice", message)
    }

    @Test
    fun plainPositionalFormatUsedByProductStillWorks() {
        // Mirrors GeoMapPickerDialog / chart formatting style.
        val text = String.format(Locale.US, "lat=%.6f", 31.230416)
        assertEquals("lat=31.230416", text)
    }

    @Test
    fun asListToArrayHasObjectComponentType() {
        val raw = Arrays.asList("one", "two").toArray()
        assertEquals(Any::class.java, raw.javaClass.componentType)
        try {
            @Suppress("UNCHECKED_CAST")
            val cast = raw as Array<String>
            // If the cast somehow succeeds, component access may still be unsafe; force failure.
            fail("unsafe cast should not succeed; got ${cast.javaClass}")
        } catch (_: ClassCastException) {
            // Expected on Android 15+ OpenJDK change.
        }
    }

    @Test
    fun typedToArrayKeepsStringComponentType() {
        val elements = Arrays.asList("two", "one").toArray(arrayOfNulls<String>(0))
        assertEquals(String::class.java, elements.javaClass.componentType)
        assertEquals("two", elements[0])
    }
}
