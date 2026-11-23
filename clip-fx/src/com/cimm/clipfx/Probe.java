package com.cimm.clipfx;

import com.bitwig.ramona.core.oDc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Stage 1 probe: from a selected clip, what can the mod reach (its track, the track's devices and chains)? */
final class Probe {
    private Probe() {}

    static void run(List<Object> selection) {
        if (selection.isEmpty()) { ClipFx.MOD.log("PROBE: nothing selected"); return; }
        Object o = selection.get(0);
        StringBuilder chain = new StringBuilder();
        Object track = null;
        for (Object x = o; x != null; x = parent(x)) {
            chain.append(chain.length() == 0 ? "" : " < ").append(x.getClass().getSimpleName());
            if (track == null && x instanceof bitwig.obf.xJo) track = x;
        }
        ClipFx.MOD.log("PROBE selected " + o.getClass().getName() + " path " + path(o) + "\n  parents " + chain);
        if (track == null) { ClipFx.MOD.log("PROBE: no track found above it"); return; }
        bitwig.obf.xJo t = (bitwig.obf.xJo) track;
        describe("track.DxJ()", call(() -> t.DxJ()), 2);
        describe("track.sWR()", call(() -> t.sWR()), 2);
        describe("track.mcL()", call(() -> t.mcL()), 2);
        describe("track.exV()", call(() -> t.exV()), 2);
    }

    interface Get { Object get() throws Throwable; }
    private static Object call(Get g) { try { return g.get(); } catch (Throwable t) { return "!" + t; } }

    /** Logs an object's class, path and, for its no-argument getters that return lists, what they hold. */
    private static void describe(String what, Object o, int depth) {
        if (o == null || o instanceof String) { ClipFx.MOD.log("PROBE " + what + " = " + o); return; }
        StringBuilder b = new StringBuilder("PROBE " + what + " = " + o.getClass().getName() + " " + path(o));
        Class<?> c = o.getClass();
        for (int up = 0; up < 6 && c != null && c != Object.class; up++, c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) continue;
                if (!List.class.isAssignableFrom(m.getReturnType())) continue;
                try {
                    List<?> l = (List<?>) m.invoke(o);
                    if (l == null || l.isEmpty()) continue;
                    b.append("\n    ").append(c.getSimpleName()).append('.').append(m.getName()).append("() -> ").append(l.size()).append(" x ");
                    for (int i = 0; i < Math.min(4, l.size()); i++) b.append(l.get(i) == null ? "null" : l.get(i).getClass().getSimpleName() + " " + path(l.get(i))).append("; ");
                } catch (Throwable ignored) {}
            }
        }
        ClipFx.MOD.log(b.toString());
    }

    static Object parent(Object o) { try { return o instanceof oDc d ? d.J_() : null; } catch (Throwable t) { return null; } }
    static String path(Object o) { try { return o instanceof oDc d && d.J_() != null ? d.veO() : ""; } catch (Throwable t) { return ""; } }
}
