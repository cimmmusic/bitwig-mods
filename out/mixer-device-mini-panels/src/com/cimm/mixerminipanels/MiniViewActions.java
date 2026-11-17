package com.cimm.mixerminipanels;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/**
 * In a device's right-click menu: collapse / expand its mini view in the mixer and in the track Inspector,
 * separately (right-clicking the mini view itself toggles just that one). Mod menu items are sorted alphabetically,
 * so the labels start with the place ("Mini View in Mixer: Collapse" / "…: Expand"): the row keeps its position
 * whichever state it is in.
 */
public final class MiniViewActions implements ActionProvider {
    public MiniViewActions() {}

    @Override
    public void declare(Actions actions) {
        for (int place : new int[] {Collapse.MIXER, Collapse.INSPECTOR}) {
            // IDs stay "…_mixer" / "…_inspector"; labels sort alphabetically, so "Track Inspector" puts Mixer first.
            String id = Collapse.placeName(place).toLowerCase(java.util.Locale.ROOT);
            String where = place == Collapse.INSPECTOR ? "Track Inspector" : "Mixer";
            actions.action("cimm_mini_view_collapse_" + id, "Mini View in " + where + ": Collapse")
                   .in(Menu.DEVICE)
                   .menuOnly()
                   .visibleWhen(c -> Collapse.hasMiniView(c.device()) && !Collapse.isCollapsed(c.device(), place))
                   .onRun(c -> Collapse.set(c.device(), place, true));
            actions.action("cimm_mini_view_expand_" + id, "Mini View in " + where + ": Expand")
                   .in(Menu.DEVICE)
                   .menuOnly()
                   .visibleWhen(c -> Collapse.hasMiniView(c.device()) && Collapse.isCollapsed(c.device(), place))
                   .onRun(c -> Collapse.set(c.device(), place, false));
        }
    }
}
