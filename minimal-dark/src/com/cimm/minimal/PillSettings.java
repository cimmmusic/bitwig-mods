package com.cimm.minimal;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Pill Buttons. */
public final class PillSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section mine = preferences.section(mods, "pill_buttons", "Minimal Dark: Buttons");
        preferences.checkbox("pill_buttons_on", "Pill buttons")
                   .in(mine)
                   .value(() -> Minimal.on, v -> { Minimal.on = v; Minimal.STORE.putBoolean("enabled", v); });
        preferences.checkbox("pill_buttons_toolbar_plain", "No outlines on toolbar buttons")
                   .in(mine)
                   .value(() -> Minimal.toolbarPlain, v -> { Minimal.toolbarPlain = v; Minimal.STORE.putBoolean("toolbar_no_outlines", v); });
        preferences.checkbox("pill_buttons_toolbar_hover_icons", "Toolbar icons light up on hover (highlight colour)")
                   .in(mine)
                   .value(() -> Minimal.hoverIcons, v -> { Minimal.hoverIcons = v; Minimal.STORE.putBoolean("toolbar_hover_icons", v); });
        preferences.checkbox("pill_buttons_menu_arrows", "Arrows on menu buttons (restart)")
                   .in(mine)
                   .value(() -> Minimal.menuArrows, v -> { Minimal.menuArrows = v; Minimal.STORE.putBoolean("menu_arrows", v); });
        preferences.checkbox("pill_buttons_transport_bottom", "Transport at the bottom (restart)")
                   .in(mine)
                   .value(() -> Minimal.transportBottom, v -> { Minimal.transportBottom = v; Minimal.STORE.putBoolean("transport_bottom", v); });
        preferences.checkbox("pill_buttons_bottom_bar_top", "Arrange/Mix/Edit bar at the top (restart)")
                   .in(mine)
                   .value(() -> Minimal.barTop, v -> { Minimal.barTop = v; Minimal.STORE.putBoolean("bottom_bar_top", v); });
        preferences.slider("pill_buttons_roundness", "Roundness (%)", 0, 100).wholeNumbers()
                   .in(mine)
                   .value(() -> Minimal.roundness, v -> { Minimal.roundness = Math.round(v); Minimal.STORE.putInt("roundness", (int) Math.round(v)); });
        preferences.number("pill_buttons_max_height", "Up to height (px)", 8, 200).wholeNumbers()
                   .in(mine)
                   .value(() -> Minimal.maxH, v -> { Minimal.maxH = Math.round(v); Minimal.STORE.putInt("max_height", (int) Math.round(v)); });
    }
}
