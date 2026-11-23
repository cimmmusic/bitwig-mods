package com.cimm.clipfx;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/** Device right-click: Remove from Clip FX, for a clip's inserts. */
public final class ClipFxActions implements ActionProvider {
    @Override public void declare(Actions actions) {
        actions.action("cimm_clipfx_remove_insert", "Remove from Clip FX")
               .in(Menu.DEVICE)
               .visibleWhen(c -> c.device() != null && ClipFx.isInsert(c.device()))
               .onRun(c -> { Object d = c.device(); if (d != null) com.bitwig.flt.app.CHR.Grk().asyncExec(() -> ClipGate.delete(d)); });
    }
}
