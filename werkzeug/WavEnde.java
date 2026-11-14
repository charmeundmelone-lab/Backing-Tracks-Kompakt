import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * WavEnde <datei>            : zeigt Pegel links/rechts je 10 s und wo der letzte Ton ist.
 * WavEnde <datei> <ziel> <ms>: schreibt <ziel> = <datei> gekürzt auf <ms> Millisekunden (Format bleibt gleich).
 */
public class WavEnde {
    public static void main(String[] a) throws Exception {
        File f = new File(a[0]);
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            r.seek(12);
            int ch = 0, rate = 0, bits = 0, fmt = 0; long dataPos = -1, dataLen = 0, fmtPos = -1, fmtLen = 0;
            while (r.getFilePointer() < r.length() - 8) {
                byte[] id = new byte[4]; r.readFully(id);
                long len = Integer.toUnsignedLong(Integer.reverseBytes(r.readInt()));
                String s = new String(id, "ISO-8859-1");
                if (s.equals("fmt ")) {
                    fmtPos = r.getFilePointer(); fmtLen = len;
                    byte[] b = new byte[(int) len]; r.readFully(b);
                    ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
                    fmt = bb.getShort(0) & 0xffff; ch = bb.getShort(2); rate = bb.getInt(4); bits = bb.getShort(14);
                    if (fmt == 0xFFFE && len >= 26) fmt = bb.getShort(24) & 0xffff;
                    if (len % 2 == 1) r.skipBytes(1);
                } else if (s.equals("data")) { dataPos = r.getFilePointer(); dataLen = len; break; }
                else r.seek(r.getFilePointer() + len + (len & 1));
            }
            int bps = bits / 8, frame = bps * ch;
            dataLen = Math.min(dataLen, r.length() - dataPos);
            long frames = dataLen / frame;
            System.out.printf(Locale.ROOT, "Format %d, %d Kanäle, %d Hz, %d bit, %.1f s, data bei %d%n", fmt, ch, rate, bits, frames / (double) rate, dataPos);
            if (a.length >= 3) {
                long ms = Long.parseLong(a[2]);
                long neuFrames = Math.min(frames, ms * rate / 1000);
                long neuLen = neuFrames * frame;
                // Kopf (alles vor data) unverändert übernehmen, nur RIFF- und data-Länge anpassen.
                byte[] kopf = new byte[(int) dataPos]; r.seek(0); r.readFully(kopf);
                ByteBuffer kb = ByteBuffer.wrap(kopf).order(ByteOrder.LITTLE_ENDIAN);
                kb.putInt(4, (int) (dataPos - 8 + neuLen));
                kb.putInt((int) dataPos - 4, (int) neuLen);
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(a[1]), 1 << 20)) {
                    out.write(kopf);
                    byte[] buf = new byte[1 << 20]; long rest = neuLen; r.seek(dataPos);
                    while (rest > 0) { int n = r.read(buf, 0, (int) Math.min(buf.length, rest)); if (n < 0) break; out.write(buf, 0, n); rest -= n; }
                }
                System.out.printf(Locale.ROOT, "geschrieben: %s, %.1f s%n", a[1], neuFrames / (double) rate);
                return;
            }
            r.seek(dataPos);
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(r.getFD()), 1 << 20);
            int blk = rate / 10; // 100 ms
            byte[] fb = new byte[frame * blk];
            double[] maxL = new double[(int) (frames / blk) + 1], maxR = new double[maxL.length];
            for (int i = 0; i < maxL.length; i++) {
                int got = 0; while (got < fb.length) { int k = in.read(fb, got, fb.length - got); if (k < 0) break; got += k; }
                int fr = got / frame;
                for (int j = 0; j < fr; j++) {
                    for (int c = 0; c < Math.min(ch, 2); c++) {
                        int o = j * frame + c * bps; double v;
                        if (fmt == 3 && bits == 32) v = Float.intBitsToFloat((fb[o] & 0xff) | (fb[o+1] & 0xff) << 8 | (fb[o+2] & 0xff) << 16 | fb[o+3] << 24);
                        else if (bits == 16) v = (short) ((fb[o] & 0xff) | fb[o+1] << 8) / 32768.0;
                        else if (bits == 24) v = ((fb[o] & 0xff) | (fb[o+1] & 0xff) << 8 | fb[o+2] << 16) / 8388608.0;
                        else v = ((fb[o] & 0xff) | (fb[o+1] & 0xff) << 8 | (fb[o+2] & 0xff) << 16 | fb[o+3] << 24) / 2147483648.0;
                        v = Math.abs(v);
                        if (c == 0) maxL[i] = Math.max(maxL[i], v); else maxR[i] = Math.max(maxR[i], v);
                    }
                }
                if (got < fb.length) break;
            }
            for (int s = 0; s < maxL.length; s += 100) {
                double l = 0, rr = 0; for (int i = s; i < Math.min(s + 100, maxL.length); i++) { l = Math.max(l, maxL[i]); rr = Math.max(rr, maxR[i]); }
                System.out.printf(Locale.ROOT, "%4d s  L %6.1f dB  R %6.1f dB%n", s / 10, db(l), db(rr));
            }
            int letzterL = -1, letzterR = -1;
            for (int i = 0; i < maxL.length; i++) { if (db(maxL[i]) > -60) letzterL = i; if (db(maxR[i]) > -60) letzterR = i; }
            System.out.printf(Locale.ROOT, "letzter Ton (über -60 dB): links %.1f s, rechts %.1f s%n", letzterL / 10.0, letzterR / 10.0);
        }
    }
    static double db(double v) { return v <= 1e-9 ? -180 : 20 * Math.log10(v); }
}
