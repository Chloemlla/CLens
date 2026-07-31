package com.chloemlla.clens.ui

import android.os.Build
import android.view.InputDevice
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Detects whether an external (physical) keyboard is connected to the device.
 * Returns `false` if only the built-in virtual/on-screen keyboard is available.
 */
fun hasExternalKeyboard(): Boolean {
    val devices = InputDevice.getDeviceIds()
    return devices.any { id ->
        InputDevice.getDevice(id)?.let { device ->
            // InputDevice.isExternal was added in API 29. On older releases,
            // the keyboard type remains the available physical-keyboard signal.
            val isExternal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && device.isExternal
            isExternal || device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE
        } == true
    }
}

/** Represents a keyboard shortcut with its key combination. */
sealed class KeyboardShortcut(val label: String) {
    data object FocusSearch : KeyboardShortcut("Ctrl+K")
    data object SearchInDocument : KeyboardShortcut("Ctrl+F")
    data object Save : KeyboardShortcut("Ctrl+S")
    data object Edit : KeyboardShortcut("Ctrl+E")
    data object Delete : KeyboardShortcut("Ctrl+D")
    data object Escape : KeyboardShortcut("Escape")
    data object TabNext : KeyboardShortcut("Tab")
    data object TabPrev : KeyboardShortcut("Shift+Tab")
    data object NewDocument : KeyboardShortcut("Ctrl+N")
    data object NavigateUp : KeyboardShortcut("Arrow Up")
    data object NavigateDown : KeyboardShortcut("Arrow Down")
    data object OpenDocument : KeyboardShortcut("Enter")
    data object Unknown : KeyboardShortcut("")
}

/** Route a raw key event to a [KeyboardShortcut] or `Unknown`. */
fun routeKeyEvent(event: KeyEvent): KeyboardShortcut {
    if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
        return KeyboardShortcut.Unknown
    }

    val key = event.key
    val isCtrl = event.isCtrlPressed
    val isShift = event.isShiftPressed
    val isMeta = event.isMetaPressed

    // Ctrl shortcuts
    if (isCtrl && !isShift && !isMeta) {
        return when (key) {
            androidx.compose.ui.input.key.Key.K -> KeyboardShortcut.FocusSearch
            androidx.compose.ui.input.key.Key.F -> KeyboardShortcut.SearchInDocument
            androidx.compose.ui.input.key.Key.S -> KeyboardShortcut.Save
            androidx.compose.ui.input.key.Key.E -> KeyboardShortcut.Edit
            androidx.compose.ui.input.key.Key.D -> KeyboardShortcut.Delete
            androidx.compose.ui.input.key.Key.N -> KeyboardShortcut.NewDocument
            else -> KeyboardShortcut.Unknown
        }
    }

    // No modifier shortcuts
    if (!isCtrl && !isShift && !isMeta) {
        return when (key) {
            androidx.compose.ui.input.key.Key.Escape -> KeyboardShortcut.Escape
            androidx.compose.ui.input.key.Key.Tab -> KeyboardShortcut.TabNext
            androidx.compose.ui.input.key.Key.Enter -> KeyboardShortcut.OpenDocument
            androidx.compose.ui.input.key.Key.DirectionUp -> KeyboardShortcut.NavigateUp
            androidx.compose.ui.input.key.Key.DirectionDown -> KeyboardShortcut.NavigateDown
            else -> KeyboardShortcut.Unknown
        }
    }

    // Shift+Tab
    if (isShift && !isCtrl && !isMeta && key == androidx.compose.ui.input.key.Key.Tab) {
        return KeyboardShortcut.TabPrev
    }

    return KeyboardShortcut.Unknown
}
