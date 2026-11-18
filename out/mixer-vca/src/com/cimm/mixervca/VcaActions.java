package com.cimm.mixervca;

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

/** Track right-click menu: Group as VCA (the selected tracks get a new VCA track that moves their faders). */
public final class VcaActions implements ActionProvider {
    public VcaActions() {}

    @Override
    public void declare(Actions actions) {
        Linker.start(); // the VCAs follow from now on, in every open project
        actions.action("cimm_group_as_vca", "Group as VCA")
               .in(Menu.selection("track"))
               .category("mixer")
               .visibleWhen(c -> !tracks(c).isEmpty())
               .onRun(c -> Vca.create(tracks(c)));
    }

    /** The selected tracks that can join a VCA: regular tracks (not effect tracks, not a group's master). */
    static List<xJo> tracks(ActionContext context) {
        List<xJo> out = new ArrayList<>();
        ICh selection = null;
        if (context.selection() instanceof big view) selection = view.UNj();   // the selection behind the menu's view
        else if (context.widget() instanceof dve window) {
            Object panel = window.lYc(aYp.class).findFirst().orElse(null);     // the first arranger panel below the window
            if (panel instanceof aYp arranger) selection = arranger.BBT();     // its track selection
        }
        if (selection == null) return out;
        for (Object item : selection.FzB()) {
            if (!(item instanceof xJo t)) continue;
            try {
                if (t.UAr()) continue;                                         // an effect track
                if (t.zjb() != null && t.zjb().Lqt() == t) continue;          // a group's own master
            } catch (Throwable ignored) {}
            out.add(t);
        }
        return out;
    }
}
