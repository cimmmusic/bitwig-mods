package com.cimm.groove;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * A groove template: a short pattern of timing and velocity offsets per Base grid step, read from an Ableton .agr.
 *
 * Format-independent (future formats only need their own reader that fills these fields). Pure Java, no Bitwig
 * classes, so it can be tested outside Bitwig.
 *
 * Ableton .agr comes in two encodings:
 * - XML (Live 10/11, sometimes gzipped): Groove/Clip/Value/MidiClip with Loop and MidiNoteEvent Time/Velocity, then
 *   Grid, QuantizationAmount, TimingAmount, RandomAmount, VelocityAmount.
 * - Binary (magic AB 1E 56 78, older Core Library grooves): a schema of field names followed by data. Read here:
 *   the MidiClip header doubles (Time, CurrentStart, CurrentEnd, LoopStart, LoopEnd), the MidiNoteEvent records
 *   (double Time, double Duration, float Velocity, float OffVelocity, bool IsEnabled = 25 bytes, little-endian) and the
 *   Groove fields after the notes (int Grid, float Quantization, Timing, Random, Velocity amounts). The binary schema
 *   is not decoded generically: anything else in those files is ignored.
 */
public final class Groove {
    /** Ableton's Base choices, by the .agr Grid value: 1/4, 1/8, 1/8T, 1/16, 1/16T, 1/32 (in beats). */
    public static final double[] BASES = {1.0, 0.5, 1.0 / 3, 0.25, 1.0 / 6, 0.125};
    public static final String[] BASE_NAMES = {"1/4", "1/8", "1/8T", "1/16", "1/16T", "1/32"};

    public String name;
    /** Pattern length in beats (the groove clip's loop). */
    public double length;
    /** Index into BASES, from the file. */
    public int base;
    /** Ableton's amounts from the file: 0..1 (velocity -1..1). */
    public double quantize, timing, random, velocity;
    /** Note starts (beats from the pattern start) and velocities (0..127), sorted by time. */
    public double[] times, velocities;

    // ---- the table the playback uses, per Base step ----
    private int tableBase = -1;
    private double[] stepOffset;   // beats, per step
    private double[] stepVelocity; // 0..127 or NaN (no groove note on that step)
    private double minVel, maxVel;

    /** Builds (or reuses) the per-step table for a Base: each step takes the groove note nearest to it (within half
     *  a step); steps without one get no timing offset and no velocity change. */
    /** A drawn groove (Manual) gives its steps directly, at its own Base: offsets in beats and velocities 0..127.
     *  No nearest-note matching, so a step pushed a full half step still belongs to its own step. */
    double[] fixedOffsets, fixedVelocities;

    synchronized void table(int baseIndex) {
        if (baseIndex == tableBase && stepOffset != null) return;
        if (fixedOffsets != null && baseIndex == base) {
            stepOffset = fixedOffsets.clone();
            stepVelocity = fixedVelocities.clone();
            minVel = 127; maxVel = 0;
            for (double v : stepVelocity) { minVel = Math.min(minVel, v); maxVel = Math.max(maxVel, v); }
            tableBase = baseIndex;
            return;
        }
        double b = BASES[baseIndex];
        int steps = Math.max(1, (int) Math.round(length / b));
        stepOffset = new double[steps];
        stepVelocity = new double[steps];
        java.util.Arrays.fill(stepVelocity, Double.NaN);
        double[] best = new double[steps];
        java.util.Arrays.fill(best, Double.MAX_VALUE);
        minVel = 127; maxVel = 0;
        for (int i = 0; i < times.length; i++) {
            double pos = times[i] / b;
            int s = (int) Math.round(pos);
            double d = times[i] - s * b;
            if (Math.abs(d) > b / 2) continue;
            int k = Math.floorMod(s, steps);
            if (Math.abs(d) < best[k]) { best[k] = Math.abs(d); stepOffset[k] = d; stepVelocity[k] = velocities[i]; }
        }
        for (double v : stepVelocity) if (!Double.isNaN(v)) { minVel = Math.min(minVel, v); maxVel = Math.max(maxVel, v); }
        tableBase = baseIndex;
    }

    /** {offsets as a fraction of a step, velocities} for the first `count` steps at a Base (the Manual graph's start). */
    synchronized double[][] steps(int baseIndex, int count) {
        table(baseIndex);
        double[] off = new double[count], vel = new double[count];
        for (int k = 0; k < count; k++) {
            int i = k % stepOffset.length;
            off[k] = Math.max(-0.5, Math.min(0.5, stepOffset[i] / BASES[baseIndex]));
            vel[k] = Double.isNaN(stepVelocity[i]) ? 100 : stepVelocity[i];
        }
        return new double[][]{off, vel};
    }

    /** Settings the user turns (Strength scales everything; the others start from the file's values). */
    public static final class Amounts {
        public double strength = 1, timing, velocity, random, quantize;
        public int base;
        public static Amounts from(Groove g) {
            Amounts a = new Amounts();
            a.timing = g.timing; a.velocity = g.velocity; a.random = g.random; a.quantize = g.quantize; a.base = g.base;
            return a;
        }
    }

    /** Where a note starting at t (beats from the clip start) plays. seed makes Random stable per note. */
    public double time(double t, Amounts a, long seed) {
        table(a.base);
        double b = BASES[a.base];
        long s = Math.round(t / b);
        double grid = s * b;
        double q = a.quantize * a.strength;
        double out = t + (grid - t) * q;
        int k = (int) Math.floorMod(s, (long) stepOffset.length);
        out += stepOffset[k] * a.timing * a.strength;
        if (a.random > 0) out += rnd(seed) * a.random * a.strength * b * 0.5;
        return out;
    }

    /** A smooth version of the timing, for audio (stretched between steps, never jumping): each Base step moves by
     *  its offset, and times between steps move by the blend of the two. Quantize and Random don't apply. */
    public double warp(double t, Amounts a) {
        table(a.base);
        double b = BASES[a.base];
        double x = t / b;
        long k = (long) Math.floor(x);
        double f = x - k;
        int n = stepOffset.length;
        double o1 = stepOffset[(int) Math.floorMod(k, (long) n)], o2 = stepOffset[(int) Math.floorMod(k + 1, (long) n)];
        return t + (o1 + (o2 - o1) * f) * a.timing * a.strength;
    }

    /** Velocity (0..1) for a note starting at t with velocity v. Negative amounts invert the accents. */
    public double velocity(double t, double v, Amounts a) {
        table(a.base);
        double amt = a.velocity * a.strength;
        if (amt == 0 || maxVel <= 0) return v;
        double b = BASES[a.base];
        int k = (int) Math.floorMod(Math.round(t / b), (long) stepVelocity.length);
        double g = stepVelocity[k];
        if (Double.isNaN(g)) return v;
        double f = amt > 0 ? g / maxVel : (maxVel + minVel - g) / maxVel;
        double out = v * (1 + Math.abs(amt) * (f - 1));
        return Math.max(0, Math.min(1, out));
    }

    /** -1..1, the same for the same seed. */
    private static double rnd(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return ((z >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    // ------------------------------------------------------------------------------------------------ reading

    public static Groove read(Path file) throws IOException {
        byte[] b = Files.readAllBytes(file);
        if (b.length > 2 && (b[0] & 0xff) == 0x1f && (b[1] & 0xff) == 0x8b) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(b))) { b = in.readAllBytes(); }
        }
        String fallback = file.getFileName().toString().replaceFirst("\\.agr$", "");
        Groove g;
        if (b.length > 4 && (b[0] & 0xff) == 0xab && (b[1] & 0xff) == 0x1e && (b[2] & 0xff) == 0x56 && (b[3] & 0xff) == 0x78) g = binary(b);
        else if (b.length > 14 && b[0] == 'M' && b[1] == 'T' && b[2] == 'h' && b[3] == 'd') g = midi(b);
        else if (new String(b, 0, Math.min(b.length, 200), StandardCharsets.UTF_8).contains("<Ableton")) g = xml(new String(b, StandardCharsets.UTF_8));
        else throw new IOException("not an Ableton groove or MIDI file");
        if (g.name == null || g.name.isBlank()) g.name = fallback;
        g.name = fallback; // the file name is what people chose and browse by
        g.check();
        return g;
    }

    private void check() throws IOException {
        if (times == null || times.length == 0) throw new IOException("the groove has no notes");
        if (!(length > 0) || length > 1024) throw new IOException("unusable groove length " + length);
        if (base < 0 || base >= BASES.length) throw new IOException("unknown Base value " + base);
        for (double t : times) if (!Double.isFinite(t)) throw new IOException("bad note time");
    }

    private static final Pattern NOTE = Pattern.compile("<MidiNoteEvent\\b([^>]*)>");

    private static Groove xml(String s) throws IOException {
        Groove g = new Groove();
        g.name = attr(tag(s, "Name"), "Value");
        String loop = section(s, "Loop");
        double ls = num(attr(tag(loop, "LoopStart"), "Value"), 0), le = num(attr(tag(loop, "LoopEnd"), "Value"), Double.NaN);
        g.length = le - ls;
        String tail = s.substring(Math.max(0, s.lastIndexOf("</Clip>")));
        g.base = (int) num(attr(tag(tail, "Grid"), "Value"), 3);
        g.quantize = num(attr(tag(tail, "QuantizationAmount"), "Value"), 0) / 100;
        g.timing = num(attr(tag(tail, "TimingAmount"), "Value"), 100) / 100;
        g.random = num(attr(tag(tail, "RandomAmount"), "Value"), 0) / 100;
        g.velocity = num(attr(tag(tail, "VelocityAmount"), "Value"), 0) / 100;
        List<double[]> notes = new ArrayList<>();
        Matcher m = NOTE.matcher(s);
        while (m.find()) {
            String a = m.group(1);
            if ("false".equals(attr(a, "IsEnabled"))) continue;
            double t = num(attr(a, "Time"), Double.NaN), d = num(attr(a, "Duration"), 1), v = num(attr(a, "Velocity"), 100);
            notes.add(new double[]{t, d, v});
        }
        g.setNotes(notes, ls);
        return g;
    }

    private static Groove binary(byte[] b) throws IOException {
        Groove g = new Groove();
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        int clip = lastIndexOf(b, "\u0008MidiClip".getBytes(StandardCharsets.ISO_8859_1));
        if (clip < 0) throw new IOException("no clip in the groove");
        int h = clip + 9 + 4; // class name, then 4 bytes, then Time, CurrentStart, CurrentEnd, LoopStart, LoopEnd
        if (h + 40 > b.length) throw new IOException("groove clip header cut short");
        double ls = bb.getDouble(h + 24), le = bb.getDouble(h + 32);
        g.length = le - ls;
        int ev = lastIndexOf(b, "\rMidiNoteEvent".getBytes(StandardCharsets.ISO_8859_1));
        if (ev < 0) throw new IOException("no notes in the groove");
        int j = ev + 14;
        List<double[]> notes = new ArrayList<>();
        while (j + 25 <= b.length) {
            double t = bb.getDouble(j), d = bb.getDouble(j + 8);
            float v = bb.getFloat(j + 16);
            int en = b[j + 24];
            if (!(d > 0 && d < 1000 && v >= 0 && v <= 127.5 && (en == 0 || en == 1) && t > -1 && t < 10000)) break;
            if (en == 1) notes.add(new double[]{t, d, v});
            j += 25;
        }
        // After the notes: a few header fields ending in FF bytes, three zero bytes, then int Grid and four float
        // amounts (Quantization, Timing, Random, Velocity). The first spot after the notes that reads like that.
        boolean found = false;
        for (int k = j + 4; k + 20 <= b.length && !found; k++) {
            if ((b[k - 4] & 0xff) != 0xff || b[k - 3] != 0 || b[k - 2] != 0 || b[k - 1] != 0) continue;
            int grid = bb.getInt(k);
            float q = bb.getFloat(k + 4), t = bb.getFloat(k + 8), r = bb.getFloat(k + 12), v = bb.getFloat(k + 16);
            if (grid >= 0 && grid < BASES.length && amount(q) && amount(t) && amount(r) && amount(v)) {
                g.base = grid; g.quantize = q / 100; g.timing = t / 100; g.random = r / 100; g.velocity = v / 100;
                found = true;
            }
        }
        if (!found) throw new IOException("couldn't find the groove's Base/Timing settings");
        g.setNotes(notes, ls);
        return g;
    }

    /** Longest MIDI groove, in beats (longer files use their start). */
    static final double MIDI_MAX = 64;

    /**
     * A Standard MIDI File as a groove: every note-on (all tracks, all channels) at its time in beats (from the file's
     * ticks per quarter note) with its velocity; notes struck together count once. Length: whole 4/4 bars up to
     * MIDI_MAX. Amounts as Ableton gives an extracted groove: Base 1/16, Timing 100%, the rest 0.
     */
    private static Groove midi(byte[] b) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN);
        int hlen = bb.getInt(4);
        int ntrks = bb.getShort(10) & 0xffff;
        int division = bb.getShort(12) & 0xffff;
        if ((division & 0x8000) != 0) throw new IOException("SMPTE-timed MIDI files aren't supported");
        if (division == 0) throw new IOException("the MIDI file has no time resolution");
        List<double[]> notes = new ArrayList<>();
        int pos = 8 + hlen;
        for (int t = 0; t < ntrks && pos + 8 <= b.length; t++) {
            if (!(b[pos] == 'M' && b[pos + 1] == 'T' && b[pos + 2] == 'r' && b[pos + 3] == 'k')) { // skip unknown chunks
                pos += 8 + bb.getInt(pos + 4); t--; continue;
            }
            int len = bb.getInt(pos + 4), p = pos + 8, end = Math.min(b.length, p + len);
            long tick = 0;
            int status = 0;
            while (p < end) {
                long delta = 0; int c;
                do { c = b[p++] & 0xff; delta = (delta << 7) | (c & 0x7f); } while ((c & 0x80) != 0 && p < end);
                tick += delta;
                if (p >= end) break;
                int s = b[p] & 0xff;
                if (s == 0xff) { // meta event
                    int vl = 0; p += 2;
                    do { c = b[p++] & 0xff; vl = (vl << 7) | (c & 0x7f); } while ((c & 0x80) != 0 && p < end);
                    p += vl; continue;
                }
                if (s == 0xf0 || s == 0xf7) { // sysex
                    int vl = 0; p++;
                    do { c = b[p++] & 0xff; vl = (vl << 7) | (c & 0x7f); } while ((c & 0x80) != 0 && p < end);
                    p += vl; continue;
                }
                if ((s & 0x80) != 0) { status = s; p++; } // else running status
                int type = status & 0xf0;
                int d1 = p < end ? b[p] & 0xff : 0, d2 = p + 1 < end ? b[p + 1] & 0xff : 0;
                p += (type == 0xc0 || type == 0xd0) ? 1 : 2;
                if (type == 0x90 && d2 > 0) notes.add(new double[]{tick / (double) division, 0.1, d2});
            }
            pos = 8 + pos + len;
        }
        if (notes.isEmpty()) throw new IOException("the MIDI file has no notes");
        double last = 0;
        for (double[] n : notes) last = Math.max(last, n[0]);
        Groove g = new Groove();
        g.length = Math.min(MIDI_MAX, Math.max(4, Math.ceil((last + 1e-6) / 4) * 4));
        g.base = 3; g.timing = 1; g.velocity = 0; g.random = 0; g.quantize = 0;
        g.setNotes(notes, 0);
        return g;
    }

    private static boolean amount(float x) { return x >= -100.001f && x <= 100.001f && (x == 0 || Math.abs(x) >= 0.5f); }

    /** Keeps notes inside the loop; drops near-duplicates (some grooves start with a tiny marker note). */
    private void setNotes(List<double[]> notes, double loopStart) {
        notes.removeIf(n -> !Double.isFinite(n[0]));
        notes.sort((x, y) -> Double.compare(x[0], y[0]));
        List<double[]> keep = new ArrayList<>();
        for (double[] n : notes) {
            double t = n[0] - loopStart;
            if (t < -0.01 || t >= length - 1e-6) continue;
            if (!keep.isEmpty() && Math.abs(t - keep.get(keep.size() - 1)[0]) < 0.004) {
                double[] prev = keep.get(keep.size() - 1);
                if (n[1] > prev[1]) { prev[0] = t; prev[2] = n[2]; prev[1] = n[1]; } // keep the longer (real) note
                continue;
            }
            keep.add(new double[]{t, n[1], n[2]});
        }
        times = new double[keep.size()];
        velocities = new double[keep.size()];
        for (int i = 0; i < keep.size(); i++) { times[i] = keep.get(i)[0]; velocities[i] = keep.get(i)[2]; }
    }

    private static int lastIndexOf(byte[] b, byte[] p) {
        outer:
        for (int i = b.length - p.length; i >= 0; i--) {
            for (int k = 0; k < p.length; k++) if (b[i + k] != p[k]) continue outer;
            return i;
        }
        return -1;
    }

    private static String section(String s, String tag) {
        int a = s.indexOf("<" + tag + ">");
        int e = a < 0 ? -1 : s.indexOf("</" + tag + ">", a);
        return a < 0 || e < 0 ? "" : s.substring(a, e);
    }

    private static String tag(String s, String tag) {
        Matcher m = Pattern.compile("<" + tag + "\\b([^>]*)/?>").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private static String attr(String attrs, String name) {
        Matcher m = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    private static double num(String s, double dflt) {
        if (s == null) return dflt;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return dflt; }
    }

    // ------------------------------------------------------------------------------------------------ writing

    /** This groove as an Ableton .agr (the XML form Live 11 writes), with the given amounts. */
    String toAgr(Amounts a) {
        StringBuilder notes = new StringBuilder();
        double dur = BASES[base] / 2;
        for (int i = 0; i < times.length; i++) {
            notes.append("\t\t\t\t\t\t\t\t\t<MidiNoteEvent Time=\"").append(num(times[i])).append("\" Duration=\"").append(num(dur))
                    .append("\" Velocity=\"").append(num(velocities[i]))
                    .append("\" VelocityDeviation=\"0\" OffVelocity=\"64\" Probability=\"1\" IsEnabled=\"true\" NoteId=\"")
                    .append(i + 1).append("\" />\n");
        }
        String n = escape(name), len = num(length);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Ableton MajorVersion=\"5\" MinorVersion=\"11.0_432\" Creator=\"Bitwig Global Groove\" Revision=\"\">\n"
                + "\t<Groove>\n\t\t<LomId Value=\"0\" />\n\t\t<Name Value=\"" + n + "\" />\n"
                + "\t\t<Clip>\n\t\t\t<Value>\n\t\t\t\t<MidiClip Id=\"0\" Time=\"0\">\n"
                + "\t\t\t\t\t<LomId Value=\"0\" />\n\t\t\t\t\t<LomIdView Value=\"0\" />\n"
                + "\t\t\t\t\t<CurrentStart Value=\"0\" />\n\t\t\t\t\t<CurrentEnd Value=\"" + len + "\" />\n"
                + "\t\t\t\t\t<Loop>\n\t\t\t\t\t\t<LoopStart Value=\"0\" />\n\t\t\t\t\t\t<LoopEnd Value=\"" + len + "\" />\n"
                + "\t\t\t\t\t\t<StartRelative Value=\"0\" />\n\t\t\t\t\t\t<LoopOn Value=\"true\" />\n"
                + "\t\t\t\t\t\t<OutMarker Value=\"" + len + "\" />\n\t\t\t\t\t\t<HiddenLoopStart Value=\"0\" />\n"
                + "\t\t\t\t\t\t<HiddenLoopEnd Value=\"" + len + "\" />\n\t\t\t\t\t</Loop>\n"
                + "\t\t\t\t\t<Name Value=\"" + n + "\" />\n\t\t\t\t\t<Annotation Value=\"\" />\n\t\t\t\t\t<Color Value=\"24\" />\n"
                + "\t\t\t\t\t<TimeSignature>\n\t\t\t\t\t\t<TimeSignatures>\n\t\t\t\t\t\t\t<RemoteableTimeSignature Id=\"0\">\n"
                + "\t\t\t\t\t\t\t\t<Numerator Value=\"4\" />\n\t\t\t\t\t\t\t\t<Denominator Value=\"4\" />\n\t\t\t\t\t\t\t\t<Time Value=\"0\" />\n"
                + "\t\t\t\t\t\t\t</RemoteableTimeSignature>\n\t\t\t\t\t\t</TimeSignatures>\n\t\t\t\t\t</TimeSignature>\n"
                + "\t\t\t\t\t<Notes>\n\t\t\t\t\t\t<KeyTracks>\n\t\t\t\t\t\t\t<KeyTrack Id=\"1\">\n\t\t\t\t\t\t\t\t<Notes>\n"
                + notes
                + "\t\t\t\t\t\t\t\t</Notes>\n\t\t\t\t\t\t\t\t<MidiKey Value=\"36\" />\n\t\t\t\t\t\t\t</KeyTrack>\n"
                + "\t\t\t\t\t\t</KeyTracks>\n\t\t\t\t\t</Notes>\n\t\t\t\t</MidiClip>\n\t\t\t</Value>\n\t\t</Clip>\n"
                + "\t\t<Grid Value=\"" + a.base + "\" />\n"
                + "\t\t<QuantizationAmount Value=\"" + Math.round(a.quantize * 100) + "\" />\n"
                + "\t\t<TimingAmount Value=\"" + Math.round(a.timing * 100) + "\" />\n"
                + "\t\t<RandomAmount Value=\"" + Math.round(a.random * 100) + "\" />\n"
                + "\t\t<VelocityAmount Value=\"" + Math.round(a.velocity * 100) + "\" />\n"
                + "\t\t<Annotation Value=\"\" />\n\t</Groove>\n</Ableton>\n";
    }

    private static String num(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override public String toString() {
        return name + ": " + times.length + " notes, " + length + " beats, base " + BASE_NAMES[base]
                + ", quantize " + Math.round(quantize * 100) + "%, timing " + Math.round(timing * 100)
                + "%, random " + Math.round(random * 100) + "%, velocity " + Math.round(velocity * 100) + "%";
    }
}
