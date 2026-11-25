package com.cimm.uifont;

import java.util.Map;

/**
 * Sentence-case toolbar menus.
 *
 * Bitwig's toolbar menu names (FILE, ADD, EDIT, TRACK, PLAY...) are written in capitals in its text files
 * (localization/Bitwig Studio-resources*.properties, keys toolbar.&lt;menu&gt;.name). Every text file is read into a map by
 * YGP.cfM(file), called from YGP.cfM(files); the hook in UiFont changes the capitalised toolbar names in that map to sentence case (File, Add, Play), so
 * Bitwig shows them that way everywhere it uses them. Applies when Bitwig starts.
 */
public final class Labels {
    private Labels() {}

    static final String KEY = "sentence_case_toolbar";

    static boolean on() {
        try { return Fonts.STORE.getBoolean(KEY, true); } catch (Throwable t) { return true; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Map sentenceCase(Map texts) {
        if (texts == null || !on()) return texts;
        int changed = 0;
        try {
            for (Map.Entry e : (Iterable<Map.Entry>) texts.entrySet()) {
                if (!(e.getKey() instanceof String key) || !(e.getValue() instanceof String text)) continue;
                if (!key.startsWith("toolbar.") || !key.endsWith(".name")) continue;
                String s = sentence(text);
                if (!s.equals(text)) { e.setValue(s); changed++; }
            }
            if (changed > 0) Fonts.MOD.log("toolbar menus in sentence case: " + changed);
        } catch (Throwable t) { Fonts.MOD.log("sentence case failed", t); }
        return texts;
    }

    /** "FILE" -> "File", "MODULATOR" -> "Modulator"; text that isn't all capitals is left as it is. */
    static String sentence(String s) {
        if (s.isEmpty() || !s.equals(s.toUpperCase()) || s.equals(s.toLowerCase())) return s;
        int first = s.offsetByCodePoints(0, 1);
        return s.substring(0, first) + s.substring(first).toLowerCase();
    }
}
