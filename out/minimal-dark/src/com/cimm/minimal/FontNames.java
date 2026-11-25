package com.cimm.minimal;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Reads a font file's own name (its 'name' table: full name, else family) from .ttf, .otf and .ttc (first face). */
final class FontNames {
    private FontNames() {}

    static String read(File f) {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            int tag = r.readInt();
            if (tag == 0x74746366) { // 'ttcf': a collection, use its first face
                r.readInt();
                if (r.readInt() < 1) return null;
                r.seek(r.readInt() & 0xffffffffL);
                r.readInt();
            }
            int tables = r.readUnsignedShort();
            r.skipBytes(6);
            long nameAt = -1;
            for (int i = 0; i < tables; i++) {
                int t = r.readInt(); r.readInt();
                long off = r.readInt() & 0xffffffffL; r.readInt();
                if (t == 0x6E616D65) nameAt = off; // 'name'
            }
            if (nameAt < 0) return null;
            r.seek(nameAt);
            r.readUnsignedShort();
            int count = r.readUnsignedShort(), strings = r.readUnsignedShort();
            int best = -1, bestPid = 0, bestLen = 0, bestOff = 0;
            for (int i = 0; i < count; i++) {
                int pid = r.readUnsignedShort(), eid = r.readUnsignedShort(), lid = r.readUnsignedShort(),
                    nid = r.readUnsignedShort(), len = r.readUnsignedShort(), off = r.readUnsignedShort();
                if (nid != 4 && nid != 1 || len == 0) continue;
                int score = nid == 4 ? 10 : 0;
                if (pid == 3 && lid == 0x409) score += 5;
                else if (pid == 3 || pid == 0) score += 3;
                else if (pid == 1 && lid == 0 && eid == 0) score += 2;
                else continue;
                if (score > best) { best = score; bestPid = pid; bestLen = len; bestOff = off; }
            }
            if (best < 0) return null;
            byte[] b = new byte[bestLen];
            r.seek(nameAt + strings + bestOff);
            r.readFully(b);
            String s = new String(b, bestPid == 1 ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_16BE).trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }
}
