package com.cimm.minimal;

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

    /**
     * Instead of Bitwig's toUpperCase() for headers and tabs (DEVICE, INSTRUMENT TRACK, SETTINGS...): sentence case.
     * The first letter is a capital; later words written as Capitalised or ALL CAPS (4+ letters) become lower case;
     * short acronyms (FX, MIDI, CPU) and words like "DAWproject" stay as they are. Off: Bitwig's capitals.
     */
    /** Words kept in capitals. */
    static final java.util.Set<String> ACRONYMS = java.util.Set.of("MIDI", "FX", "CPU", "MPE", "OSC", "VST", "VST2", "VST3", "CLAP",
            "DSP", "LFO", "ADSR", "AHDSR", "BPM", "EQ", "CC", "IO", "DAW", "AU", "USB", "HQ", "UI", "ID", "OK", "MIX", "AB", "XY", "DJ", "MSEG", "ASIO", "PDF", "WAV", "MP3", "AI");

    private static final java.util.Set<String> seenCaps = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** caps(), logging the first texts seen from each call site (to find what still shows in capitals). */
    static String caps(String s, int site) {
        String r = caps(s);
        if (s != null && seenCaps.size() < 80 && seenCaps.add(site + ":" + s)) Fonts.MOD.log("sentence case #" + site + ": \"" + s + "\" -> \"" + r + "\"");
        return r;
    }

    static String caps(String s) {
        if (s == null) return null;
        if (!on()) return s.toUpperCase();
        try {
            StringBuilder out = new StringBuilder(s.length());
            String[] words = s.split(" ", -1);
            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                if (i > 0) out.append(' ');
                if (w.isEmpty()) continue;
                boolean allCaps = w.equals(w.toUpperCase()) && !w.equals(w.toLowerCase());
                boolean titled = Character.isUpperCase(w.charAt(0)) && w.length() > 1 && w.substring(1).equals(w.substring(1).toLowerCase());
                String r = w;
                if (allCaps && !ACRONYMS.contains(w.replaceAll("[^A-Za-z0-9]", ""))) r = w.toLowerCase();
                else if (titled && i > 0) r = w.toLowerCase();
                if (i == 0 && !r.isEmpty()) r = Character.toUpperCase(r.charAt(0)) + r.substring(1);
                out.append(r);
            }
            return out.toString();
        } catch (Throwable t) { return s.toUpperCase(); }
    }

    /** "FILE" -> "File", "MODULATOR" -> "Modulator"; text that isn't all capitals is left as it is. */
    static String sentence(String s) {
        if (s.isEmpty() || !s.equals(s.toUpperCase()) || s.equals(s.toLowerCase())) return s;
        int first = s.offsetByCodePoints(0, 1);
        return s.substring(0, first) + s.substring(first).toLowerCase();
    }
}
