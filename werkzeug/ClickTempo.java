import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Liest den rechten Kanal (Click) einer WAV und schätzt das Tempo aus den Click-Abständen. */
public class ClickTempo {
    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        File[] fs = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".wav"));
        Arrays.sort(fs);
        for (File f : fs) {
            try { System.out.println(f.getName() + "\t" + analyse(f)); }
            catch (Exception e) { System.out.println(f.getName() + "\tFEHLER " + e); }
        }
    }

    static String analyse(File f) throws IOException {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[12]; r.readFully(h);
            int ch = 0, rate = 0, bits = 0, fmt = 0; long dataPos = -1, dataLen = 0;
            while (r.getFilePointer() < r.length() - 8) {
                byte[] id = new byte[4]; r.readFully(id);
                long len = Integer.toUnsignedLong(Integer.reverseBytes(r.readInt()));
                String s = new String(id, "ISO-8859-1");
                if (s.equals("fmt ")) {
                    byte[] b = new byte[(int) len]; r.readFully(b);
                    ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
                    fmt = bb.getShort(0) & 0xffff; ch = bb.getShort(2); rate = bb.getInt(4); bits = bb.getShort(14);
                    if (fmt == 0xFFFE && len >= 26) fmt = bb.getShort(24) & 0xffff;
                } else if (s.equals("data")) { dataPos = r.getFilePointer(); dataLen = len; break; }
                else r.seek(r.getFilePointer() + len + (len & 1));
            }
            if (ch < 2) return "nur " + ch + " Kanal";
            int bps = bits / 8, frame = bps * ch;
            dataLen = Math.min(dataLen, r.length() - dataPos);
            long frames = dataLen / frame;
            int hop = rate / 200; // 5 ms
            int n = (int) (frames / hop);
            double[] env = new double[n];
            r.seek(dataPos);
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(r.getFD()), 1 << 20);
            byte[] fb = new byte[frame * hop];
            for (int i = 0; i < n; i++) {
                int got = 0; while (got < fb.length) { int k = in.read(fb, got, fb.length - got); if (k < 0) break; got += k; }
                double m = 0;
                for (int j = 0; j < hop; j++) {
                    int o = j * frame + bps; // rechter Kanal
                    double v;
                    if (fmt == 3 && bits == 32) v = Float.intBitsToFloat((fb[o] & 0xff) | (fb[o+1] & 0xff) << 8 | (fb[o+2] & 0xff) << 16 | fb[o+3] << 24);
                    else if (bits == 16) v = (short) ((fb[o] & 0xff) | fb[o+1] << 8) / 32768.0;
                    else if (bits == 24) v = ((fb[o] & 0xff) | (fb[o+1] & 0xff) << 8 | fb[o+2] << 16) / 8388608.0;
                    else if (bits == 32) v = ((fb[o] & 0xff) | (fb[o+1] & 0xff) << 8 | (fb[o+2] & 0xff) << 16 | fb[o+3] << 24) / 2147483648.0;
                    else v = 0;
                    m = Math.max(m, Math.abs(v));
                }
                env[i] = m;
            }
            // Onsets: Anstieg über Schwelle nach Ruhe, mind. 150 ms Abstand
            double[] sorted = env.clone(); Arrays.sort(sorted);
            double p99 = sorted[(int) (sorted.length * 0.995)];
            if (p99 < 1e-4) return "rechts stumm";
            double thr = p99 * 0.35;
            List<Integer> on = new ArrayList<>();
            for (int i = 1; i < n; i++) if (env[i] >= thr && env[i - 1] < thr && (on.isEmpty() || i - on.get(on.size() - 1) >= 30)) on.add(i);
            if (on.size() < 20) return "zu wenige Clicks (" + on.size() + ")";
            // Abstände in ms, Histogramm in 5-ms-Schritten, häufigster Abstand zwischen 250 und 1500 ms
            Map<Integer, Integer> hist = new TreeMap<>();
            List<Integer> iv = new ArrayList<>();
            for (int i = 1; i < on.size(); i++) { int d = (on.get(i) - on.get(i - 1)) * 5; if (d >= 250 && d <= 1500) { iv.add(d); hist.merge(d / 5, 1, Integer::sum); } }
            int best = 0, bestC = 0;
            for (var e : hist.entrySet()) { int c = e.getValue() + hist.getOrDefault(e.getKey() - 1, 0) + hist.getOrDefault(e.getKey() + 1, 0); if (c > bestC) { bestC = c; best = e.getKey(); } }
            // Feinwert: Mittel der Abstände nahe am häufigsten
            double sum = 0; int cnt = 0;
            for (int d : iv) if (Math.abs(d - best * 5) <= 10) { sum += d; cnt++; }
            double ms = sum / cnt;
            double bpm = 60000.0 / ms;
            return String.format(Locale.ROOT, "%.1f BPM\t(%d Clicks, %d%% passend, %ds)", bpm, on.size(), 100 * cnt / Math.max(1, iv.size()), frames / rate);
        }
    }
}
