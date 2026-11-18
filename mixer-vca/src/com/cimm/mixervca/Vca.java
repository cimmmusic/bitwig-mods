package com.cimm.mixervca;

import bitwig.obf.fP;  // track types
import bitwig.obf.iok; // a colour
import bitwig.obf.xJo; // a track
import bml.api.Bml;
import com.bitwig.flt.document.core.iface.WSA; // a track, as its group lists it
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.flt.document.core.master.ruS; // a group track
import com.bitwig.flt.document.core.master.zwV; // a channel (the insert method for main tracks)
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A VCA is an ordinary (empty) audio track, named "VCA n" and coloured a lighter grey. Its comment lists its members'
 * track UUIDs — "[mixer-vca members=uuid,uuid]" — so the link is saved with the project, survives renaming and moving,
 * and tells someone without the mod what the track is. The Linker moves the members' faders with the VCA's.
 */
final class Vca {
    static final Bml.Mod MOD = Bml.mod("mixer-vca");
    static final iok COLOUR = iok.cfM(150, 150, 156); // a lighter grey, not much
    static final Pattern MARK = Pattern.compile("\\[mixer-vca members=([0-9a-fA-F,-]*)\\]");

    private Vca() {}

    /** Members named in a track's comment; null if the track isn't a VCA. */
    static List<UUID> members(xJo track) {
        String c;
        try { c = track.AgD(); } catch (Throwable t) { return null; } // the track's comment
        if (c == null) return null;
        Matcher m = MARK.matcher(c);
        if (!m.find()) return null;
        List<UUID> out = new ArrayList<>();
        for (String s : m.group(1).split(",")) {
            try { if (!s.isBlank()) out.add(UUID.fromString(s.trim())); } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    static String comment(List<xJo> members) {
        StringBuilder names = new StringBuilder(), ids = new StringBuilder();
        for (xJo t : members) {
            if (names.length() > 0) { names.append(", "); ids.append(','); }
            names.append(t.ca_());   // displayed name (macOS ca_; Windows cb_)
            ids.append(t.kFU());     // UUID
        }
        return "VCA: " + names + " [mixer-vca members=" + ids + "]"; // short: the members' names, then the link (their UUIDs)
    }

    /** Bitwig's default mixer strip width (the track's view settings: JVl "mixer_width", 85). A VCA gets half. */
    static final int DEFAULT_WIDTH = 85;

    /** The track's mixer strip width: its view settings object (cXR.oQj(), an RBc) — LEd() reads, Hnt(int) sets
     *  (inside an open undo step, as Bitwig's own set_mixer_channel_widths does). */
    static void setHalfWidth(xJo track) {
        try {
            Object view = Reflect.call(track, "oQj");
            if (view instanceof bitwig.obf.yel v) v.Hnt(DEFAULT_WIDTH / 2);
            else MOD.log("VCA: no mixer width settings on the new track");
        } catch (Throwable t) { MOD.log("VCA: couldn't set the width", t); }
    }

    static void create(List<xJo> members) {
        if (members.isEmpty()) return;
        xJo first = members.get(0);
        g2L project = first.cb_();       // its project (macOS cb_; Windows cc_)
        ruS parent = first.zjb();        // the group holding the first selected track
        if (project == null || parent == null) { MOD.log("Group as VCA: no project or group"); return; }
        int count = 0;
        for (xJo t : Linker.allTracks(project)) if (members(t) != null) count++;
        int index = 0;                   // after the last selected track in that group
        for (xJo t : members) {
            try { if (t.zjb() == parent) index = Math.max(index, parent.cfM((WSA) t) + 1); } catch (Throwable ignored) {}
        }
        project.cfM("Group as VCA", false); // open one undo step
        try {
            xJo vca = parent.MRY(fP.MRY);    // a new audio track (empty: it carries no audio)
            vca.cfM(COLOUR);
            vca.d_("VCA " + (count + 1));
            vca.TtY(comment(members));
            parent.cfM((zwV) vca, index);    // zwV: the insert method for main tracks
            setHalfWidth(vca);
            project.cfM(false);              // close the undo step
            Linker.know(project);
            MOD.log("VCA " + (count + 1) + " created for " + members.size() + " tracks");
        } catch (Throwable t) {
            try { project.cfM(false); } catch (Throwable ignored) {}
            MOD.log("Group as VCA failed", t);
        }
    }
}
