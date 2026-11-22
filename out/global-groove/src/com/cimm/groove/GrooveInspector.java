package com.cimm.groove;

import bml.inspector.BlockContext;
import bml.inspector.Blocks;
import bml.inspector.Context;
import bml.inspector.InspectorProvider;
import bml.inspector.Rows;

import java.util.ArrayList;
import java.util.List;

/**
 * The Inspector's GROOVE block: a groove of its own for the selected notes (next to Bitwig's NOTE block), clips
 * (launcher and arranger) or tracks. Source Default follows the clip / track / global groove; Off plays straight;
 * Groove File or Manual A..D use that groove with their own amounts. It wins over the global groove, on or off.
 */
public final class GrooveInspector implements InspectorProvider {

    @Override
    public void declare(Blocks blocks) {
        // the Note Inspector also shows audio events (an AUDIO EVENT block in place of NOTE): at its end, for both
        blocks.block("groove-note", "Groove").in(Context.NOTE).bottom().content(GrooveInspector::content);
        blocks.block("groove", "Groove").in(Context.LAUNCHER_CLIP, Context.ARRANGER_CLIP, Context.TRACK).bottom()
              .content(GrooveInspector::content);
    }


    // ---- the panels showing a Groove block: their selection is read again when it changes ----
    private static final class Live {
        final BlockContext context; final Object selection; int lastHash; String[] lastNames; Rows.Choice files;
        Live(BlockContext context, Object selection) { this.context = context; this.selection = selection; }
    }
    private static final List<Live> live = new ArrayList<>();

    private static Object content(BlockContext context) {
        Object selection = selectionOf(context.panel());
        Live l = new Live(context, selection);
        l.lastHash = hash(selected(selection));
        l.lastNames = fileNames();
        synchronized (live) { live.add(l); while (live.size() > 16) live.remove(0); }

        Rows.Choice source = Rows.choice(context, "Source", Overrides.SOURCES,
                () -> first(l).source,
                v -> Overrides.edit(selected(selection), o -> {
                    if (o.source == Overrides.DEFAULT && v >= Overrides.MANUAL_A) o.amounts = State.manualDefaults();
                    o.source = v;
                }));
        l.files = Rows.choice(context, "Groove", l.lastNames,
                () -> fileIndex(first(l).file),
                v -> { List<State.Entry> es = State.list(); if (v >= 0 && v < es.size()) Overrides.edit(selected(selection), o -> o.file = es.get(v).path.toString()); });
        return Rows.column(
                Rows.text(context, null, () -> Overrides.describe(firstObject(l))),
                source.row(),
                Rows.when(context, () -> first(l).source == Overrides.FILE, l.files.row()),
                Rows.when(context, () -> first(l).source >= Overrides.FILE,
                        Rows.number(context, "Strength", 0, 100, 100, " %", false,
                                () -> first(l).amounts.strength * 100, v -> amount(selection, a -> a.strength = v / 100)),
                        Rows.number(context, "Timing", 0, 100, 100, " %", false,
                                () -> first(l).amounts.timing * 100, v -> amount(selection, a -> a.timing = v / 100)),
                        Rows.number(context, "Velocity", -100, 100, 0, " %", true,
                                () -> first(l).amounts.velocity * 100, v -> amount(selection, a -> a.velocity = v / 100)),
                        Rows.number(context, "Random", 0, 100, 0, " %", false,
                                () -> first(l).amounts.random * 100, v -> amount(selection, a -> a.random = v / 100)),
                        Rows.number(context, "Quantize", 0, 100, 0, " %", false,
                                () -> first(l).amounts.quantize * 100, v -> amount(selection, a -> a.quantize = v / 100)),
                        Rows.steps(context, "Base", Groove.BASE_NAMES, 3,
                                () -> first(l).amounts.base, v -> amount(selection, a -> a.base = v))));
    }

    private static void amount(Object selection, java.util.function.Consumer<Groove.Amounts> change) {
        Overrides.edit(selected(selection), o -> { o.amounts = State.copy(o.amounts); change.accept(o.amounts); });
    }

    private static Object firstObject(Live l) { List<Object> s = selected(l.selection); return s.isEmpty() ? null : s.get(0); }
    private static Overrides.Ov first(Live l) { return Overrides.shown(firstObject(l)); }

    private static int fileIndex(String path) {
        List<State.Entry> es = State.list();
        for (int i = 0; i < es.size(); i++) if (es.get(i).path.toString().equals(path)) return i;
        return es.isEmpty() ? -1 : 0;
    }

    private static String[] fileNames() {
        List<State.Entry> es = State.list();
        if (es.isEmpty()) return new String[]{"(no groove files: Browse in the Play menu)"};
        String[] n = new String[es.size()];
        for (int i = 0; i < n.length; i++) n[i] = es.get(i).groove.name;
        return n;
    }

    // ---- the selection an Inspector panel shows ----
    private static Object selectionOf(Object panel) {
        try { return ((bitwig.obf.aLZ) panel).vpw(); } catch (Throwable t) { State.MOD.log("Inspector selection not found", t); return null; }
    }

    static List<Object> selected(Object selection) {
        List<Object> out = new ArrayList<>();
        try {
            if (selection instanceof bitwig.obf.OXq s) {
                int n = s.Guv();
                for (int i = 0; i < n && i < 4096; i++) out.add(s.cfM(i));
            }
        } catch (Throwable t) { State.MOD.log("reading the Inspector selection failed", t); }
        return out;
    }

    private static int hash(List<Object> objects) {
        int h = 1;
        for (Object o : objects) h = 31 * h + System.identityHashCode(o);
        return h;
    }

    /** Every Groove block reads its rows again (after Undo / Redo of a groove change). UI thread. */
    static void refreshAll() {
        Live[] all; synchronized (live) { all = live.toArray(new Live[0]); }
        for (Live l : all) { try { Rows.refresh(l.context); } catch (Throwable t) { synchronized (live) { live.remove(l); } } }
    }

    /** Twice a second (UI thread): panels whose selection or groove file list changed read their rows again. */
    static void tick() {
        Live[] all; synchronized (live) { all = live.toArray(new Live[0]); }
        String[] names = null;
        for (Live l : all) {
            try {
                int h = hash(selected(l.selection));
                if (names == null) names = fileNames();
                boolean filesChanged = !java.util.Arrays.equals(names, l.lastNames);
                if (filesChanged) { l.lastNames = names; l.files.options(names); }
                if (h != l.lastHash || filesChanged) { l.lastHash = h; Rows.refresh(l.context); }
            } catch (Throwable t) { synchronized (live) { live.remove(l); } }
        }
    }

}
