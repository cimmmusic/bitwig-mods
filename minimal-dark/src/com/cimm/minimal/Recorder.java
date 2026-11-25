package com.cimm.minimal;

import bitwig.obf.iok;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * For tuning themes: which theme colour name gives which colour, and on which kinds of widget. For the first minutes
 * after Bitwig starts, every widget colour lookup notes (name, value); the table is written to
 * colours-in-use.tsv in the mod's data folder every 15 s while it changes. Then it stops (no cost while drawing).
 */
final class Recorder {
    private Recorder() {}

    static volatile boolean on = false; // diagnostic (colours-in-use.tsv): off in the theme
    private static final long STOP_AFTER_MS = 3 * 60_000;
    private static final long started = System.currentTimeMillis();

    /** Theme colour objects by identity hash, with their names (several objects may share a name). */
    private static final Map<Object, String> names = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    /** name -> values seen (#rrggbbaa). */
    private static final Map<String, Map<String, Set<String>>> table = new ConcurrentHashMap<>();
    private static volatile boolean dirty;
    private static Thread writer;

    static void named(Object colour, String name) { names.put(colour, name); }

    static void seen(Object colour, iok value) {
        if (System.currentTimeMillis() - started > STOP_AFTER_MS) { on = false; write(); return; }
        if (value == null) return;
        String name = names.get(colour);
        if (name == null) return;
        String hex = String.format("#%02X%02X%02X%02X", c(value.FzB()), c(value.IwV()), c(value.UNj()), c(value.hUR()));
        Map<String, Set<String>> values = table.computeIfAbsent(name, k -> new ConcurrentHashMap<>());
        if (values.size() < 24 && values.putIfAbsent(hex, Set.of()) == null) { dirty = true; startWriter(); }
    }

    private static synchronized void startWriter() {
        if (writer != null) return;
        writer = new Thread(() -> {
            while (on) {
                try { Thread.sleep(15_000); } catch (InterruptedException e) { return; }
                if (dirty) write();
            }
        }, "highlight-colour-recorder");
        writer.setDaemon(true);
        writer.start();
    }

    private static synchronized void write() {
        if (!dirty) return;
        dirty = false;
        try {
            StringBuilder b = new StringBuilder("name\tvalue\n");
            for (var e : new TreeMap<>(table).entrySet())
                for (var v : new TreeMap<>(e.getValue()).entrySet())
                    b.append(e.getKey()).append('\t').append(v.getKey()).append('\n');
            Path file = Minimal.MOD.resource("colours_in_use");
            Files.createDirectories(file.getParent());
            Files.writeString(file, b.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) { Minimal.MOD.log("writing colours-in-use.tsv failed", t); }
    }

    private static int c(float v) { return Math.round(Math.max(0, Math.min(1, v)) * 255); }
}
