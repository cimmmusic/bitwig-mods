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
        preferences.checkbox("highlight_colour_dark_module_previews", "Grid module previews match the theme (restart)")
                   .in(mine)
                   .value(() -> Previews.on, v -> { Previews.on = v; Highlight.STORE.putBoolean(Previews.KEY, v); });
        Section bg = preferences.section(mods, "background_tone", "Background");
        tone(preferences, bg, "hue", "Hue (°)", 0, 360, () -> Tone.hue, v -> Tone.hue = v, "bg_hue");
        tone(preferences, bg, "saturation", "Saturation (%)", -100, 100, () -> Tone.sat, v -> Tone.sat = v, "bg_saturation");
        tone(preferences, bg, "luminance", "Luminance (%)", -100, 100, () -> Tone.lum, v -> Tone.lum = v, "bg_luminance");
        tone(preferences, bg, "contrast", "Contrast (%)", -100, 100, () -> Tone.con, v -> Tone.con = v, "bg_contrast");
    }

    private static void tone(Preferences preferences, Section in, String id, String label, int min, int max,
                             java.util.function.IntSupplier get, java.util.function.IntConsumer set, String key) {
        preferences.slider("background_tone_" + id, label, min, max).wholeNumbers()
                   .in(in)
                   .value(get::getAsInt, v -> {
                       int i = (int) Math.round(v);
                       set.accept(i);
                       Highlight.STORE.putInt(key, i);
                       Tone.changed();
                       Tone.repaint();
                   });
    }
}
