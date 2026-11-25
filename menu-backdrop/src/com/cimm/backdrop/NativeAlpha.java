package com.cimm.backdrop;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Sets the opacity of a native macOS window (or layer) by its pointer, through the Objective-C runtime (Java's foreign
 * function API): [window setAlphaValue:] for an NSWindow, [layer setOpacity:] for a CALayer. Called on Bitwig's UI
 * thread (the one that shows the windows). Everything fails quietly: no opacity change, the popup just appears.
 */
final class NativeAlpha {
    private NativeAlpha() {}

    private static volatile boolean ready, broken;
    private static MethodHandle sel, sendD, sendF, responds, className;
    private static MemorySegment setAlpha, setOpacity, respondsTo;

    private static synchronized boolean init() {
        if (ready || broken) return ready;
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global());
            MemorySegment send = objc.find("objc_msgSend").orElseThrow();
            sel = linker.downcallHandle(objc.find("sel_registerName").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS));
            className = linker.downcallHandle(objc.find("object_getClassName").orElseThrow(), FunctionDescriptor.of(ADDRESS, JAVA_LONG));
            sendD = linker.downcallHandle(send, FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS, JAVA_DOUBLE));
            sendF = linker.downcallHandle(send, FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS, JAVA_FLOAT));
            responds = linker.downcallHandle(send, FunctionDescriptor.of(JAVA_BYTE, JAVA_LONG, ADDRESS, ADDRESS));
            setAlpha = selector("setAlphaValue:");
            setOpacity = selector("setOpacity:");
            respondsTo = selector("respondsToSelector:");
            ready = true;
        } catch (Throwable t) {
            broken = true;
            Backdrop.MOD.log("popup fade unavailable (native calls)", t);
        }
        return ready;
    }

    private static MemorySegment selector(String name) throws Throwable {
        return (MemorySegment) sel.invokeExact(Arena.global().allocateFrom(name));
    }

    /** The Objective-C class of the object at ptr (for the log). */
    static String classOf(long ptr) {
        if (ptr == 0 || !init()) return "?";
        try { return ((MemorySegment) className.invokeExact(ptr)).reinterpret(256).getString(0); }
        catch (Throwable t) { return "?"; }
    }

    /** Sets the opacity (0..1); false if the object can't take one. */
    static boolean set(long ptr, double alpha) {
        if (ptr == 0 || !init()) return false;
        try {
            if ((byte) responds.invokeExact(ptr, respondsTo, setAlpha) != 0) {
                sendD.invokeExact(ptr, setAlpha, alpha);
                return true;
            }
            if ((byte) responds.invokeExact(ptr, respondsTo, setOpacity) != 0) {
                sendF.invokeExact(ptr, setOpacity, (float) alpha);
                return true;
            }
        } catch (Throwable t) {
            broken = true; ready = false;
            Backdrop.MOD.log("popup fade failed; turned off", t);
        }
        return false;
    }
}
