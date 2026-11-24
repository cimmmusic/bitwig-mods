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
        // each block names what it grooves: with a clip open in the editor, the clip's block and its notes' / audio
        // events' block show together (the notes' / events' own groove wins over the clip's)
        blocks.block("groove-note", "Note / Event Groove").in(Context.NOTE).bottom().content(GrooveInspector::content);
        blocks.block("groove", "Clip Groove").in(Context.LAUNCHER_CLIP, Context.ARRANGER_CLIP).bottom()
              .content(GrooveInspector::content);
        blocks.block("groove-track", "Track Groove").in(Context.TRACK).bottom().content(GrooveInspector::content);
        blocks.onBlockBuilt(b -> {
            if (!IDS.contains(b.blockId()) || b.bitwigBlock()) return;
            synchronized (live) { for (Live l : live) if (l.context.panel() == b.panel() && l.kind == kindOf(b.context())) l.widget = b.widget(); }
            showOne();
        });
    }


    // ---- the panels showing a Groove block: their selection is read again when it changes ----
    private static final class Live {
        final BlockContext context; final Object selection; final int kind; int lastHash; String[] lastNames; Rows.Choice files;
        Boolean shown;
        Object widget; // the whole block (header and rows), shown only for what was selected last
        Live(BlockContext context, Object selection) { this.context = context; this.selection = selection; this.kind = kindOf(context.context()); }
    }

    // ---- one Groove block at a time: the one for what was selected last (a clip, notes / audio events, a track).
    //      Selecting a clip also selects its track (and may change the editor's events): the clip wins those ties. ----
    private static final java.util.Set<String> IDS = java.util.Set.of("groove", "groove-note", "groove-track");
    private static final int CLIP = 0, NOTE = 1, TRACK = 2; // also the tie order
    private static final long[] changedAt = new long[3];
    private static final int[] kindHash = new int[3];
    private static final long TIE_MS = 400;

    private static int kindOf(Context c) {
        if (c == Context.TRACK) return TRACK;
        if (c == Context.NOTE) return NOTE;
        return CLIP;
    }

    /** A block's selection: if it differs from the last one seen for its kind, that kind was selected just now. */
    private static void seen(int kind, int hash) {
        if (kindHash[kind] != hash) { kindHash[kind] = hash; changedAt[kind] = System.currentTimeMillis(); }
    }

    private static boolean attached(Object widget) {
        try { bitwig.obf.dve w = (bitwig.obf.dve) widget, root = w.Wov(); return root != null && root != w; } catch (Throwable t) { return true; }
    }

    /** Shows the Groove block of the kind selected last (among those on screen with something selected), hides the rest. */
    static void showOne() {
        Live[] all; synchronized (live) { all = live.toArray(new Live[0]); }
        boolean[] present = new boolean[3];
        for (Live l : all) if (l.widget != null && attached(l.widget) && !selected(l.selection).isEmpty()) present[l.kind] = true;
        int win = -1;
        for (int k = 0; k < 3; k++) {
            if (!present[k]) continue;
            if (win < 0 || changedAt[k] > changedAt[win] + TIE_MS) win = k;
        }
        for (Live l : all) {
            if (l.widget == null) continue;
            boolean show = win < 0 || l.kind == win;
            if (l.shown == null || l.shown != show) { try { ((bitwig.obf.dve) l.widget).t_(show); l.shown = show; } catch (Throwable ignored) {} }
        }
    }
    private static final List<Live> live = new ArrayList<>();

    private static Object content(BlockContext context) {
        Object selection = selectionOf(context.panel());
        Live l = new Live(context, selection);
        l.lastHash = hash(selected(selection));
        seen(l.kind, l.lastHash);
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
                if (h != l.lastHash) seen(l.kind, h);
                if (h != l.lastHash || filesChanged) { l.lastHash = h; Rows.refresh(l.context); }
            } catch (Throwable t) { synchronized (live) { live.remove(l); } }
        }
        try { showOne(); } catch (Throwable ignored) {}
    }

}
