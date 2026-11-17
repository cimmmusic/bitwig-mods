package com.cimm.labelmodulator;

/** A Label modulator: its name starts with "Label"; the text is the rest of the name. */
final class Label {
    static final String PREFIX = "Label";
    private Label() {}

    static String name(Object modulator) {
        try {
            if (modulator instanceof com.bitwig.flt.document.core.master.device.Dgc d) { String n = d.mlQ(); if (n != null) return n; } // its own name
        } catch (Throwable ignored) {}
        try { Object n = modulator.getClass().getMethod("aF_").invoke(modulator); if (n instanceof String s) return s; } catch (Throwable ignored) {}
        return "";
    }

    static boolean isLabel(Object modulator) { return modulator != null && name(modulator).startsWith(PREFIX); }

    /** "Label: Bass bus" → "Bass bus"; "Label" alone → "Label". */
    static String text(Object modulator) {
        String n = name(modulator);
        if (!n.startsWith(PREFIX)) return n;
        String rest = n.substring(PREFIX.length()).replaceFirst("^\\s*[:\\-–]?\\s*", "");
        return rest.isEmpty() ? "Label" : rest;
    }
}
