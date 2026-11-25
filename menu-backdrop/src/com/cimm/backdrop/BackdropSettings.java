package com.cimm.backdrop;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Menu Backdrop. */
public final class BackdropSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section mine = preferences.section(mods, "menu_backdrop", "Menu Backdrop");
        preferences.dropdown("menu_backdrop_style", "Behind open menus", Backdrop.STYLES)
                   .in(mine)
                   .value(() -> Backdrop.style, i -> { Backdrop.style = i; Backdrop.STORE.putInt("style", i); });
        preferences.slider("menu_backdrop_darkness", "Darkness (%)", 0, 90).wholeNumbers()
                   .in(mine)
                   .value(() -> Backdrop.darkness, v -> {
                       Backdrop.darkness = (int) Math.round(v);
                       Backdrop.STORE.putInt("darkness", Backdrop.darkness);
                   });
    }
}
