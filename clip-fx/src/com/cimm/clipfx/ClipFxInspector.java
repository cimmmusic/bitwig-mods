package com.cimm.clipfx;

import bml.inspector.BlockContext;
import bml.inspector.Blocks;
import bml.inspector.Context;
import bml.inspector.InspectorProvider;
import bml.inspector.Rows;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** The Inspector's CLIP FX block, for clips (arranger, launcher) and audio events (in the Note Inspector panel). */
public final class ClipFxInspector implements InspectorProvider {

    @Override
    public void declare(Blocks blocks) {
        blocks.block("clip-fx", "Clip FX").in(Context.ARRANGER_CLIP, Context.LAUNCHER_CLIP, Context.NOTE).bottom()
              .content(ClipFxInspector::content);
    }

    private static Object content(BlockContext context) {
        Object selection = selectionOf(context.panel());
        return Rows.column(
                Rows.choice(context, "Clip FX", new String[]{"Off", "On"},
                        () -> ClipFx.shown(first(selection)).on ? 1 : 0,
                        v -> { for (Object o : selected(selection)) ClipFx.edit(o).on = v == 1; }).row(),
                Rows.when(context, () -> ClipFx.shown(first(selection)).on,
                        Rows.number(context, "Tail", 0, 30, 0, " s", false,
                                () -> ClipFx.shown(first(selection)).tail,
                                v -> { for (Object o : selected(selection)) ClipFx.edit(o).tail = v; }),
                        Rows.button(context, "Test: add a Clip FX Unit to this track", () -> ChainBuilder.test(selected(selection))),
                        Rows.button(context, "Test: write the gate into this clip", () -> GateTest.run(selected(selection)))));
    }

    private static Object first(Object selection) { List<Object> s = selected(selection); return s.isEmpty() ? null : s.get(0); }

    static Object selectionOf(Object panel) {
        try { return ((bitwig.obf.aLZ) panel).vpw(); } catch (Throwable t) { ClipFx.MOD.log("Inspector selection not found", t); return null; }
    }

    static List<Object> selected(Object selection) {
        List<Object> out = new ArrayList<>();
        try {
            if (selection instanceof bitwig.obf.OXq s) { int n = s.Guv(); for (int i = 0; i < n && i < 256; i++) out.add(s.cfM(i)); }
        } catch (Throwable t) { ClipFx.MOD.log("reading the Inspector selection failed", t); }
        return out;
    }
}
