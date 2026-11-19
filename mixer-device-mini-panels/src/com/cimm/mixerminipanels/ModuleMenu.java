package com.cimm.mixerminipanels;

import bml.api.Bml;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The module-swap menu of Bitwig's module slots (Filter+'s filter, its waveshaper…), rebuilt from what Bitwig's own
 * module chooser (auG, Bitwig 6.1.4) uses:
 *  • layouts = auG's static Object[] fields hUR / TMF / lYc / E3: a String is a category heading ("One Knob"), a UUID a
 *    module, in menu order; the chooser picks one by its module kind rQz (auI.cfM maps the kind's ordinal to 2..5);
 *  • the module catalogue = cBi.lYc.Hnt() (a wz): E3().getValue() lists module types (uE: hY_() id, LaE() name,
 *    Ucl() description, tp2() file);
 *  • a slot's current module = slot.TMF().UNj() (UUID);
 *  • choosing = the slot's module grid (rSf.sut(), an IcG / TIq).cfM(slot, file.toString(), null) — "Replace Module".
 */
final class ModuleMenu {
    private static final Bml.Mod MOD = Bml.mod("mixer-device-mini-panels");
    private ModuleMenu() {}

    private static final String[] LAYOUT_FIELDS = {"hUR", "TMF", "lYc", "E3"}; // chooser cases 2, 3, 4, 5
    private static Object[][] layouts;
    private static String[] headings;
    private static bitwig.obf.wz catalogue;

    static synchronized void init() {
        if (layouts != null) return;
        layouts = new Object[4][];
        headings = new String[4];
        for (int i = 0; i < 4; i++) {
            try {
                java.lang.reflect.Field f = bitwig.obf.auG.class.getDeclaredField(Names.field("auG", LAYOUT_FIELDS[i]));
                f.setAccessible(true);
                layouts[i] = (Object[]) f.get(null);
            } catch (Throwable t) { MOD.log("module menu: layout " + LAYOUT_FIELDS[i] + " failed: " + t); }
        }
        try { // heading of each layout: the module kind whose chooser case is 2..5
            java.lang.reflect.Field f = bitwig.obf.auI.class.getDeclaredField(Names.field("auI", "cfM"));
            f.setAccessible(true);
            int[] cases = (int[]) f.get(null);
            for (bitwig.obf.rQz k : bitwig.obf.rQz.values()) {
                int c = k.ordinal() < cases.length ? cases[k.ordinal()] : 0;
                if (c >= 2 && c <= 5) headings[c - 2] = text(k);
            }
        } catch (Throwable t) { MOD.log("module menu: headings failed: " + t); }
        try {
            catalogue = bitwig.obf.cBi.lYc.Hnt();
            catalogue.NC();          // as auG does when attached: start listing
            catalogue.MIO().ejd();
        } catch (Throwable t) { MOD.log("module menu: catalogue failed: " + t); }
        if (MiniGraph.DEBUG) MOD.log("module menu: headings " + java.util.Arrays.toString(headings));
    }

    /** A module kind's display text (its own String getters; "" if none). */
    private static String text(bitwig.obf.rQz k) {
        try { String s = k.MRY(); if (s != null && !s.isBlank()) return s; } catch (Throwable ignored) {}
        try { String s = k.FzB(); if (s != null && !s.isBlank()) return s; } catch (Throwable ignored) {}
        return k.name();
    }

    /** The module currently in the slot, or null. */
    static UUID current(Object slot) {
        try { return ((bitwig.obf.Qvs) slot).TMF().UNj(); } catch (Throwable t) { return null; }
    }

    /** Index of the layout listing this module (0..3), or -1. */
    static int layoutOf(UUID id) {
        init();
        if (id == null) return -1;
        for (int i = 0; i < 4; i++) if (layouts[i] != null) for (Object o : layouts[i]) if (id.equals(o)) return i;
        return -1;
    }

    static List<bitwig.obf.uE> modules() {
        init();
        List<bitwig.obf.uE> out = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked") List<Object> l = (List<Object>) catalogue.E3().getValue();
            if (l != null) for (Object o : l) if (o instanceof bitwig.obf.uE u) out.add(u);
        } catch (Throwable t) { MOD.log("module menu: catalogue list failed: " + t); }
        return out;
    }

    /** The name of the module with this id (from the catalogue), or "". */
    static String name(UUID id) {
        if (id == null) return "";
        for (bitwig.obf.uE u : modules()) if (id.equals(u.hY_())) return str(u.LaE());
        return "";
    }

    /** Fills the popup with the slot's swap menu, as the module chooser does; false if the slot's kind is unknown. */
    static boolean fill(bitwig.obf.dKW popup, bitwig.obf.dve owner, Object slot, Runnable afterSwap) {
        UUID cur = current(slot);
        int li = layoutOf(cur);
        if (li < 0) { MOD.log("module menu: no layout for module " + cur + " '" + name(cur) + "'"); return false; }
        List<bitwig.obf.uE> mods = modules();
        if (mods.isEmpty()) { MOD.log("module menu: catalogue still empty"); return false; }
        popup.MRY(headings[li] == null ? "Modules" : headings[li]);
        for (Object o : layouts[li]) {
            if (o instanceof String heading) { popup.cfM(heading, owner.PEn().Dah()); continue; } // category heading, styled as the chooser's
            if (!(o instanceof UUID id)) continue;
            for (bitwig.obf.uE u : mods) {
                if (!id.equals(u.hY_())) continue;
                boolean on = id.equals(cur);
                bitwig.obf.dKa row = bitwig.obf.dKa.cfM(str(u.LaE()), (Runnable) () -> {
                    if (on) return;
                    try {
                        bitwig.obf.TIq grid = (bitwig.obf.TIq) Reflect.call(slot, "sut");
                        grid.cfM((bitwig.obf.Qvs) slot, u.tp2().toString(), null);
                        afterSwap.run();
                    } catch (Throwable t) { MOD.log("module swap failed", t); }
                });
                if (on) row.eLY("check.svg");
                String d = str(u.Ucl());
                if (!d.isEmpty()) row.lYc(d);
                popup.hUR(row);
            }
        }
        return true;
    }

    static String str(Object o) { return o instanceof String s ? s : ""; }
}
