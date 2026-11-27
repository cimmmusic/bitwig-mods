package com.cimm.minimal;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Rounded corners for Bitwig's popup windows (menus, dropdown lists, tooltips) on macOS.
 *
 * A popup is a borderless NSWindow (CocoaWindowHandle's native pointer, IBe.Hnt(), once shown). Its content view is made
 * layer-backed with a corner radius that clips everything inside (Bitwig's Metal layer included), the window is made
 * see-through so the clipped corners show what is behind, and a 1 pt border is drawn around the rounded edge (the
 * square border Bitwig draws inside is clipped at the corners). The shadow is recomputed for the new shape.
 *
 * Objective-C through Java's foreign function API, on Bitwig's UI thread (the main thread on macOS). Any failure turns
 * the feature off for the session and the popups simply stay square.
 */
final class NativeCorners {
    private NativeCorners() {}

    private static volatile boolean ready, broken;
    private static MethodHandle sel, getClass, sendP, sendV, sendB, sendL, sendD, responds, cgColor, getD, getB;
    private static MemorySegment cornerRadius, isOpaque, isVisible, parentWindow, removeChild, addChild, orderOut, orderFront;
    private static MethodHandle sendLL;
    private static MemorySegment respondsTo, contentView, window, delegate, superlayer, setOpaque, setBackgroundColor, clearColor, setWantsLayer,
            layer, setCornerRadius, setMasksToBounds, setBorderWidth, setBorderColor, invalidateShadow, setHasShadow;
    private static long nsColor;
    private static int logs;

    private static synchronized boolean init() {
        if (ready || broken) return ready;
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global());
            SymbolLookup cg = SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global());
            MemorySegment send = objc.find("objc_msgSend").orElseThrow();
            sel = linker.downcallHandle(objc.find("sel_registerName").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS));
            getClass = linker.downcallHandle(objc.find("objc_getClass").orElseThrow(), FunctionDescriptor.of(JAVA_LONG, ADDRESS));
            sendP = linker.downcallHandle(send, FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS));
            sendV = linker.downcallHandle(send, FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS));
            sendB = linker.downcallHandle(send, FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS, JAVA_BYTE));
            sendL = linker.downcallHandle(send, FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS, JAVA_LONG));
            sendD = linker.downcallHandle(send, FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS, JAVA_DOUBLE));
            responds = linker.downcallHandle(send, FunctionDescriptor.of(JAVA_BYTE, JAVA_LONG, ADDRESS, ADDRESS));
            getD = linker.downcallHandle(send, FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG, ADDRESS));
            getB = linker.downcallHandle(send, FunctionDescriptor.of(JAVA_BYTE, JAVA_LONG, ADDRESS));
            cornerRadius = s("cornerRadius");
            isVisible = s("isVisible");
            parentWindow = s("parentWindow");
            removeChild = s("removeChildWindow:");
            addChild = s("addChildWindow:ordered:");
            orderOut = s("orderOut:");
            orderFront = s("orderFront:");
            sendLL = linker.downcallHandle(send, FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG));
            isOpaque = s("isOpaque");
            cgColor = linker.downcallHandle(cg.find("CGColorCreateSRGB").orElseThrow(),
                    FunctionDescriptor.of(JAVA_LONG, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));
            respondsTo = s("respondsToSelector:");
            contentView = s("contentView");
            window = s("window");
            superlayer = s("superlayer");
            delegate = s("delegate");
            setOpaque = s("setOpaque:");
            setBackgroundColor = s("setBackgroundColor:");
            clearColor = s("clearColor");
            setWantsLayer = s("setWantsLayer:");
            layer = s("layer");
            setCornerRadius = s("setCornerRadius:");
            setMasksToBounds = s("setMasksToBounds:");
            setBorderWidth = s("setBorderWidth:");
            setBorderColor = s("setBorderColor:");
            invalidateShadow = s("invalidateShadow");
            setHasShadow = s("setHasShadow:");
            nsColor = (long) getClass.invokeExact(Arena.global().allocateFrom("NSColor"));
            ready = nsColor != 0;
            if (!ready) broken = true;
        } catch (Throwable t) {
            broken = true;
            Minimal.MOD.log("rounded menus unavailable (native calls)", t);
        }
        return ready;
    }

    private static MemorySegment s(String name) throws Throwable {
        return (MemorySegment) sel.invokeExact(Arena.global().allocateFrom(name));
    }

    /** Only real heap objects are ever messaged: a small number or a misaligned value is a handle, not a pointer
     *  (dev.15 messaged Bitwig's window id 0x6ee and crashed). */
    static boolean plausible(long p) { return p >= (1L << 32) && (p & 7) == 0; }

    private static boolean can(long obj, MemorySegment selector) throws Throwable {
        return plausible(obj) && (byte) responds.invokeExact(obj, respondsTo, selector) != 0;
    }

    /** The NSWindow showing a CALayer: the layer's delegate is its NSView, whose window it is. 0 if not (yet) known. */
    static long windowOfLayer(long layerPtr) {
        if (!plausible(layerPtr) || !init()) return 0;
        try {
            if (!can(layerPtr, delegate)) return 0;
            long view = (long) sendP.invokeExact(layerPtr, delegate);
            if (!can(view, window)) return 0;
            long win = (long) sendP.invokeExact(view, window);
            return can(win, contentView) ? win : 0;
        } catch (Throwable t) {
            broken = true; ready = false;
            Minimal.MOD.log("finding the popup window failed; rounded menus off", t);
            return 0;
        }
    }

    /**
     * Rounds the popup's corners (radius in points; 0 puts square corners back). ptr: the native window, or a view in it.
     * Border: sRGB 0..1 and width in points.
     */
    private static int chainLogs;

    /** Whether the window is on screen now. */
    static boolean visible(long win) {
        if (!init()) return false;
        try { return can(win, isVisible) && (byte) getB.invokeExact(win, isVisible) != 0; } catch (Throwable t) { return false; }
    }

    /**
     * A window made see-through while already on screen keeps showing square (macOS composites it as it was when it
     * appeared). Re-attaching it to its parent (or ordering it out and in) makes it composite again, with its new shape.
     * All within one turn of the event loop, so it doesn't visibly blink.
     */
    static void reshow(long win) {
        if (!init()) return;
        try {
            long parent = can(win, parentWindow) ? (long) sendP.invokeExact(win, parentWindow) : 0;
            if (plausible(parent) && can(parent, addChild)) {
                sendL.invokeExact(parent, removeChild, win);
                sendLL.invokeExact(parent, addChild, win, 1L); // NSWindowAbove
            } else if (can(win, orderFront)) {
                sendL.invokeExact(win, orderOut, 0L);
                sendL.invokeExact(win, orderFront, 0L);
            }
            if (reshowLogs++ < 3) Minimal.MOD.log("menu shown again to take its rounded shape (" + (plausible(parent) ? "child window" : "window") + ")");
        } catch (Throwable t) {
            broken = true; ready = false;
            Minimal.MOD.log("re-showing the menu failed; rounded menus off", t);
        }
    }
    private static int reshowLogs;

    /** Diagnostics: what the window and its layers hold now (radius of each layer up the chain, opacity). */
    static String describe(long win, long metalLayer) {
        if (!init()) return "native off";
        try {
            StringBuilder b = new StringBuilder("window " + Long.toHexString(win));
            if (can(win, isOpaque)) b.append(" opaque=").append((byte) getB.invokeExact(win, isOpaque) != 0);
            b.append(" | layer ").append(Long.toHexString(metalLayer));
            long l = metalLayer;
            for (int i = 0; i < 5 && plausible(l); i++) {
                double r = can(l, cornerRadius) ? (double) getD.invokeExact(l, cornerRadius) : -1;
                b.append(i == 0 ? " " : " < ").append(NativeAlpha.classOf(l)).append(" r=").append(Math.round(r * 10) / 10.0);
                if (!can(l, superlayer)) break;
                l = (long) sendP.invokeExact(l, superlayer);
            }
            long view = can(win, contentView) ? (long) sendP.invokeExact(win, contentView) : 0;
            long vl = can(view, layer) ? (long) sendP.invokeExact(view, layer) : 0;
            if (plausible(vl)) b.append(" | content layer ").append(NativeAlpha.classOf(vl)).append(" r=")
                    .append(Math.round((double) getD.invokeExact(vl, cornerRadius) * 10) / 10.0);
            return b.toString();
        } catch (Throwable t) { return "describe failed: " + t; }
    }

    /** Rounds one layer: radius, clipping, and see-through so the clipped corners really show what is behind. */
    private static void roundLayer(long l, double radius) throws Throwable {
        boolean rounded = radius > 0;
        sendD.invokeExact(l, setCornerRadius, radius);
        sendB.invokeExact(l, setMasksToBounds, (byte) (rounded ? 1 : 0));
        if (rounded && can(l, setOpaque)) sendB.invokeExact(l, setOpaque, (byte) 0);
    }

    /**
     * Rounds the popup: the window (see-through), its content view's layer, and Bitwig's Metal layer with every layer
     * it sits in (rounding only the top view did not clip the Metal drawing: menus stayed square). The border goes on
     * the Metal layer, the one actually drawn. metalLayer may be 0 (then only the window and its view are rounded).
     */
    static boolean round(long ptr, long metalLayer, double radius, float r, float g, float b, double borderWidth) {
        if (!round(ptr, radius, r, g, b, plausible(metalLayer) ? 0 : borderWidth)) return false;
        if (!plausible(metalLayer)) return true;
        try {
            StringBuilder chain = new StringBuilder();
            long l = metalLayer;
            for (int i = 0; i < 6 && plausible(l); i++) {
                roundLayer(l, radius);
                chain.append(i == 0 ? "" : " < ").append(NativeAlpha.classOf(l));
                if (!can(l, superlayer)) break;
                l = (long) sendP.invokeExact(l, superlayer);
            }
            boolean rounded = radius > 0;
            sendD.invokeExact(metalLayer, setBorderWidth, rounded ? borderWidth : 0.0);
            if (rounded) {
                long colour = (long) cgColor.invokeExact((double) r, (double) g, (double) b, 1.0);
                if (colour != 0) sendL.invokeExact(metalLayer, setBorderColor, colour);
            }
            if (chainLogs++ < 12) Minimal.MOD.log("menu layers rounded: " + chain + " (metal " + Long.toHexString(metalLayer) + ")");
            return true;
        } catch (Throwable t) {
            broken = true; ready = false;
            Minimal.MOD.log("rounding the menu's layers failed; turned off", t);
            return false;
        }
    }

    static boolean round(long ptr, double radius, float r, float g, float b, double borderWidth) {
        if (!plausible(ptr) || !init()) return false;
        try {
            long win = can(ptr, contentView) ? ptr : can(ptr, window) ? (long) sendP.invokeExact(ptr, window) : 0;
            if (win == 0) { if (logs++ < 3) Minimal.MOD.log("rounded menus: no window for " + NativeAlpha.classOf(ptr)); return false; }
            long view = (long) sendP.invokeExact(win, contentView);
            if (view == 0) return false;
            boolean rounded = radius > 0;
            sendB.invokeExact(win, setOpaque, (byte) (rounded ? 0 : 1));
            if (rounded) {
                long clear = (long) sendP.invokeExact(nsColor, clearColor);
                sendL.invokeExact(win, setBackgroundColor, clear);
            }
            sendB.invokeExact(view, setWantsLayer, (byte) 1);
            long l = (long) sendP.invokeExact(view, layer);
            if (l == 0) return false;
            sendD.invokeExact(l, setCornerRadius, radius);
            sendB.invokeExact(l, setMasksToBounds, (byte) (rounded ? 1 : 0));
            sendD.invokeExact(l, setBorderWidth, rounded ? borderWidth : 0.0);
            if (rounded && borderWidth > 0) {
                long colour = (long) cgColor.invokeExact((double) r, (double) g, (double) b, 1.0);
                if (colour != 0) sendL.invokeExact(l, setBorderColor, colour); // (one small colour per popup shown)
            }
            sendB.invokeExact(win, setHasShadow, (byte) 1);
            sendV.invokeExact(win, invalidateShadow);
            if (logs++ < 12) Minimal.MOD.log("menu corners rounded: " + Math.round(radius * 10) / 10.0 + " pt (" + NativeAlpha.classOf(win)
                    + " " + Long.toHexString(win) + ", view layer " + NativeAlpha.classOf(l) + " " + Long.toHexString(l) + ")");
            return true;
        } catch (Throwable t) {
            broken = true; ready = false;
            Minimal.MOD.log("rounding menus failed; turned off", t);
            return false;
        }
    }
}
