package com.cimm.uifont;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Settings > Mods > Font: a dropdown each for the interface, bold and numbers fonts. Clicking one opens the font
 * list (Browser) instead of Bitwig's plain list.
 */
public final class FontSettings implements PreferenceProvider {
    static final String INTERFACE_FIRST = "Bitwig default (Lato)";
    static final String BOLD_FIRST = "Same as interface font";
    static final String NUMBERS_FIRST = "Bitwig default (CP Mono)";

    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section mine = preferences.section(mods, "ui_font", "Font");
        dropdown(preferences, mine, Fonts.INTERFACE, "Interface font (restart)");
        dropdown(preferences, mine, Fonts.BOLD, "Bold font (restart)");
        dropdown(preferences, mine, Fonts.NUMBERS, "Numbers font (restart)");
        size(preferences, mine, Fonts.INTERFACE, "Interface text size % (restart)");
        size(preferences, mine, Fonts.BOLD, "Bold text size % (restart)");
        size(preferences, mine, Fonts.NUMBERS, "Numbers text size % (restart)");
        preferences.number("ui_font_move_down", "Move text down (px)", -10, 10)
                   .in(mine)
                   .value(Shift::px, Shift::set);
    }

    /** The dropdown's fixed choices first (labels and the values stored for them), then every font. */
    private static List<String> fixedLabels(String role) {
        return switch (role) {
            case Fonts.BOLD -> List.of(BOLD_FIRST, INTERFACE_FIRST);
            case Fonts.NUMBERS -> List.of(NUMBERS_FIRST);
            default -> List.of(INTERFACE_FIRST);
        };
    }
    private static List<String> fixedValues(String role) {
        return role.equals(Fonts.BOLD) ? List.of(Fonts.SAME, "") : List.of("");
    }

    static List<String> options(String role) {
        List<String> labels = new ArrayList<>(fixedLabels(role));
        for (Fonts.Font f : Fonts.catalogue()) labels.add(f.label());
        return labels;
    }
    private static List<String> values(String role) {
        List<String> values = new ArrayList<>(fixedValues(role));
        for (Fonts.Font f : Fonts.catalogue()) values.add(f.path());
        return values;
    }
    private static String fallback(String role) { return fixedValues(role).get(0); }

    private static int index(String role) {
        int i = values(role).indexOf(Fonts.STORE.get(role, fallback(role)));
        return i < 0 ? 0 : i;
    }

    /** The label of the font chosen now for this role. */
    static String currentLabel(String role) { return options(role).get(index(role)); }

    /** Chooses the option with this label for the role. */
    static void store(String role, String label) {
        int i = options(role).indexOf(label);
        if (i < 0) return;
        Fonts.STORE.put(role, values(role).get(i));
    }

    /** A role's text size, 50..200 % of Bitwig's. */
    private static void size(Preferences preferences, Section in, String role, String label) {
        preferences.number("ui_font_" + role + "_size", label, 50, 200).wholeNumbers()
                   .in(in)
                   .value(() -> Fonts.sizePercent(role), v -> {
                       int p = (int) Math.max(50, Math.min(200, Math.round(v)));
                       Fonts.STORE.putInt(Fonts.sizeKey(role), p);
                       Fonts.MOD.log(role + " text size set to " + p + "% (restart Bitwig to apply)");
                   });
    }

    private static void dropdown(Preferences preferences, Section in, String role, String label) {
        List<String> labels = options(role), values = values(role);
        IntSupplier get = () -> index(role);
        IntConsumer set = i -> {
            if (i < 0 || i >= values.size()) return;
            Fonts.STORE.put(role, values.get(i));
            Fonts.MOD.log(role + " font set to " + labels.get(i) + " (restart Bitwig to apply)");
        };
        preferences.dropdown("ui_font_" + role, label, labels.toArray(String[]::new)).in(in).value(get, set);
    }
}
