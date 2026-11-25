package com.cimm.minimal;

import java.util.ArrayList;
import java.util.List;

/**
 * Looks: ready-made combinations of the Background sliders (hue, saturation, luminance, contrast) and the highlight
 * colour, chosen from one dropdown. Choosing one sets the settings, recolours Bitwig live, and moves the sliders and the
 * highlight dropdown on the Settings page to match (through the Settings framework's own values). Any later change to
 * those settings simply makes the dropdown read "Custom".
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
            new Look("OLED Black", 0, 0, -70, 10, 0, 30),
            new Look("Minimal Light (experimental)", 0, 0, 80, -20, 6, 215));

    static final String CUSTOM = "Custom";

    /** The dropdown's options: "Custom" first, then every look. */
    static String[] labels() {
        List<String> out = new ArrayList<>();
        out.add(CUSTOM);
        for (Look l : ALL) out.add(l.name());
        return out.toArray(String[]::new);
    }

    /** The option matching the current settings (0 = Custom). */
    static int current() {
        int choice = Minimal.choice();
        for (int i = 0; i < ALL.size(); i++) {
            Look l = ALL.get(i);
            if (l.hue() == Tone.hue && l.sat() == Tone.sat && l.lum() == Tone.lum && l.con() == Tone.con && l.highlight() == choice
                    && (choice != Minimal.CUSTOM || l.customHue() == Minimal.hue())) return i + 1;
        }
        return 0;
    }

    /** Applies the option at this index of labels() (0 = Custom: nothing to do). */
    static void choose(int index) {
        if (index <= 0 || index > ALL.size()) return;
        Look l = ALL.get(index - 1);
        try {
            Tone.hue = l.hue(); Tone.sat = l.sat(); Tone.lum = l.lum(); Tone.con = l.con();
            Minimal.STORE.putInt("bg_hue", l.hue()); Minimal.STORE.putInt("bg_saturation", l.sat());
            Minimal.STORE.putInt("bg_luminance", l.lum()); Minimal.STORE.putInt("bg_contrast", l.con());
            Minimal.STORE.putInt("choice", l.highlight()); Minimal.STORE.putInt("hue", l.customHue());
            Minimal.apply();
            Tone.changed();
            Tone.repaint();
            int moved = Tone.showOnSettingsPage(java.util.Map.of(
                    "background_tone_hue", (Object) (double) l.hue(), "background_tone_saturation", (double) l.sat(),
                    "background_tone_luminance", (double) l.lum(), "background_tone_contrast", (double) l.con(),
                    "highlight_colour_choice", Minimal.LABELS[l.highlight()], "highlight_colour_hue", (double) l.customHue()));
            Minimal.MOD.log("look: " + l.name() + " (" + moved + " controls moved)");
        } catch (Throwable t) { Minimal.MOD.log("choosing the look " + l.name() + " failed", t); }
    }
}
