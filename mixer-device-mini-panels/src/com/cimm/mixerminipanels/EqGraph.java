package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mini EQ+ / EQ-5 / EQ-2 (same view; each EQ's own bands, band types and gain range): four bands (separators at 100 Hz, 1 kHz, 10 kHz), EQ+'s rainbow response curve and a white dot per
 * active band. Drag a node: left/right = frequency; up/down = gain (bell, shelves) or Q (cuts, notch).
 *
 * EQ+ parameters (Bitwig 6.1.4): FREQn = pitch (69 = 440 Hz), GAINn = dB, Qn = log10(Q), TYPEn / ENABLEn = choice /
 * switch (read as their displayed text), GLOBAL_SHIFT = semitones, OUTPUT_GAIN = dB, DECIBEL_RANGE = "±10" etc.
 */
final class EqGraph extends MiniGraph {
    static final int BANDS = 8;
    static final double FMIN = 20, FMAX = 20000, FS = 48000;
    private static final Set<String> seenTypes = ConcurrentHashMap.newKeySet();

    final Params.P[] freq = new Params.P[BANDS], gain = new Params.P[BANDS], q = new Params.P[BANDS];
    final String[] typeId = new String[BANDS], enableId = new String[BANDS];
    final Params.P shift, out, amount;

    EqGraph(jsd device) {
        super(device, 110);
        boolean eq2 = params.find("contents/lo_freq") != null;
        for (int i = 0; i < BANDS; i++) {
            int n = i + 1;
            if (eq2) { // EQ-2: a low and a high band
                if (i > 1) break;
                String pre = i == 0 ? "contents/lo_" : "contents/hi_";
                freq[i] = params.find(pre + "freq"); gain[i] = params.find(pre + "gain"); q[i] = params.find(pre + "q");
                // EQ-2 (Bitwig 6.1.4, from the mod log): CONTENTS/TYPE1 + LO_ENABLE, CONTENTS/HI_TYPE + HI_ENABLE
                typeId[i] = firstChoice(i == 0 ? "CONTENTS/TYPE1" : "CONTENTS/HI_TYPE", choiceId(i == 0 ? "lo" : "hi", "type"));
                enableId[i] = firstChoice(i == 0 ? "CONTENTS/LO_ENABLE" : "CONTENTS/HI_ENABLE", choiceId(i == 0 ? "lo" : "hi", "enable"));
            } else { // EQ+ (8 bands), EQ-5 (5 bands)
                freq[i] = params.find("contents/freq" + n); gain[i] = params.find("contents/gain" + n); q[i] = params.find("contents/q" + n);
                typeId[i] = "CONTENTS/TYPE" + n; enableId[i] = "CONTENTS/ENABLE" + n;
            }
        }
        shift = params.find("contents/global_shift");
        out = params.find("contents/output_gain");
        amount = params.find("contents/global_amount"); // EQ-5: scales every band's gain (0…100 %)
    }

    // ---- band model ----
    enum Kind { OFF, BELL, LOW_SHELF, HIGH_SHELF, HIGH_PASS, LOW_PASS, NOTCH, BAND_PASS }
    record Band(Kind kind, int order, double f, double g, double q) {}

    /** Frequency in Hz: EQ+ stores a pitch (69 = 440 Hz); a parameter whose range goes past 200 is already in Hz. */
    boolean hzParam(int i) { return freq[i].max() > 200; }
    double hzOf(int i, double sh) { return hzParam(i) ? freq[i].value() * Math.pow(2, sh / 12) : 440 * Math.pow(2, (freq[i].value() + sh - 69) / 12); }
    double freqValue(int i, double hz, double sh) { return hzParam(i) ? hz / Math.pow(2, sh / 12) : 69 + 12 * Math.log(hz / 440) / Math.log(2) - sh; }
    /** Q: EQ+ stores log10(Q) (range below zero); otherwise the plain Q. */
    double qOf(int i) { if (q[i] == null) return 1; return q[i].min() < 0 ? Math.pow(10, q[i].value()) : Math.max(0.05, q[i].value()); }

    String typeText(int i) { return typeId[i] == null ? "" : params.text(typeId[i]); }

    Band band(int i) {
        if (freq[i] == null || gain[i] == null) return new Band(Kind.OFF, 0, 1000, 0, 1);
        String t = typeText(i).toLowerCase(Locale.ROOT), en = enableId[i] == null ? "" : params.text(enableId[i]).toLowerCase(Locale.ROOT);
        if (!t.isEmpty() && seenTypes.add(t)) MOD.log("EQ+ band type text: '" + t + "'");
        Kind k;
        if (t.equals("off") || en.equals("off")) k = Kind.OFF;
        else if (t.isEmpty()) k = typeId[i] != null && params.choices.containsKey(typeId[i]) ? Kind.OFF : Kind.BELL;
        else if (t.contains("notch")) k = Kind.NOTCH;
        else if (t.contains("shelf")) k = t.contains("high") || t.contains("hi") ? Kind.HIGH_SHELF : Kind.LOW_SHELF;
        else if (t.contains("band")) k = Kind.BAND_PASS;
        else if (t.contains("low-cut") || t.contains("low cut") || t.contains("lowcut") || t.contains("high-pass") || t.contains("high pass") || t.contains("hp") || t.contains("lc")) k = Kind.HIGH_PASS;
        else if (t.contains("high-cut") || t.contains("high cut") || t.contains("highcut") || t.contains("low-pass") || t.contains("low pass") || t.contains("lp") || t.contains("hc")) k = Kind.LOW_PASS;
        else k = Kind.BELL;
        int order = 2;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(t);
        if (m.find()) order = Math.max(1, Math.min(8, Integer.parseInt(m.group(1)))); // "Low-cut 8ᴾ": 8 poles (48 dB/oct)
        double sh = shift == null ? 0 : shift.value();
        double scale = amount == null ? 1 : clamp(amount.value(), 0, 1);
        return new Band(k, order, hzOf(i, sh), gain[i].value() * scale, qOf(i));
    }

    /** The displayed dB range: EQ+'s own setting ("±10" …); EQ-2 / EQ-5 have none, so their gain range (at most ±24). */
    double range() {
        String r = params.text("CONTENTS/DECIBEL_RANGE").replaceAll("[^0-9.]", "");
        if (!r.isEmpty()) { try { return Double.parseDouble(r); } catch (NumberFormatException ignored) {} }
        double g = 0;
        for (Params.P p : gain) if (p != null) g = Math.max(g, Math.max(Math.abs(p.min()), Math.abs(p.max())));
        return g > 0 ? Math.min(24, g) : 10;
    }

    /** A gain shown on the graph back to the band's own gain (EQ-5's Global Amount scales what is drawn). */
    double unscaled(double shown) {
        double scale = amount == null ? 1 : clamp(amount.value(), 0, 1);
        return scale > 0.01 ? shown / scale : shown;
    }

    /** The known ID if the device has it, else the fallback. */
    String firstChoice(String known, String fallback) { return params.choices.containsKey(known) ? known : fallback; }

    /** The ID of a choice/switch parameter whose ID contains every word (case-insensitive), or null. */
    String choiceId(String... words) {
        outer:
        for (String id : params.choices.keySet()) {
            String k = id.toLowerCase(Locale.ROOT);
            for (String w : words) if (!k.contains(w)) continue outer;
            return id;
        }
        return null;
    }

    // ---- response (dB) ----
    static double biquadDb(double[] c, double f) {
        double w = 2 * Math.PI * Math.min(f, FS * 0.49) / FS, cw = Math.cos(w), sw = Math.sin(w), c2 = Math.cos(2 * w), s2 = Math.sin(2 * w);
        double nr = c[0] + c[1] * cw + c[2] * c2, ni = -(c[1] * sw + c[2] * s2), dr = c[3] + c[4] * cw + c[5] * c2, di = -(c[4] * sw + c[5] * s2);
        return 10 * Math.log10(Math.max(1e-30, (nr * nr + ni * ni)) / Math.max(1e-30, (dr * dr + di * di)));
    }

    static double[] rbj(Kind k, double f0, double g, double Q) {
        double A = Math.pow(10, g / 40), w0 = 2 * Math.PI * Math.min(f0, FS * 0.45) / FS, c = Math.cos(w0), s = Math.sin(w0), al = s / (2 * Q);
        switch (k) {
            case BELL: return new double[] {1 + al * A, -2 * c, 1 - al * A, 1 + al / A, -2 * c, 1 - al / A};
            case NOTCH: return new double[] {1, -2 * c, 1, 1 + al, -2 * c, 1 - al};
            case BAND_PASS: return new double[] {al, 0, -al, 1 + al, -2 * c, 1 - al};
            case HIGH_PASS: return new double[] {(1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al};
            case LOW_PASS: return new double[] {(1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al};
            case LOW_SHELF: { double sa = 2 * Math.sqrt(A) * al;
                return new double[] {A * ((A + 1) - (A - 1) * c + sa), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - sa),
                        (A + 1) + (A - 1) * c + sa, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - sa}; }
            case HIGH_SHELF: { double sa = 2 * Math.sqrt(A) * al;
                return new double[] {A * ((A + 1) + (A - 1) * c + sa), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sa),
                        (A + 1) - (A - 1) * c + sa, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sa}; }
            default: return new double[] {1, 0, 0, 1, 0, 0};
        }
    }

    double bandDb(Band b, double f) {
        switch (b.kind) {
            case OFF: return 0;
            case HIGH_PASS: case LOW_PASS: {
                // n poles: Butterworth, n/2 second-order sections (Q_k = 1 / (2 sin((2k+1)π/2n))) plus one first-order if odd;
                // the band's Q (1.0 = Butterworth) scales the sections' resonance
                int n = b.order; double db = 0;
                for (int k = 0; k < n / 2; k++) {
                    double qk = 1 / (2 * Math.sin((2 * k + 1) * Math.PI / (2 * n)));
                    db += biquadDb(rbj(b.kind, b.f, 0, qk * b.q), f);
                }
                if (n % 2 == 1) { double r = b.kind == Kind.HIGH_PASS ? f / b.f : b.f / f; db += 10 * Math.log10(r * r / (1 + r * r)); }
                return db;
            }
            default: return biquadDb(rbj(b.kind, b.f, b.g, b.q), f);
        }
    }

    Band[] bands() { Band[] bs = new Band[BANDS]; for (int i = 0; i < BANDS; i++) bs[i] = band(i); return bs; }

    double total(Band[] bs, double f) {
        double sum = out == null ? 0 : out.value();
        for (Band b : bs) sum += bandDb(b, f);
        return sum;
    }

    static boolean gainMoves(Kind k) { return k == Kind.BELL || k == Kind.LOW_SHELF || k == Kind.HIGH_SHELF; }

    // ---- geometry ----
    double fx(double f, double w) { return w * Math.log(f / FMIN) / Math.log(FMAX / FMIN); }
    double xf(double x, double w) { return FMIN * Math.pow(FMAX / FMIN, x / w); }
    double dy(double d, double h, double r) { return h / 2 - d / r * (h / 2 - 5); }
    double yd(double y, double h, double r) { return (h / 2 - y) / (h / 2 - 5) * r; }
    double nodeY(Band b, double h, double r) { return dy(gainMoves(b.kind) ? clamp(b.g, -r, r) : 0, h, r); }

    static iok rainbow(double t) {
        double[][] s = {{150, 30, 30}, {230, 70, 40}, {240, 200, 60}, {110, 210, 90}, {90, 210, 220}, {120, 150, 240}, {200, 200, 210}};
        t = clamp(t, 0, 1) * (s.length - 1);
        int i = (int) Math.min(s.length - 2, Math.floor(t));
        double u = t - i;
        return iok.cfM((int) (s[i][0] + (s[i + 1][0] - s[i][0]) * u), (int) (s[i][1] + (s[i + 1][1] - s[i][1]) * u), (int) (s[i][2] + (s[i + 1][2] - s[i][2]) * u));
    }

    static final iok GRID_MINOR = iok.cfM(38, 38, 42);
    static final iok LABEL = iok.cfM(95, 95, 102);

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        double r = range();
        g.IwV(1);
        // minor frequency lines (20–90, 200–900, 2k–9k Hz), then the band separators (100 Hz, 1 kHz, 10 kHz)
        col(g, GRID_MINOR);
        for (double dec = 10; dec <= 10000; dec *= 10)
            for (int m = 2; m <= 9; m++) { double f = dec * m; if (f < FMIN || f > FMAX) continue; double x = Math.floor(fx(f, w)) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        // dB lines: quarters of the range
        for (int k = -3; k <= 3; k++) { if (k == 0) continue; double y = Math.floor(dy(r * k / 4, h, r)) + 0.5; g.MRY(0, y); g.cfM(w, y); g.hbP(); }
        col(g, GRID_MAJOR);
        for (double f : new double[] {100, 1000, 10000}) { double x = Math.floor(fx(f, w)) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        double y0 = Math.floor(h / 2) + 0.5; g.MRY(0, y0); g.cfM(w, y0); g.hbP();
        if (freq[0] == null) { g.cfM(4, 12, "EQ+ parameters not found", TEXT); return; }
        Band[] bs = bands();
        g.IwV(1.6);
        int n = (int) Math.max(16, w / 1.5);
        double lim = r * 6; // let the curve run off the view instead of flattening along the edge
        double px = 0, py = dy(clamp(total(bs, FMIN), -lim, lim), h, r);
        for (int i = 1; i <= n; i++) {
            double x = w * i / n, y = dy(clamp(total(bs, xf(x, w)), -lim, lim), h, r);
            if (!(py > h + 2 && y > h + 2) && !(py < -2 && y < -2)) { col(g, rainbow(x / w)); g.MRY(px, py); g.cfM(x, y); g.hbP(); }
            px = x; py = y;
        }
        for (int b = 0; b < BANDS; b++) {
            if (bs[b].kind == Kind.OFF) continue;
            double x = fx(clamp(bs[b].f, FMIN, FMAX), w), y = nodeY(bs[b], h, r);
            col(g, NODE); g.MRY(x, y, 5); g.wGc(); // plain white dot
        }
    }

    // ---- choices: the band's type, read and set through Bitwig's choice parameter (kyb: UNj() index, cfM(int) set) ----
    Object choice(String id) { return id == null ? null : params.choices.get(id); }

    /** A choice's option: its label and the value Bitwig stores for it (not its position in the list). */
    record Opt(String label, int value) {}

    /** The options of a choice parameter: Lig.DNo() is its definition, TMF() the options (aQ: IwV() label, cfM() value). */
    static java.util.List<Opt> options(Object choice) {
        java.util.List<Opt> out = new java.util.ArrayList<>();
        if (choice instanceof bitwig.obf.Lig l) {
            try {
                for (Object o : l.DNo().TMF()) {
                    bitwig.obf.aQ q = (bitwig.obf.aQ) o;
                    out.add(new Opt(q.IwV(), q.cfM()));
                }
            } catch (Throwable t) { MOD.log("can't read options", t); }
        }
        return out;
    }

    /** The stored value of the first option whose label contains every word, or -1. */
    static int valueOf(java.util.List<Opt> opts, String... words) {
        for (Opt o : opts) {
            String l = o.label().toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) if (!l.contains(w)) all = false;
            if (all) return o.value();
        }
        return -1;
    }

    static void setChoice(Object choice, int value) {
        if (choice instanceof bitwig.obf.zKO z && value >= 0) {
            for (Object o : z.ODA().TMF()) if (((bitwig.obf.Dew) o).cfM() == value) { z.cfM((bitwig.obf.Dew) o); break; } // select, as EQ+'s menu does
            if (sets.getAndIncrement() < 60) MOD.log("set choice -> " + value + ", now '" + Params.name(choice) + "'");
            return;
        }
        MOD.log("can't set choice " + (choice == null ? null : choice.getClass().getName()));
    }

    static void setSwitch(Object sw, boolean on) {
        if (sw instanceof bitwig.obf.Qe q) { q.eLY(on); return; } // an on/off parameter's value, set as an undoable change
        MOD.log("can't set switch " + (sw == null ? null : sw.getClass().getName()));
    }

    void turnOff(int b) {
        Object t = choice(typeId[b]);
        int off = valueOf(options(t), "off");
        if (t != null && off >= 0) setChoice(t, off);
        else if (choice(enableId[b]) != null) setSwitch(choice(enableId[b]), false);
    }

    int nearestNode(Band[] bs, double x, double y, double w, double h, double r, double maxPx) {
        int best = -1; double bestD = maxPx * maxPx;
        for (int b = 0; b < BANDS; b++) {
            if (bs[b].kind == Kind.OFF) continue;
            double bx = fx(clamp(bs[b].f, FMIN, FMAX), w) - x, by = nodeY(bs[b], h, r) - y, d = bx * bx + by * by;
            if (d < bestD) { bestD = d; best = b; }
        }
        return best;
    }

    /** Switch on the first free band as a bell at (x, y); returns its index, or -1 when all bands are in use. */
    /**
     * What a click on empty space creates, as EQ+ does: on the curve, a low shelf at the far left, a high shelf at the
     * far right, a bell in between; off the curve, a low-cut at the far left, a high-cut at the far right, a bell in
     * between; at the bottom edge, a notch.
     */
    Kind kindAt(double x, double y, double w, double h) {
        double r = range(), lim = r * 6;
        double curveY = dy(clamp(total(bands(), xf(x, w)), -lim, lim), h, r);
        boolean onCurve = Math.abs(y - curveY) <= 6;
        boolean left = x < w * 0.18, right = x > w * 0.82;
        if (!onCurve && y > h - 6) return Kind.NOTCH;
        if (onCurve) return left ? Kind.LOW_SHELF : right ? Kind.HIGH_SHELF : Kind.BELL;
        return left ? Kind.HIGH_PASS : right ? Kind.LOW_PASS : Kind.BELL;
    }

    /** The option value for a kind of band (EQ+'s labels: "Bell", "Low-shelf", "Low-cut 2ᴾ", "Notch", ...). */
    static int typeValue(java.util.List<Opt> opts, Kind k) {
        int v;
        switch (k) {
            case LOW_SHELF: v = valueOf(opts, "low", "shelf"); break;
            case HIGH_SHELF: v = valueOf(opts, "high", "shelf"); break;
            case HIGH_PASS: v = valueOf(opts, "low-cut", "2"); if (v < 0) v = valueOf(opts, "low-cut"); if (v < 0) v = valueOf(opts, "low", "cut"); break;
            case LOW_PASS: v = valueOf(opts, "high-cut", "2"); if (v < 0) v = valueOf(opts, "high-cut"); if (v < 0) v = valueOf(opts, "high", "cut"); break;
            case NOTCH: v = valueOf(opts, "notch"); break;
            default: v = -1;
        }
        return v >= 0 ? v : valueOf(opts, "bell");
    }

    /** Switch on the first free band at (x, y) with the type the position calls for; returns its index, or -1. */
    int addNode(double x, double y, double w, double h) {
        Band[] bs = bands();
        double r = range(), sh = shift == null ? 0 : shift.value();
        Kind kind = kindAt(x, y, w, h);
        for (int b = 0; b < BANDS; b++) {
            if (freq[b] == null || bs[b].kind != Kind.OFF) continue;
            Object t = choice(typeId[b]);
            if (t != null) {
                int v = typeValue(options(t), kind);
                if (v < 0) continue;
                setChoice(t, v);
            }
            if (choice(enableId[b]) != null) setSwitch(choice(enableId[b]), true);
            Object undo = new Object();
            set(freq[b], freqValue(b, xf(x, w), sh), undo, "Add EQ band");
            if (gain[b] != null) set(gain[b], gainMoves(kind) ? unscaled(yd(y, h, r)) : 0, undo, "Add EQ band");
            addedBand = b; addedAt = System.currentTimeMillis(); addedKind = kind;
            return b;
        }
        return -1;
    }
    private Kind addedKind = Kind.BELL;
    private int lastPressBand = -1;
    private final Object wheelUndo = new Object(); // wheel turns merge into one undo step (within 1.5 s)
    private long lastPressAt;
    private int addedBand = -1;
    private long addedAt;

    /**
     * Right-click on a node: the same menu EQ+'s own node opens (aSs): the parameter's name as the heading, then each
     * visible option with its icon and description, the current one marked; choosing one selects it as EQ+ does.
     */
    @Override boolean menu(bitwig.obf.dKW popup, double x, double y, double w, double h) {
        Band[] bs = bands();
        int b = nearestNode(bs, x, y, w, h, range(), 12);
        if (b < 0) return false;
        Object t = choice(typeId[b]);
        if (!(t instanceof bitwig.obf.zKO z)) { MOD.log("band type isn't a choice: " + (t == null ? null : t.getClass().getName())); return false; }
        popup.MRY(z.fFs());                       // heading: "Band 3 Type"
        Object current = z.vpw();                 // the selected option
        for (Object o : z.ODA().TMF()) {         // the options
            bitwig.obf.Dew opt = (bitwig.obf.Dew) o;
            if (opt.wGc()) continue;              // hidden option
            bitwig.obf.dKa row = bitwig.obf.dKa.cfM(opt.IwV(), (Runnable) () -> z.cfM(opt)); // label; select it
            row.eLY(opt == current ? "radio_menu_item_on.svg" : "device/" + opt.hUR());     // icon
            row.lYc(opt.lYc());                   // description ("12dB/oct slope")
            popup.hUR(row);
        }
        return true;
    }

    /** Change band b's Q by a number of wheel steps (up = narrower). */
    void nudgeQ(int b, double steps, Object undo) {
        if (q[b] == null) return;
        double v = q[b].min() < 0 ? q[b].value() + steps * 0.04 : q[b].value() * Math.pow(1.1, steps); // EQ+: log10(Q)
        set(q[b], v, undo, "Change EQ band Q");
    }

    @Override boolean wantsWheel(double x, double y) { return nearestNode(bands(), x, y, Sxo(), yiX(), range(), 14) >= 0; }

    @Override boolean wheelAt(double x, double y, double delta) {
        int b = nearestNode(bands(), x, y, Sxo(), yiX(), range(), 14);
        if (b < 0) return false;
        nudgeQ(b, clamp(delta * 0.25, -3, 3), wheelUndo); // proportional: trackpads send many small steps
        return true;
    }

    @Override Dragger middle(double x, double y, double w, double h) {
        int b = nearestNode(bands(), x, y, w, h, range(), 14);
        if (b < 0) return null;
        final Object undo = new Object();
        return (ddx, ddy) -> { if (ddy != 0) nudgeQ(b, -ddy * 0.5, undo); }; // drag up: narrower
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        // No changes here: Bitwig also calls this while the mouse only hovers. Changes happen in start()/move().
        Band[] bs = bands();
        double r = range();
        int hit = nearestNode(bs, x, y, w, h, r, 12);
        final double sh = shift == null ? 0 : shift.value();
        final Object undo = new Object();
        final int[] band = {hit};
        final double[] pos = new double[3];
        if (hit >= 0) { pos[0] = fx(clamp(bs[hit].f, FMIN, FMAX), w); pos[1] = nodeY(bs[hit], h, r); }
        else { pos[0] = clamp(x, 0, w); pos[1] = clamp(y, 0, h); }
        final boolean doubleClick = clicks >= 2;
        return new Dragger() {
            boolean removed, added;
            @Override public void start() {
                if (band[0] >= 0) {
                    long now = System.currentTimeMillis();
                    boolean justAdded = band[0] == addedBand && now - addedAt < 600;
                    boolean again = band[0] == lastPressBand && now - lastPressAt < 400; // our own double-click timing
                    lastPressBand = band[0]; lastPressAt = now;
                    if ((doubleClick || again) && !justAdded) { turnOff(band[0]); removed = true; lastPressBand = -1; } // double-click a node: delete
                } else {
                    band[0] = addNode(pos[0], pos[1], w, h); // click on empty space: a new node, then drag it
                    added = band[0] >= 0 && gainMoves(addedKind); // a new shelf/bell drags gain; a cut/notch drags Q
                }
                if (band[0] >= 0 && q[band[0]] != null) pos[2] = q[band[0]].value();
            }
            @Override public boolean wheels() { return band[0] >= 0; } // over a node: the wheel sets its Q
            @Override public void wheel(double delta) {
                int b = band[0];
                if (b < 0 || q[b] == null) return;
                double step = q[b].min() < 0 ? 0.04 : 0.1; // log10(Q) for EQ+
                double v = q[b].min() < 0 ? q[b].value() + Math.signum(delta) * Math.min(3, Math.abs(delta)) * step
                                          : q[b].value() * Math.pow(1.1, Math.signum(delta) * Math.min(3, Math.abs(delta)));
                set(q[b], v, wheelUndo, "Change EQ band Q");
            }
            @Override public void move(double ddx, double ddy) {
                int b = band[0];
                if (b < 0 || removed) return;
                boolean vGain = added || gainMoves(band(b).kind); // a new node is a bell even before Bitwig reports it
                pos[0] = clamp(pos[0] + ddx, 0, w);
                set(freq[b], freqValue(b, xf(pos[0], w), sh), undo, "Change EQ band frequency");
                if (vGain) { pos[1] = clamp(pos[1] + ddy, 0, h); set(gain[b], unscaled(yd(pos[1], h, r)), undo, "Change EQ band gain"); }
                else if (q[b] != null) { pos[2] = q[b].min() < 0 ? pos[2] - ddy * 0.02 : pos[2] * Math.pow(1.03, -ddy); set(q[b], pos[2], undo, "Change EQ band Q"); }
            }
        };
    }
}
