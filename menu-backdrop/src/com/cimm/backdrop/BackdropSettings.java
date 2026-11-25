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
        preferences.checkbox("menu_backdrop_raise_button", "Menu's button stays above the dim")
                   .in(mine)
                   .value(() -> Backdrop.raiseButton, v -> { Backdrop.raiseButton = v; Backdrop.STORE.putBoolean("raise_button", v); });
        preferences.checkbox("menu_backdrop_fade_popups", "Popups fade in")
                   .in(mine)
                   .value(() -> Backdrop.fadePopups, v -> { Backdrop.fadePopups = v; Backdrop.STORE.putBoolean("fade_popups", v); });
    }
}
