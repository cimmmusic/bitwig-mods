package com.cimm.minimal;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Menu Backdrop. */
public final class BackdropSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section mine = preferences.section(mods, "menu_backdrop", "Minimal Dark: Menus");
        preferences.dropdown("menu_backdrop_style", "Behind open menus", Minimal.STYLES)
                   .in(mine)
                   .value(() -> Minimal.style, i -> { Minimal.style = i; Minimal.STORE.putInt("style", i); });
        preferences.checkbox("menu_backdrop_raise_button", "Menu's button stays above the dim")
                   .in(mine)
                   .value(() -> Minimal.raiseButton, v -> { Minimal.raiseButton = v; Minimal.STORE.putBoolean("raise_button", v); });
        preferences.checkbox("menu_backdrop_fade_popups", "Popups fade in")
                   .in(mine)
                   .value(() -> Minimal.fadePopups, v -> { Minimal.fadePopups = v; Minimal.STORE.putBoolean("fade_popups", v); });
    }
}
