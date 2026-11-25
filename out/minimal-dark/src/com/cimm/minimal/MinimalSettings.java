package com.cimm.minimal;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods: Minimal Dark's sections (theme, font, highlight, background, menus). */
public final class MinimalSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section theme = preferences.section(mods, "minimal_dark_theme", "Minimal Dark");
        preferences.dropdown("minimal_dark_look", "Look", Looks.labels())
                   .in(theme)
                   .value(Looks::current, Looks::choose);
        preferences.checkbox("minimal_dark_theme_colours", "Minimal Dark colours (restart)")
                   .in(theme)
                   .value(() -> Minimal.STORE.getBoolean("theme_on", true), v -> Minimal.STORE.putBoolean("theme_on", v));
        new FontSettings().declare(preferences);
        new HighlightSettings().declare(preferences);
        new BackdropSettings().declare(preferences);
        new PillSettings().declare(preferences);
    }
}
