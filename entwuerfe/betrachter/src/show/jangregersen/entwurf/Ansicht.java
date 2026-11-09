package show.jangregersen.entwurf;
import android.app.Activity; import android.graphics.BitmapFactory; import android.os.Bundle; import android.view.KeyEvent; import android.view.MotionEvent; import android.widget.ImageView; import java.util.Arrays;
// Zeigt die Entwurfsbilder (480x800, pixelgenau). Lautstärke oder Tippen links/rechts blättert.
public class Ansicht extends Activity {
  ImageView v; String[] bilder; int i = 0;
  @Override protected void onCreate(Bundle b) { super.onCreate(b);
    try { bilder = getAssets().list(""); } catch (Exception e) { bilder = new String[0]; }
    bilder = Arrays.stream(bilder).filter(n -> n.endsWith(".png")).sorted().toArray(String[]::new);
    v = new ImageView(this); v.setScaleType(ImageView.ScaleType.FIT_XY); v.setBackgroundColor(0xffffffff); setContentView(v);
    if (b != null) i = b.getInt("i", 0); zeige(); }
  @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); o.putInt("i", i); }
  void zeige() { if (bilder.length == 0) return; i = Math.max(0, Math.min(bilder.length - 1, i));
    try (java.io.InputStream in = getAssets().open(bilder[i])) { v.setImageBitmap(BitmapFactory.decodeStream(in)); } catch (Exception e) { } }
  @Override public boolean onKeyDown(int k, KeyEvent e) {
    if (k == KeyEvent.KEYCODE_VOLUME_DOWN) { i++; zeige(); return true; }
    if (k == KeyEvent.KEYCODE_VOLUME_UP) { i--; zeige(); return true; }
    return super.onKeyDown(k, e); }
  @Override public boolean onTouchEvent(MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_UP) { i += e.getX() > v.getWidth() / 2f ? 1 : -1; zeige(); }
    return true; }
}
