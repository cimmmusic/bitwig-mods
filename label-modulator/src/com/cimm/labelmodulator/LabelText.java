package com.cimm.labelmodulator;

import bitwig.obf.dve; // a widget
import bitwig.obf.iok; // a colour
import com.bitwig.base.geom.SYo; // a size
import com.bitwig.flt.app.CHR; // Bitwig's application: asyncExec runs on the UI thread
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** The Label modulator's panel: just its text, wrapped to the panel's width. Repaints when the name changes. */
final class LabelText extends dve {
    static final iok TEXT = iok.cfM(225, 225, 230);
    private static final Set<LabelText> shown = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static boolean polling;

    final Object modulator;
    private String shownText;

    LabelText(Object modulator) { this.modulator = modulator; shown.add(this); startPolling(); }

    @Override public SYo mK_() { return new SYo(40, 20); }
    @Override public SYo iG_() { return new SYo(120, 60); }
    @Override public SYo ni_() { return new SYo(100000, 100000); }
    @Override public dve MJ() { return this; }

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        try {
            String text = Label.text(modulator);
            shownText = text;
            double w = Sxo(), pad = 6, lineH = 13;
            try { lineH = Math.max(11, g.iHb().FzB() + 2); } catch (Throwable ignored) {} // the font's line height
            double y = pad + lineH - 3;
            for (String line : wrap(g, text, w - 2 * pad)) { g.cfM(pad, y, line, TEXT); y += lineH; }
        } catch (Throwable t) { LabelModulator.MOD.log("label paint failed", t); }
    }

    /** Words wrapped to the width (long words broken). */
    static List<String> wrap(com.bitwig.graphics.SYo g, String text, double width) {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String tryLine = line.length() == 0 ? word : line + " " + word;
                if (width(g, tryLine) <= width || line.length() == 0) { line.setLength(0); line.append(tryLine); }
                else { lines.add(line.toString()); line.setLength(0); line.append(word); }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    static double width(com.bitwig.graphics.SYo g, String s) { try { return g.cfM(s).IwV(); } catch (Throwable t) { return s.length() * 6; } }

    /** A few times a second on the UI thread: repaint labels whose text (name) changed. */
    static synchronized void startPolling() {
        if (polling) return;
        polling = true;
        Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cimm-label-modulator"); t.setDaemon(true); return t; })
                .scheduleAtFixedRate(() -> {
                    if (shown.isEmpty()) return;
                    try {
                        CHR.Grk().asyncExec(() -> {
                            List<LabelText> ls;
                            synchronized (shown) { ls = new ArrayList<>(shown); }
                            for (LabelText l : ls) { try { if (!Label.text(l.modulator).equals(l.shownText)) l.jbi(); } catch (Throwable ignored) {} }
                        });
                    } catch (Throwable ignored) {}
                }, 300, 300, TimeUnit.MILLISECONDS);
    }
}
