package show.jangregersen.tontest;
import android.app.*; import android.content.Intent; import android.media.*; import android.os.*; import android.util.Log; import java.util.Arrays;
// Stereo-Test: links weicher Akkord ("Band"), rechts Click 120 BPM. Loggt Ausgang, Unterläufe, Latenz, Bildschirm.
public class Ton extends Service {
  static final String T = "TonTest";
  public IBinder onBind(Intent i) { return null; }
  public int onStartCommand(Intent in, int f, int id) {
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("t", "Tontest", NotificationManager.IMPORTANCE_LOW));
    startForeground(1, new Notification.Builder(this, "t").setContentTitle("Tontest").setSmallIcon(android.R.drawable.ic_media_play).build());
    final int sek = in.getIntExtra("sek", 60), rate = in.getIntExtra("rate", 48000);
    new Thread(() -> spiel(sek, rate)).start();
    return START_NOT_STICKY;
  }
  void spiel(int sek, int rate) {
    AudioManager am = getSystemService(AudioManager.class);
    Log.i(T, "nativeRate=" + am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) + " framesPerBuffer=" + am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) + " testRate=" + rate);
    for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
      Log.i(T, "ausgang type=" + d.getType() + " name=" + d.getProductName() + " kanaele=" + Arrays.toString(d.getChannelCounts()) + " raten=" + Arrays.toString(d.getSampleRates()));
    int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
    AudioTrack t = new AudioTrack.Builder()
      .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
      .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
      .setBufferSizeInBytes(min * 4).setTransferMode(AudioTrack.MODE_STREAM).build();
    t.setVolume(0.12f); t.play();
    Log.i(T, "minBuffer=" + min + " bufferFrames=" + t.getBufferSizeInFrames());
    int blk = rate / 10; short[] buf = new short[blk * 2]; long frame = 0, ende = (long) rate * sek, melde = 0;
    double p1 = 0, p2 = 0, p3 = 0; long start = SystemClock.elapsedRealtime(); int beat = rate / 2, clickLen = rate / 100;
    PowerManager pm = getSystemService(PowerManager.class);
    while (frame < ende) {
      for (int i = 0; i < blk; i++) {
        long fr = frame + i;
        double l = (Math.sin(p1) + Math.sin(p2) + Math.sin(p3)) / 3 * 0.5;
        p1 += 2 * Math.PI * 220 / rate; p2 += 2 * Math.PI * 277.18 / rate; p3 += 2 * Math.PI * 329.63 / rate;
        long b = fr % beat; double r = b < clickLen ? Math.sin(2 * Math.PI * 1000.0 * b / rate) * 0.8 * (1 - b / (double) clickLen) : 0;
        buf[2 * i] = (short) (l * 32767); buf[2 * i + 1] = (short) (r * 32767);
      }
      int w = t.write(buf, 0, buf.length);
      if (w < 0) { Log.e(T, "write-Fehler " + w); break; }
      frame += blk;
      if (frame >= melde) {
        melde += rate * 5L;
        AudioDeviceInfo dev = t.getRoutedDevice(); AudioTimestamp ts = new AudioTimestamp();
        long lat = t.getTimestamp(ts) ? (frame - ts.framePosition) * 1000L / rate - (System.nanoTime() - ts.nanoTime) / 1000000L : -1;
        long wand = SystemClock.elapsedRealtime() - start, kopfMs = t.getPlaybackHeadPosition() * 1000L / rate;
        Log.i(T, String.format("t=%ds kopf=%dms wand=%dms unterlaeufe=%d latenz~%dms ausgang=%s bildschirm=%s",
          wand / 1000, kopfMs, wand, t.getUnderrunCount(), lat, dev == null ? "null" : dev.getType() + "/" + dev.getProductName(), pm.isInteractive() ? "an" : "aus"));
      }
    }
    t.stop(); t.release(); Log.i(T, "ENDE"); stopForeground(true); stopSelf();
  }
}
