package show.jangregersen.tontest;
import android.app.Activity; import android.content.Intent; import android.os.Bundle;
public class Start extends Activity {
  @Override protected void onCreate(Bundle b) { super.onCreate(b);
    Intent i = new Intent(this, Ton.class);
    i.putExtra("sek", getIntent().getIntExtra("sek", 60));
    i.putExtra("rate", getIntent().getIntExtra("rate", 48000));
    startForegroundService(i); finish(); }
}
