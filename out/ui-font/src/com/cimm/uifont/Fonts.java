package com.cimm.uifont;

import bml.api.Bml;
import bml.api.Settings;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The user's font choices, and which of Bitwig's font files each one replaces. */
final class Fonts {
    static final Bml.Mod MOD = Bml.mod("ui-font");
    static final Settings STORE = MOD.settings();

    /** Settings keys; each holds the chosen font file's full path, or "" for Bitwig's own font. */
    static final String INTERFACE = "interface", BOLD = "bold", NUMBERS = "numbers";
    /** Stored for BOLD when it should follow the interface font (the default). */
    static final String SAME = "same";

    /** Bitwig's own UI font files (in its Resources/fonts) and the choice that replaces each. */
    private static final Map<String, String> ROLE = Map.of(
            "Lato-Regular.ttf", INTERFACE,
            "Lato-Light.ttf", INTERFACE,
            "Lato-Italic.ttf", INTERFACE,
            "Lato-Medium.ttf", BOLD,
            "Lato-Semibold.ttf", BOLD,
            "CPMono_v07_Plain.otf", NUMBERS,
            "SourceCodePro-Regular.ttf", NUMBERS);

    private static final Set<String> logged = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private Fonts() {}

    /** The font file to load instead of Bitwig's file of this name, or null to keep Bitwig's. */
    static File replacement(String bitwigFile) {
        try {
            String name = bitwigFile == null ? "" : new File(bitwigFile).getName();
            String role = ROLE.get(name);
            if (role == null) return null;
            String path = STORE.get(role, role.equals(BOLD) ? SAME : "");
            if (role.equals(BOLD) && SAME.equals(path)) path = STORE.get(INTERFACE, "");
            if (path == null || path.isEmpty()) return null;
            File f = new File(path);
            if (!f.isFile()) {
                if (logged.add("missing " + path)) MOD.log("font not found, using Bitwig's own for " + name + ": " + path);
                return null;
            }
            if (logged.add(name)) MOD.log(name + " -> " + f);
            return f;
        } catch (Throwable t) {
            if (logged.add("error")) MOD.log("font choice failed", t);
            return null;
        }
    }

    /** One font a user can pick. */
    record Font(String label, String path) {}

    private static List<Font> catalogue;

    /** Every font file found on this Mac and in the mod's fonts folder, sorted by name. */
    static synchronized List<Font> catalogue() {
        if (catalogue != null) return catalogue;
        List<File> dirs = new ArrayList<>();
        try { Path own = MOD.resource("fonts"); if (own != null) dirs.add(own.toFile()); } catch (Throwable ignored) {}
        String home = System.getProperty("user.home");
        dirs.add(new File(home, "Library/Fonts"));
        dirs.add(new File("/Library/Fonts"));
        dirs.add(new File("/System/Library/Fonts"));
        dirs.add(new File("/System/Library/Fonts/Supplemental"));
        Map<String, File> byLabel = new HashMap<>();
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            java.util.Arrays.sort(files);
            for (File f : files) {
                String n = f.getName(), lower = n.toLowerCase(Locale.ROOT);
                if (!f.isFile() || n.startsWith(".")) continue;
                if (!(lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc"))) continue;
                String label = FontNames.read(f); // the font's own name, as font menus show it
                if (label == null) label = n.substring(0, n.lastIndexOf('.'));
                label = label.replaceAll("\\s+", " ").trim();
                if (label.isEmpty() || label.startsWith(".")) continue; // macOS's hidden system fonts
                byLabel.putIfAbsent(label, f); // the same font twice: the first found (the mod's folder, then yours)
            }
        }
        List<Font> list = new ArrayList<>();
        byLabel.forEach((label, f) -> list.add(new Font(label, f.getAbsolutePath())));
        list.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
        catalogue = List.copyOf(list);
        return catalogue;
    }
}
