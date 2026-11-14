// Startet TrikTrak-Sicherung.ps1 (liegt daneben) ohne sichtbares Fenster und wartet, bis es fertig ist.
// Bauen: C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe /nologo /target:winexe /out:TrikTrak-Sicherung-Start.exe TrikTrak-Sicherung-Start.cs
using System.Diagnostics;
using System.IO;
using System.Reflection;

class Start {
    static void Main() {
        string ordner = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
        var p = new ProcessStartInfo("powershell.exe",
            "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"" + Path.Combine(ordner, "TrikTrak-Sicherung.ps1") + "\"");
        p.UseShellExecute = false;
        p.CreateNoWindow = true;
        p.WorkingDirectory = ordner;
        using (var pr = Process.Start(p)) { pr.WaitForExit(); }
    }
}
