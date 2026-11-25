package com.cimm.uifont;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Settings > Mods > Font: a dropdown each for the interface, bold and numbers fonts. */
public final class FontSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section mine = preferences.section(mods, "ui_font", "Font");
        List<Fonts.Font> fonts = Fonts.catalogue();

        // Interface and numbers: [Bitwig default, fonts...]
        dropdown(preferences, mine, Fonts.INTERFACE, "Interface font (restart)", List.of("Bitwig default (Lato)"), List.of(""), fonts);
        dropdown(preferences, mine, Fonts.BOLD, "Bold font (restart)",
                List.of("Same as interface font", "Bitwig default (Lato)"), List.of(Fonts.SAME, ""), fonts);
        dropdown(preferences, mine, Fonts.NUMBERS, "Numbers font (restart)", List.of("Bitwig default (CP Mono)"), List.of(""), fonts);
    }

    private static void dropdown(Preferences preferences, Section in, String key, String label,
                                 List<String> fixedLabels, List<String> fixedValues, List<Fonts.Font> fonts) {
        List<String> labels = new ArrayList<>(fixedLabels), values = new ArrayList<>(fixedValues);
        for (Fonts.Font f : fonts) { labels.add(f.label()); values.add(f.path()); }
        String fallback = fixedValues.get(0);
        IntSupplier get = () -> {
            int i = values.indexOf(Fonts.STORE.get(key, fallback));
            return i < 0 ? 0 : i;
        };
        IntConsumer set = i -> {
            if (i < 0 || i >= values.size()) return;
            Fonts.STORE.put(key, values.get(i));
            Fonts.MOD.log(key + " font set to " + labels.get(i) + " (restart Bitwig to apply)");
        };
        preferences.dropdown("ui_font_" + key, label, labels.toArray(String[]::new)).in(in).value(get, set);
    }
}
