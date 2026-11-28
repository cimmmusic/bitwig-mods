package com.cimm.minimal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks: ready-made combinations of the Background sliders (hue, saturation, luminance, contrast) and the highlight
 * colour, chosen from one dropdown, plus "Custom", the user's own combination, saved separately (custom_* settings).
 *
 * - Choosing a preset applies it; the Custom values stay as they were.
 * - Choosing Custom applies the saved Custom values.
 * - The Background sliders and the highlight controls belong to Custom: while a preset is selected they're dimmed, and
 *   touching one switches the look to Custom, starting from the preset's values (so the change is never lost).
 */
final class Looks {
    private Looks() {}

    /** A look: background hue (°), saturation, luminance and contrast (%), highlight preset (Minimal.LABELS index) and its custom hue. */
    record Look(String name, int hue, int sat, int lum, int con, int highlight, int customHue) {}

    static final List<Look> ALL = List.of(
            new Look("Minimal Dark", 270, 0, 0, 0, 0, 30),
            new Look("Midnight Blue", 220, 35, 4, 0, 7, 190),
            new Look("Aubergine", 285, 30, 3, 0, 3, 330),
            new Look("Forest", 150, 25, 2, 0, 10, 80),
            new Look("Espresso", 25, 28, 3, 0, 11, 45),
            new Look("Graphite", 210, 8, 10, -10, 6, 215),
            new Look("Ocean", 195, 35, 3, 0, 8, 160),
            new Look("Bordeaux", 345, 30, 2, 0, 13, 25),
            new Look("Slate", 215, 15, 6, 0, 12, 215),
            new Look("Moss", 95, 22, 2, 0, 11, 50),
            new Look("Teal Night", 175, 35, 3, 0, 1, 25),
            new Look("Plum", 315, 25, 3, 0, 8, 160),
            new Look("Indigo", 245, 40, 3, 0, 5, 265),
            new Look("Sand", 40, 18, 8, -5, 1, 25),
            new Look("Rosewood", 5, 25, 3, 0, 3, 330),
            new Look("Neon", 290, 45, 2, 10, 13, 175),
            new Look("Arctic", 200, 25, 8, -5, 12, 200),
            new Look("Ember", 15, 35, 2, 5, 1, 25),
            new Look("Lagoon", 185, 40, 2, 0, 10, 80),
            new Look("Lavender", 265, 20, 6, 0, 3, 330),
            new Look("Olive", 70, 25, 3, 0, 8, 160),
            new Look("Cobalt", 225, 50, 2, 5, 11, 45),
            new Look("Mocha", 30, 15, 5, -5, 13, 35),
            new Look("Emerald", 160, 40, 2, 0, 12, 160),
            new Look("Crimson", 355, 40, 1, 5, 12, 0),
            new Look("Storm", 220, 12, 4, 10, 7, 190));

    static final String CUSTOM = "Custom";
    static final String LOOK_ID = "minimal_dark_look";
    /** The settings that belong to Custom (dimmed while a preset is selected). */
    static final Set<String> CUSTOM_IDS = Set.of("background_tone_hue", "background_tone_saturation", "background_tone_luminance",
            "background_tone_contrast", "highlight_colour_choice", "highlight_colour_hue");

    /** The dropdown's options: "Custom" first, then every look. */
    static String[] labels() {
        List<String> out = new ArrayList<>();
        out.add(CUSTOM);
        for (Look l : ALL) out.add(l.name());
        return out.toArray(String[]::new);
    }

    /** The selected option (0 = Custom). First run: the preset matching the settings, else Custom (an existing setup). */
    static int current() {
        int stored = Minimal.STORE.getInt("look", -1);
        if (stored >= 0 && stored <= ALL.size()) return stored;
        int choice = Minimal.choice();
        for (int i = 0; i < ALL.size(); i++) {
            Look l = ALL.get(i);
            if (l.hue() == Tone.hue && l.sat() == Tone.sat && l.lum() == Tone.lum && l.con() == Tone.con && l.highlight() == choice) return i + 1;
        }
        return 0;
    }

    static boolean custom() { return current() == 0; }

    /** While a look is being applied its own control changes mustn't count as the user editing. */
    private static volatile boolean applying;
    /** When a Look was last chosen (slider double-clicks just after don't count). */
    static volatile long chosenAt;

    /** Applies the option at this index of labels(): 0 = the saved Custom values, else a preset. */
    static void choose(int index) {
        if (index < 0 || index > ALL.size()) return;
        if (quiet) return; // the framework storing the option we are about to apply ourselves
        chosenAt = System.nanoTime();
        Minimal.STORE.putInt("look", index);
        if (index != 0) Minimal.STORE.remove(Themes.ACTIVE);
        Look l = index == 0 ? savedCustom() : ALL.get(index - 1);
        applying = true;
        try {
            // the controls first: the Settings framework only moves a control (and tells it) when the value differs
            // from what its getter returns now, so this must come before set(l) stores the look's values
            int moved = Tone.showOnSettingsPage(Map.of(
                    "background_tone_hue", (Object) (double) l.hue(), "background_tone_saturation", (double) l.sat(),
                    "background_tone_luminance", (double) l.lum(), "background_tone_contrast", (double) l.con(),
                    "highlight_colour_choice", Minimal.LABELS[l.highlight()], "highlight_colour_hue", (double) l.customHue()));
            set(l);
            Minimal.MOD.log("look: " + (index == 0 ? "Custom" : l.name()) + " (" + moved + " controls moved)");
        } catch (Throwable t) { Minimal.MOD.log("choosing the look failed", t); }
        finally { applying = false; }
        Tone.dimControls(index == 0);
        Themes.NAME.fire();
    }

    /** A saved theme: its values become the Custom look, shown under its name. */
    static void useSaved(Look l) {
        Minimal.STORE.putInt("custom_bg_hue", l.hue()); Minimal.STORE.putInt("custom_bg_saturation", l.sat());
        Minimal.STORE.putInt("custom_bg_luminance", l.lum()); Minimal.STORE.putInt("custom_bg_contrast", l.con());
        Minimal.STORE.putInt("custom_choice", l.highlight()); Minimal.STORE.putInt("custom_hue", l.customHue());
        Minimal.STORE.put(Themes.ACTIVE, l.name());
        setDropdown(CUSTOM);
        choose(0);
        try { Minimal.STORE.flush(); } catch (Throwable ignored) {}
        Minimal.MOD.log("look: theme " + l.name());
    }

    /** An option of the Presets tab (0 = Custom, plain: no saved theme's name). */
    static void usePreset(Object dropdownValue, int index) {
        if (dropdownValue != null) Looks.dropdownValue = dropdownValue;
        Minimal.STORE.remove(Themes.ACTIVE);
        String[] labels = labels();
        if (index < 0 || index >= labels.length) return;
        setDropdown(labels[index]);
        choose(index);
    }

    /** The Look setting's stored option, through the Settings framework (so it is kept as Bitwig's settings are). */
    private static void setDropdown(String option) {
        Object v = dropdownValue;
        if (v == null) return;
        quiet = true;
        try { java.lang.reflect.Method m = v.getClass().getMethod("setValue", Object.class); m.setAccessible(true); m.invoke(v, option); }
        catch (Throwable t) { Minimal.MOD.log("updating the Look dropdown failed", t); }
        finally { quiet = false; }
    }

    /** The Settings framework's value behind the Look dropdown, once its popup has opened. */
    static volatile Object dropdownValue;
    private static volatile boolean quiet;

    /** The user changed a Background slider or a highlight control. */
    static void edited() {
        if (applying) return;
        if (!Themes.active().isEmpty()) { Minimal.STORE.remove(Themes.ACTIVE); Themes.NAME.fire(); } // a theme, changed: plain Custom now
        boolean wasPreset = !custom();
        saveCustom(); // the current values (including this change) become the Custom look
        if (wasPreset) {
            // the dropdown first (the framework only updates it while the stored option still differs); quiet: its
            // setter mustn't apply the Custom look again, the values are already the ones being edited
            applying = true; quiet = true;
            try { Tone.showOnSettingsPage(Map.of(LOOK_ID, CUSTOM)); } finally { applying = false; quiet = false; }
            Minimal.STORE.putInt("look", 0);
            Tone.dimControls(true);
            Minimal.MOD.log("look: Custom (edited from a preset)");
        }
        Themes.NAME.fire(); // the dropdown's name: Custom
    }

    private static void saveCustom() {
        Minimal.STORE.putInt("custom_bg_hue", Tone.hue); Minimal.STORE.putInt("custom_bg_saturation", Tone.sat);
        Minimal.STORE.putInt("custom_bg_luminance", Tone.lum); Minimal.STORE.putInt("custom_bg_contrast", Tone.con);
        Minimal.STORE.putInt("custom_choice", Minimal.choice()); Minimal.STORE.putInt("custom_hue", Minimal.hue());
    }

    /** The saved Custom look (first time: the settings as they are now). */
    private static Look savedCustom() {
        var s = Minimal.STORE;
        return new Look(CUSTOM, s.getInt("custom_bg_hue", Tone.hue), s.getInt("custom_bg_saturation", Tone.sat),
                s.getInt("custom_bg_luminance", Tone.lum), s.getInt("custom_bg_contrast", Tone.con),
                s.getInt("custom_choice", Minimal.choice()), s.getInt("custom_hue", Minimal.hue()));
    }

    /** Sets the tone and highlight to a look and redraws everything with it. */
    private static void set(Look l) {
        Tone.hue = l.hue(); Tone.sat = l.sat(); Tone.lum = l.lum(); Tone.con = l.con();
        Minimal.STORE.putInt("bg_hue", l.hue()); Minimal.STORE.putInt("bg_saturation", l.sat());
        Minimal.STORE.putInt("bg_luminance", l.lum()); Minimal.STORE.putInt("bg_contrast", l.con());
        Minimal.STORE.putInt("choice", l.highlight()); Minimal.STORE.putInt("hue", l.customHue());
        Minimal.apply();
        Tone.changed();
        Tone.refreshAll();
        Tone.repaint();
    }
}
