package com.cimm.mixerminipanels;

import java.util.Map;

/**
 * Bitwig member names used by name (reflection). Patchbay translates the mod's compiled calls to the running Bitwig,
 * but not names written as text, and Bitwig 6.1.7 renamed nearly everything. These are the 6.1.4 → 6.1.7 names
 * (from Patchbay's map macos-6.1.4__macos-6.1.7, the dominant rename of each zero-argument method); on 6.1.4 the
 * names pass through unchanged.
 */
final class Names {
    private Names() {}

    /** Running on 6.1.7? (The module chooser's layout field hUR is called Anf there; the class itself is remapped.) */
    static final boolean V617 = hasField(bitwig.obf.auG.class, "Anf") && !hasField(bitwig.obf.auG.class, "hUR");

    private static final Map<String, String> METHODS = Map.ofEntries(
            Map.entry("puu", "edx"), Map.entry("QOl", "MvA"), Map.entry("ax_", "ay_"), Map.entry("aF_", "aG_"),
            Map.entry("SQN", "ogS"), Map.entry("E3", "ymo"), Map.entry("NMA", "Ktd"), Map.entry("emh", "mYH"),
            Map.entry("jhH", "aKN"), Map.entry("som", "FyV"), Map.entry("mlQ", "pUD"), Map.entry("ghS", "m1z"),
            Map.entry("TCw", "cw"), Map.entry("zVc", "Boz"), Map.entry("SqI", "aMq"), Map.entry("ZJI", "yuT"),
            Map.entry("cg_", "ch_"), Map.entry("uW3", "VCp"), Map.entry("Bee", "EDy"), Map.entry("tB1", "vUU"),
            Map.entry("IwV", "BBg"), Map.entry("BBT", "vlg"), Map.entry("W_", "X_"), Map.entry("BDm", "Bqd"),
            Map.entry("aS_", "aT_"), Map.entry("sut", "Xit"), Map.entry("iM", "Dnd"), Map.entry("CXm", "VwA"),
            Map.entry("nWt", "Tuv"), Map.entry("upC", "wzf"), Map.entry("fFs", "ccv"), Map.entry("qFI", "sSO"),
            Map.entry("Pgx", "p1J"), Map.entry("xL", "n1g"), Map.entry("vpw", "af"), Map.entry("OzW", "fmw"),
            Map.entry("MJ", "neH"), Map.entry("rGR", "XnJ"), Map.entry("RDJ", "yX3"), Map.entry("H2K", "CKj"),
            Map.entry("au_", "av_"));

    /** Fields read by name: the module chooser's layouts (auG) and its kind map (auI). */
    private static final Map<String, String> FIELDS = Map.of(
            "auG.hUR", "Anf", "auG.TMF", "byf", "auG.lYc", "Bqk", "auG.E3", "ymo", "auI.cfM", "beI");

    /** Simple class names compared by text: the module slot. */
    private static final Map<String, String> CLASSES = Map.of("rSf", "ZFS");

    static String method(String name) { return V617 ? METHODS.getOrDefault(name, name) : name; }
    static String field(String owner, String name) { return V617 ? FIELDS.getOrDefault(owner + "." + name, name) : name; }
    static String cls(String simpleName) { return V617 ? CLASSES.getOrDefault(simpleName, simpleName) : simpleName; }

    private static boolean hasField(Class<?> c, String name) {
        try { c.getDeclaredField(name); return true; } catch (Throwable t) { return false; }
    }
}
