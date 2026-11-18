package com.cimm.pinchannels;

import bitwig.obf.ICh; // a selection of document objects
import bitwig.obf.aYp; // an arranger panel
import bitwig.obf.big; // the view's selection object a selection menu opens on
import bitwig.obf.dve; // a widget
import bitwig.obf.xJo; // a track
import bml.actions.ActionContext;
import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;
import java.util.ArrayList;
import java.util.List;

/** Track right-click menu: Pin Channel / Unpin Channel (for every selected track). */
public final class PinActions implements ActionProvider {
    public PinActions() {}

    @Override
    public void declare(Actions actions) {
        actions.action("cimm_pin_channel", "Pin Channel")
               .in(Menu.selection("track"))
               .category("mixer")
               .visibleWhen(c -> tracks(c).stream().anyMatch(t -> !Pins.isPinned(t)))
               .onRun(c -> { for (xJo t : tracks(c)) Pins.pin(t, true); });
        actions.action("cimm_unpin_channel", "Unpin Channel")
               .in(Menu.selection("track"))
               .category("mixer")
               .visibleWhen(c -> tracks(c).stream().anyMatch(Pins::isPinned))
               .onRun(c -> { for (xJo t : tracks(c)) Pins.pin(t, false); });
    }

    private static dve rootOf(dve w) { dve r = w; for (dve p = w.RbC(); p != null; p = p.RbC()) r = p; return r; }

    /** The selected tracks (as the VCA mod finds them: the menu's selection, else the arranger's). */
    static List<xJo> tracks(ActionContext context) {
        List<xJo> out = new ArrayList<>();
        ICh selection = null;
        if (context.selection() instanceof big view) selection = view.UNj();
        else if (context.widget() instanceof dve window) {
            Object panel = window.lYc(aYp.class).findFirst().orElse(null);
            if (panel instanceof aYp arranger) selection = arranger.BBT();
        }
        if (selection == null) return out;
        for (Object item : selection.FzB()) if (item instanceof xJo t) out.add(t);
        return out;
    }
}
