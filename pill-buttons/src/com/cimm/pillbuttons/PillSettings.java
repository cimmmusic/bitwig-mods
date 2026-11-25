package com.cimm.pillbuttons;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Pill Buttons. */
public final class PillSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section mine = preferences.section(mods, "pill_buttons", "Pill Buttons");
        preferences.checkbox("pill_buttons_on", "Pill buttons")
                   .in(mine)
                   .value(() -> Pill.on, v -> { Pill.on = v; Pill.STORE.putBoolean("enabled", v); });
        preferences.checkbox("pill_buttons_toolbar_plain", "No outlines on toolbar buttons")
                   .in(mine)
                   .value(() -> Pill.toolbarPlain, v -> { Pill.toolbarPlain = v; Pill.STORE.putBoolean("toolbar_no_outlines", v); });
        preferences.checkbox("pill_buttons_toolbar_hover_icons", "Toolbar icons light up on hover (highlight colour)")
                   .in(mine)
                   .value(() -> Pill.hoverIcons, v -> { Pill.hoverIcons = v; Pill.STORE.putBoolean("toolbar_hover_icons", v); });
        preferences.checkbox("pill_buttons_transport_bottom", "Transport at the bottom (restart)")
                   .in(mine)
                   .value(() -> Pill.transportBottom, v -> { Pill.transportBottom = v; Pill.STORE.putBoolean("transport_bottom", v); });
        preferences.slider("pill_buttons_roundness", "Roundness (%)", 0, 100).wholeNumbers()
                   .in(mine)
                   .value(() -> Pill.roundness, v -> { Pill.roundness = Math.round(v); Pill.STORE.putInt("roundness", (int) Math.round(v)); });
        preferences.number("pill_buttons_max_height", "Up to height (px)", 8, 200).wholeNumbers()
                   .in(mine)
                   .value(() -> Pill.maxH, v -> { Pill.maxH = Math.round(v); Pill.STORE.putInt("max_height", (int) Math.round(v)); });
    }
}
