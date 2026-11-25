package com.cimm.highlight;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Highlight Colour: a preset dropdown and a hue for "Custom hue". */
public final class HighlightSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section mine = preferences.section(mods, "highlight_colour", "Highlight Colour");
        preferences.dropdown("highlight_colour_choice", "Highlight colour", Highlight.LABELS)
                   .in(mine)
                   .value(Highlight::choice, i -> { Highlight.STORE.putInt("choice", i); Highlight.apply(); });
        preferences.slider("highlight_colour_hue", "Custom hue (°)", 0, 360).wholeNumbers()
                   .in(mine)
                   .value(Highlight::hue, v -> {
                       Highlight.STORE.putInt("hue", (int) Math.round(v));
                       if (Highlight.choice() == Highlight.CUSTOM) Highlight.apply();
                   });
        preferences.checkbox("highlight_colour_dark_scenes", "Dark scene backgrounds")
                   .in(mine)
                   .value(() -> Scenes.on, v -> { Scenes.on = v; Highlight.STORE.putBoolean(Scenes.KEY, v); });
    }
}
