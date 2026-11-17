package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device
import java.util.Locale;

/**
 * Mini view for Bitwig's "Filter+", in the EQ views' language: the frequency grid, a response curve in Filter+'s green
 * (the device's filter-section colour; user's screenshot, 2026-11-17) and one
 * white node at the cutoff — left/right = cutoff, up/down = resonance (the peak at the cutoff grows with it). The curve's
 * kind follows the filter type's display text (low-pass / high-pass / band-pass / notch; poles from its number); drive,
 * morph and the models' character aren't drawn (they colour the sound rather than the shape). Parameters are found by
 * name and logged on first use.
 */
final class FilterGraph extends MiniGraph {
    static final iok GRID_MINOR = EqGraph.GRID_MINOR;
    static final double RANGE = 24; // dB shown above / below 0
    static final iok CURVE = iok.cfM(120, 200, 70);   // Filter+'s green (its filter section)

    Params.P cutoff, resonance, drive;
    static final iok DRIVE = iok.cfM(240, 205, 70);   // yellow, as Filter+'s drive (Heat) section
    final String typeId, modeId;
    /** Filter+'s module slots (rSf, under CONTENTS/…/MODULES) by path; the filter's and the waveshaper's. */
    final java.util.Map<String, Object> slots = new java.util.LinkedHashMap<>();
    Object filterSlot, shaperSlot;
    java.util.UUID filterModule, shaperModule;
    /** Choice parameters inside the modules (e.g. a filter's mode), by path. */
    final java.util.Map<String, Object> slotChoices = new java.util.LinkedHashMap<>();

    FilterGraph(jsd device) {
        super(device, 110);
        // Filter+ keeps cutoff, resonance and drive inside its swappable modules (CONTENTS/…/MODULES/n), not in its
        // top-level parameters (only PITCH_TRANSPOSE, MIX, AUTO_RELEASE): walk the contents tree.
        resolve(true);
        typeId = firstChoice("TYPE", "MODEL", "FILTER");
        modeId = firstChoice("MODE", "RESPONSE", "SHAPE");
        ModuleMenu.init(); // start the module catalogue loading now, so it's ready for the first right-click
    }

    /** (Re)finds the module slots and the parameters the view uses; after a module swap the slot contents change. */
    void resolve(boolean log) {
        slots.clear(); slotChoices.clear();
        java.util.List<Params.P> nested = nested(device, slots, slotChoices, log);
        filterSlot = null; shaperSlot = null;
        for (java.util.Map.Entry<String, Object> e : slots.entrySet()) {
            java.util.UUID id = ModuleMenu.current(e.getValue());
            boolean hasCutoff = false;
            for (Params.P p : nested) if (p.id().startsWith(e.getKey() + "/") && p.key().contains("cutoff")) hasCutoff = true;
            if (hasCutoff && filterSlot == null) filterSlot = e.getValue();
            else if (shaperSlot == null && id != null && ModuleMenu.layoutOf(id) >= 0 && !hasCutoff
                    && ModuleMenu.layoutOf(id) == ModuleMenu.layoutOf(waveshaperHint())) shaperSlot = e.getValue();
        }
        String fp = pathOf(filterSlot), sp = pathOf(shaperSlot);
        cutoff = pickIn(nested, fp, "cutoff", "frequency", "freq");
        resonance = pickIn(nested, fp, "resonance", "reso");
        drive = pickIn(nested, sp, "drive", "heat", "amount", "gain");
        if (drive == null && sp != null) for (Params.P p : nested) if (p.id().startsWith(sp + "/")) { drive = p; break; } // the shaper's first knob
        filterModule = ModuleMenu.current(filterSlot);
        shaperModule = ModuleMenu.current(shaperSlot);
        if (log) MOD.log("Filter+ view: filter slot " + fp + " '" + ModuleMenu.name(filterModule) + "' shaper slot " + sp + " '"
                + ModuleMenu.name(shaperModule) + "' cutoff=" + EqGraphId(cutoff) + " resonance=" + EqGraphId(resonance) + " drive=" + EqGraphId(drive));
    }

    /** Heat's id (any module in the waveshaper layout would do): the waveshaper slot is the one whose module is in
     *  the same menu layout. Found from the catalogue by name; null until it has loaded. */
    static java.util.UUID waveshaperHint() {
        for (bitwig.obf.uE u : ModuleMenu.modules()) if ("Heat".equals(ModuleMenu.str(u.LaE()))) return u.hY_();
        return null;
    }

    String pathOf(Object slot) {
        if (slot == null) return null;
        for (java.util.Map.Entry<String, Object> e : slots.entrySet()) if (e.getValue() == slot) return e.getKey();
        return null;
    }

    Params.P pickIn(java.util.List<Params.P> nested, String prefix, String... words) {
        if (prefix == null) return null;
        for (String w : words) for (Params.P p : nested) if (p.id().startsWith(prefix + "/") && p.key().contains(w)) return p;
        return null;
    }

    private long nextCheck, nextRefresh, forceUntil;
    private double lastSig = Double.NaN;
    /** Every half second (and right after our own swaps): re-resolve when a slot's module changed; repaint on value changes. */
    @Override void tick() {
        long now = System.currentTimeMillis();
        if (now >= nextCheck) {
            nextCheck = now + (now < forceUntil ? 150 : 500);
            boolean stale = now < forceUntil || now >= nextRefresh
                    || !java.util.Objects.equals(ModuleMenu.current(filterSlot), filterModule)
                    || !java.util.Objects.equals(ModuleMenu.current(shaperSlot), shaperModule);
            if (stale) { resolve(false); nextRefresh = now + 3000; } // module swaps made in the device panel: picked up within 3 s
        }
        double sig = (cutoff == null ? 0 : cutoff.value()) + 3.1 * (resonance == null ? 0 : resonance.value())
                + 7.3 * (drive == null ? 0 : drive.value()) + typeText().hashCode() * 1e-6;
        if (sig != lastSig) { lastSig = sig; jbi(); }
    }

    void afterSwap() { nextCheck = 0; forceUntil = System.currentTimeMillis() + 1500; } // the swap lands asynchronously

    /** Cutoff, resonance and drive live in Filter+'s modules, outside the top-level list. */
    @Override java.util.List<Params.P> watched() {
        java.util.List<Params.P> l = new java.util.ArrayList<>(params.all);
        for (Params.P p : new Params.P[] {cutoff, resonance, drive}) if (p != null) l.add(p);
        return l;
    }

    /** Right-click on the cutoff node: the filter module's menu; on the yellow node: the waveshaper's — as Filter+'s own choosers. */
    @Override boolean menu(bitwig.obf.dKW popup, double x, double y, double w, double h) {
        double[] k = node(w, h);
        if (filterSlot != null && Math.hypot(x - k[0], y - k[1]) <= 12) return ModuleMenu.fill(popup, this, filterSlot, this::afterSwap);
        if (shaperSlot != null && drive != null && Math.hypot(x - (w - 12), y - driveY(h)) <= 12) return ModuleMenu.fill(popup, this, shaperSlot, this::afterSwap);
        return false;
    }

    /** The first parameter (top-level or nested) whose ID or name contains one of the words. */
    Params.P pick(java.util.List<Params.P> nested, String... words) {
        for (String w : words) { Params.P p = params.find(w); if (p != null) return p; }
        for (String w : words) for (Params.P p : nested) if (p.key().contains(w)) return p;
        return null;
    }

    /** Settable parameters anywhere in the device's contents tree (Filter+'s filter / FX modules are nested objects
     *  under its contents, not top-level parameters), found by walking child lists reflectively; the tree is logged. */
    static java.util.List<Params.P> nested(jsd device, java.util.Map<String, Object> slots, java.util.Map<String, Object> choices, boolean log) {
        long t0 = System.nanoTime();
        java.util.List<Params.P> out = new java.util.ArrayList<>();
        Object root = Reflect.call(device, "puu");
        // Fast path: only follow CONTENTS / POLY / MODULES / numbered items down to the module slots, then everything in
        // a slot. (The whole tree is ~4,400 nodes and took ~400 ms per view build — the delay selecting a channel.)
        try { walk(root, "", 0, out, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), true, false, slots, choices); }
        catch (Throwable t) { MOD.log("Filter+ walk failed: " + t); }
        boolean full = false;
        if (slots.isEmpty()) { // unexpected layout: the full walk
            out.clear(); choices.clear(); full = true;
            try { walk(root, "", 0, out, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), false, false, slots, choices); }
            catch (Throwable t) { MOD.log("Filter+ walk failed: " + t); }
        }
        if (log) MOD.log("Filter+ parameters: " + out.size() + " in " + slots.size() + " module slots" + (full ? " (full walk)" : "")
                + ", " + (System.nanoTime() - t0) / 1000 + " µs");
        return out;
    }

    /** Zero-argument getters that return a node's children (on Bitwig's document classes i2s / BVs / BtA / SaG / to / K2G). */
    static final String[] CHILD_LISTS = {"SQN", "E3", "NMA", "emh", "jhH", "som", "mlQ", "ghS", "TCw", "zVc", "SqI", "ZJI", "cg_", "uW3", "Bee", "tB1", "IwV", "BBT"};
    /** Getters that return a nested device's contents (AxS.puu, iyP.QOl). */
    static final String[] SUB_CONTENTS = {"puu", "QOl"};

    /** Ids followed on the way down to Filter+'s module slots (focused walk); numbered and unnamed items are followed too. */
    static final java.util.Set<String> PATH_IDS = java.util.Set.of("CONTENTS", "POLY", "MODULES");

    static void walk(Object o, String path, int depth, java.util.List<Params.P> out, java.util.Set<Object> seen, boolean focused,
                     boolean inSlot, java.util.Map<String, Object> slots, java.util.Map<String, Object> choices) {
        if (o == null || depth > 9 || !seen.add(o)) return;
        String id = str(getter(o, "ax_", String.class));
        if (focused && !inSlot && depth > 0 && !id.isEmpty() && !PATH_IDS.contains(id) && !isNumber(id)) return; // prune named side branches (Pre/Post FX chains, ports…)
        String here = path.isEmpty() ? id : (id.isEmpty() ? path : path + "/" + id);
        if (o instanceof bitwig.obf.hSh h) { out.add(new Params.P(here, str(getter(o, "aF_", String.class)), h)); return; }
        boolean slot = o.getClass().getSimpleName().equals("rSf");
        if (slot) slots.put(here, o); // a module slot
        if (o instanceof bitwig.obf.v3U) { choices.put(here, o); return; } // other parameter kinds (choices, switches): leaves
        for (String m : CHILD_LISTS) {
            Object r = getter(o, m, java.util.List.class, java.util.stream.Stream.class);
            java.util.List<?> items = null;
            if (r instanceof java.util.List<?> l) items = new java.util.ArrayList<>(l);
            else if (r instanceof java.util.stream.Stream<?> st) items = st.limit(200).toList();
            if (items == null) continue;
            for (Object c : items) walk(c, here, depth + 1, out, seen, focused, inSlot || slot, slots, choices);
        }
        for (String m : SUB_CONTENTS) walk(getter(o, m, Object.class), here, depth + 1, out, seen, focused, inSlot || slot, slots, choices);
    }

    static boolean isNumber(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    /** Public zero-argument methods by class and name (null when missing), so the walk doesn't look each one up again. */
    private static final ClassValue<java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<java.lang.reflect.Method>>> METHODS =
            new ClassValue<>() {
                @Override protected java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<java.lang.reflect.Method>> computeValue(Class<?> c) {
                    return new java.util.concurrent.ConcurrentHashMap<>();
                }
            };

    /** Calls the public zero-argument method only if it returns one of the given types (never a void/other method that
     *  happens to share the obfuscated name). */
    static Object getter(Object o, String name, Class<?>... types) {
        try {
            java.lang.reflect.Method m = METHODS.get(o.getClass()).computeIfAbsent(name, k -> {
                try { java.lang.reflect.Method x = o.getClass().getMethod(k); x.setAccessible(true); return java.util.Optional.of(x); }
                catch (Throwable t) { return java.util.Optional.empty(); }
            }).orElse(null);
            if (m == null) return null;
            boolean ok = false;
            for (Class<?> t : types) ok |= t.isAssignableFrom(m.getReturnType());
            if (!ok) return null;
            return m.invoke(o);
        } catch (Throwable t) { return null; }
    }

    static String str(Object o) { return o instanceof String s ? s : ""; }

    /** A parameter object's own name (not its value text), via its no-argument String getters. */
    static String label(Object o) {
        StringBuilder sb = new StringBuilder();
        for (String m : new String[] {"aF_", "W_", "QFI", "BDm", "ax_"}) {
            Object r = Reflect.call(o, m);
            if (r instanceof String s && !s.isBlank()) sb.append(s).append(' ');
        }
        return sb.toString().trim();
    }

    /** o itself, or what its no-argument getters return (one level), when it is a settable parameter. */
    static void collect(Object o, java.util.List<Params.P> out, java.util.Set<Object> seen, int depth) {
        if (o == null || depth > 1) return;
        if (o instanceof bitwig.obf.hSh h) { if (seen.add(h)) out.add(new Params.P("rc", Params.name(o) + " " + label(o), h)); return; }
        for (java.lang.reflect.Method m : o.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || m.getDeclaringClass() == Object.class) continue;
            Class<?> r = m.getReturnType();
            if (!bitwig.obf.v3U.class.isAssignableFrom(r) && !bitwig.obf.hSh.class.isAssignableFrom(r)) continue;
            try { collect(m.invoke(o), out, seen, depth + 1); } catch (Throwable ignored) {}
        }
    }

    static String EqGraphId(Params.P p) { return p == null ? "none" : p.id() + " [" + p.min() + ", " + p.max() + "] = " + p.value(); }

    String firstChoice(String... words) {
        for (String w : words) for (String id : params.choices.keySet()) if (id.toUpperCase(Locale.ROOT).contains(w)) return id;
        return null;
    }

    // ---- the device's values ----
    /** Cutoff in Hz: a parameter whose range goes past 200 is Hz; otherwise a pitch (69 = 440 Hz). */
    double hz() {
        if (cutoff == null) return 1000;
        return cutoff.max() > 200 ? cutoff.value() : 440 * Math.pow(2, (cutoff.value() - 69) / 12);
    }
    double cutoffValue(double hz) {
        return cutoff.max() > 200 ? hz : 69 + 12 * Math.log(hz / 440) / Math.log(2);
    }
    double res() { return resonance == null ? 0.2 : clamp(resonance.norm(), 0, 1); }

    String typeText() {
        String t = (typeId == null ? "" : params.text(typeId)) + " " + (modeId == null ? "" : params.text(modeId))
                + " " + ModuleMenu.name(filterModule);
        String fp = pathOf(filterSlot);
        if (fp != null) for (java.util.Map.Entry<String, Object> e : slotChoices.entrySet())
            if (e.getKey().startsWith(fp + "/")) t += " " + Params.name(e.getValue());
        return t.toLowerCase(Locale.ROOT);
    }

    EqGraph.Kind kind() {
        String t = typeText();
        if (t.contains("notch") || t.contains("band-reject") || t.contains("br")) return EqGraph.Kind.NOTCH;
        if (t.contains("band") || t.contains("bp")) return EqGraph.Kind.BAND_PASS;
        if (t.contains("high") || t.contains("hp")) return EqGraph.Kind.HIGH_PASS;
        return EqGraph.Kind.LOW_PASS;
    }

    int poles() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(typeText());
        int n = 2;
        while (m.find()) { int v = Integer.parseInt(m.group(1)); if (v == 1 || v == 2 || v == 3 || v == 4 || v == 6 || v == 8) { n = v; break; } if (v == 12 || v == 18 || v == 24) { n = v / 6; break; } }
        return n;
    }

    /** Response in dB at f: Butterworth sections, the last one's Q raised by the resonance. */
    double db(double f) {
        EqGraph.Kind k = kind();
        double fc = hz(), qRes = 0.707 + res() * 9;
        if (k == EqGraph.Kind.BAND_PASS || k == EqGraph.Kind.NOTCH) {
            double q = 0.5 + res() * 8;
            return EqGraph.biquadDb(EqGraph.rbj(k, fc, 0, q), f);
        }
        int n = poles();
        double sum = 0;
        for (int i = 0; i < n / 2; i++) {
            double qk = 1 / (2 * Math.sin((2 * i + 1) * Math.PI / (2 * n)));
            if (i == n / 2 - 1) qk = Math.max(qk, qRes * qk / 0.707);
            sum += EqGraph.biquadDb(EqGraph.rbj(k, fc, 0, qk), f);
        }
        if (n % 2 == 1) { double r = k == EqGraph.Kind.HIGH_PASS ? f / fc : fc / f; sum += 10 * Math.log10(r * r / (1 + r * r)); }
        return sum;
    }

    // ---- geometry (as EqGraph) ----
    double fx(double f, double w) { return w * Math.log(f / EqGraph.FMIN) / Math.log(EqGraph.FMAX / EqGraph.FMIN); }
    double xf(double x, double w) { return EqGraph.FMIN * Math.pow(EqGraph.FMAX / EqGraph.FMIN, x / w); }
    double dy(double d, double h) { return h / 2 - d / RANGE * (h / 2 - 5); }

    double[] node(double w, double h) {
        double f = clamp(hz(), EqGraph.FMIN, EqGraph.FMAX);
        return new double[] {fx(f, w), dy(clamp(db(f), -RANGE, RANGE), h)};
    }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        g.IwV(1);
        col(g, GRID_MINOR);
        for (double dec = 10; dec <= 10000; dec *= 10)
            for (int m = 2; m <= 9; m++) { double f = dec * m; if (f < EqGraph.FMIN || f > EqGraph.FMAX) continue; double x = Math.floor(fx(f, w)) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        col(g, GRID_MAJOR);
        for (double f : new double[] {100, 1000, 10000}) { double x = Math.floor(fx(f, w)) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        double y0 = Math.floor(h / 2) + 0.5; g.MRY(0, y0); g.cfM(w, y0); g.hbP();

        // Response curve in Filter+'s green.
        col(g, CURVE); g.IwV(1.8);
        int n = (int) Math.max(24, w / 1.5);
        for (int i = 0; i <= n; i++) {
            double x = w * i / n, y = dy(clamp(db(xf(x, w)), -RANGE - 6, RANGE), h);
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();
        if (drive != null) { // drive: a yellow line across, its height the drive amount, node at the right
            double yd = driveY(h);
            col(g, DRIVE); g.IwV(1.4); g.MRY(0, yd); g.cfM(w, yd); g.hbP();
            g.MRY(w - 12, yd, 5); g.wGc();
        }
        double[] k = node(w, h);
        col(g, NODE); g.MRY(k[0], k[1], 5); g.wGc();
    }

    double driveY(double h) { return h - 6 - clamp(drive.norm(), 0, 1) * (h - 12); }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        double[] k = node(w, h);
        if (drive != null && Math.hypot(x - (w - 12), y - driveY(h)) <= 10) { // the drive node: up/down
            final Object undo = new Object();
            final double[] n0 = {clamp(drive.norm(), 0, 1)};
            return (ddx, ddy) -> {
                n0[0] = clamp(n0[0] - ddy / (h - 12), 0, 1);
                set(drive, drive.min() + (drive.max() - drive.min()) * n0[0], undo, "Change drive");
            };
        }
        if (cutoff == null || Math.hypot(x - k[0], y - k[1]) > 12) return (ddx, ddy) -> {};
        final Object undo = new Object();
        final double[] pos = {k[0]};
        final double[] r0 = {res()};
        return (ddx, ddy) -> { // left/right: cutoff; up/down: resonance
            pos[0] += ddx;
            set(cutoff, cutoffValue(xf(clamp(pos[0], 0, w), w)), undo, "Change cutoff");
            if (resonance != null) {
                r0[0] = clamp(r0[0] - ddy / (h * 0.8), 0, 1);
                set(resonance, resonance.min() + (resonance.max() - resonance.min()) * r0[0], undo, "Change resonance");
            }
        };
    }
}
