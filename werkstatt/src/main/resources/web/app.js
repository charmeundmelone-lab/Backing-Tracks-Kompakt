// TrikTrak Werkstatt: Oberfläche. Alle Regeln liegen im Server (derselbe Code wie auf dem Kompakt),
// die Seite zeigt nur an und schickt, was Jan tut.
"use strict";

const INSTR = -1;
const Z = { liste: null, filter: "alle", suche: "", song: null, ansicht: "teile", gewaehlt: null };
const $ = (s) => document.querySelector(s);

function el(tag, attrs = {}, ...kinder) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === "class") e.className = v;
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else if (v === true) e.setAttribute(k, "");
    else if (v !== false && v != null) e.setAttribute(k, v);
  }
  for (const k of kinder.flat()) if (k != null && k !== false) e.append(k.nodeType ? k : document.createTextNode(k));
  return e;
}

const zeit = (ms) => { const s = Math.floor(ms / 1000); return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0"); };

function melden(text) {
  const m = $("#meldung");
  m.textContent = text ? text + "  (Klick schließt)" : "";
  m.hidden = !text;
}
$("#meldung").addEventListener("click", () => melden(""));

async function ruf(was, daten) {
  const antwort = await fetch("/api/" + was, daten === undefined ? {} : {
    method: "POST", headers: { "Content-Type": "application/json", "X-TrikTrak": "1" }, body: JSON.stringify(daten),
  });
  const j = await antwort.json();
  if (j.fehler) { melden(j.fehler); return null; }
  return j;
}

/** Änderung am Song. Kostet sie eingemessene Zeiten, fragt die Seite erst nach. */
async function aendern(was, daten) {
  let j = await ruf(was, { id: Z.song.id, ...daten });
  if (j && j.frage) {
    const n = j.frage;
    if (!confirm(`Das löscht die getippten Zeiten von ${n} ${n === 1 ? "Teil" : "Teilen"}.\nDort musst du am Mudita neu einmessen.\n\nTrotzdem ändern?`)) return;
    j = await ruf(was, { id: Z.song.id, ...daten, sicher: true });
  }
  if (j) { Z.song = j; melden(""); zeigeSong(); ladeListe(false); }
}

// ── Songliste ────────────────────────────────────────────────────────────────

async function ladeListe(mitStand = true) {
  const j = await ruf("liste");
  if (!j) return;
  Z.liste = j;
  zeigeStand();
  zeigeListe();
}

function zeigeStand() {
  const l = Z.liste;
  const wann = l.geholtAm ? "Stand vom Mudita: " + new Date(l.geholtAm).toLocaleString("de-DE", { dateStyle: "short", timeStyle: "short" }) : "Noch keine Daten vom Mudita geholt.";
  const offen = l.songs.filter((s) => s.geaendert).length;
  $("#stand").textContent = wann + (offen ? `  ·  ${offen} ${offen === 1 ? "Song" : "Songs"} hier geändert, noch nicht auf dem Mudita` : "") + (l.mudita ? "  ·  Mudita ist angeschlossen" : "  ·  Mudita nicht angeschlossen");
  const b = $("#senden");
  b.disabled = !offen;
  b.textContent = offen ? `Zum Mudita senden (${offen})` : "Zum Mudita senden";
  b.title = offen ? "Schickt Text und Teile der geänderten Songs an TrikTrak. Deine Zeiten vom Einmessen bleiben, wo der Text gleich bleibt." : "Nichts zu senden: Am Laptop ist nichts geändert.";
}

function zeigeListe() {
  const filter = [["alle", "Alle"], ["mit", "Mit Text"], ["ohne", "Ohne Text"], ["neu", "Geändert"]];
  $("#filter").replaceChildren(...filter.map(([k, name]) => el("span", { class: "chip" + (Z.filter === k ? " an" : ""), onclick: () => { Z.filter = k; zeigeListe(); } }, name)));
  const q = Z.suche.trim().toLowerCase();
  const songs = Z.liste.songs.filter((s) =>
    (!q || s.titel.toLowerCase().includes(q) || s.interpret.toLowerCase().includes(q)) &&
    (Z.filter === "alle" || (Z.filter === "mit" && s.hatText) || (Z.filter === "ohne" && !s.hatText) || (Z.filter === "neu" && s.geaendert)));
  $("#songs").replaceChildren(...songs.map((s) => el("li", { class: Z.song && Z.song.id === s.id ? "an" : "", onclick: () => waehle(s.id) },
    el("div", { class: "titel" }, s.titel),
    el("div", { class: "klein" },
      !s.hatText ? "ohne Text" : `${s.teile} Teile · ` + (s.eingemessen === 0 ? "nicht eingemessen" : s.vorlaeufig ? "Zeiten aus Vorlage" : `${s.eingemessen} eingemessen`),
      s.geaendert ? el("b", {}, " · geändert") : null))));
  if (!songs.length) $("#songs").append(el("li", { class: "klein" }, Z.liste.songs.length ? "Kein Song passt." : "Noch keine Songs. Oben „Vom Mudita holen“."));
}

$("#suche").addEventListener("input", (e) => { Z.suche = e.target.value; zeigeListe(); });
$("#holen").addEventListener("click", async () => {
  $("#holen").disabled = true; $("#holen").textContent = "Hole …";
  const j = await ruf("holen", {});
  $("#holen").disabled = false; $("#holen").textContent = "Vom Mudita holen";
  if (!j) return;
  Z.liste = j; melden(""); zeigeStand(); zeigeListe();
  if (Z.song) waehle(Z.song.id);
});
$("#senden").addEventListener("click", async () => {
  const b = $("#senden");
  b.disabled = true; b.textContent = "Sende …";
  const j = await ruf("senden", {});
  if (!j) { zeigeStand(); return; }
  Z.liste = j; zeigeStand(); zeigeListe();
  melden(j.gesendet);
  if (Z.song) waehle(Z.song.id);
});

async function waehle(id) {
  const j = await ruf("song?id=" + id);
  if (!j) return;
  Z.song = j; Z.gewaehlt = null;
  if (!j.abschnitte.length) Z.ansicht = "text";
  else if (Z.ansicht === "text") Z.ansicht = "teile";
  zeigeListe(); zeigeSong();
}

// ── Ein Song ─────────────────────────────────────────────────────────────────

function nameVon(a) { return a === INSTR ? "Instrumental" : Z.song.abschnitte[a].name; }
function pille(a) { return el("span", { class: "pille " + (a === INSTR ? "ohne" : Z.song.abschnitte[a].art) }, nameVon(a)); }
function folge() { return Z.song.ablauf.map((t) => t.abschnitt); }
function s1TeilBei(ms) { const s1 = Z.song.s1; return s1 && ms >= 0 ? s1.teile.find((t) => ms >= t.startMs && ms < t.startMs + Math.max(t.dauerMs, 1)) : null; }

function zeigeSong() {
  const s = Z.song;
  const w = $("#werkbank");
  const reiter = (k, name) => el("span", { class: "chip" + (Z.ansicht === k ? " an" : ""), onclick: () => { Z.ansicht = k; zeigeSong(); } }, name);
  w.replaceChildren(
    el("div", { class: "kopf" },
      el("div", { class: "weit" }, el("div", { class: "etikett" }, s.interpret || "Song"), el("h2", {}, s.titel)),
      el("button", { class: "knopf", disabled: !s.kannZurueck, onclick: () => aendern("zurueck", {}) }, "↶ Rückgängig")),
    el("div", { class: "unterzeile" }, `Länge ${zeit(s.dauerMs)}` + (s.geaendert ? " · hier geändert, noch nicht zum Mudita gesendet" : "")),
    el("div", { class: "reihe", style: "margin-bottom:16px" }, reiter("teile", "Teile & Ablauf"), reiter("text", "Text bearbeiten"), reiter("netz", "Text aus dem Internet")),
    Z.ansicht === "text" ? textAnsicht() : Z.ansicht === "netz" ? netzAnsicht() : teileAnsicht());
}

function netzAnsicht() {
  const s = Z.song;
  const feld = el("input", { type: "search", placeholder: "Interpret (hilft beim Finden)", style: "font:inherit;padding:8px 10px;border:2.5px solid #000;border-radius:10px;width:320px" });
  feld.value = s.interpret;
  const liste = el("div", {}, el("p", { class: "leer" }, "Suche …"));
  const suchen = async () => {
    liste.replaceChildren(el("p", { class: "leer" }, "Frage die Songtext-Datenbank …"));
    const j = await ruf("lrcsuche", { id: s.id, suchwort: feld.value });
    if (!j) { liste.replaceChildren(el("p", { class: "leer" }, "Nichts gefunden.")); return; }
    if (!j.treffer.length) { liste.replaceChildren(el("p", { class: "leer" }, "Nichts gefunden. Anderen Interpreten eintippen oder den Text unter „Text bearbeiten“ einfügen.")); return; }
    liste.replaceChildren(...j.treffer.map((t) => el("div", { class: "treffer" },
      el("div", { class: "weit" },
        el("b", {}, t.titel), " · ", t.interpret,
        el("div", { class: "klein" }, `Länge ${zeit(t.dauerMs)} (dein Track ${zeit(s.dauerMs)})` + (t.mitZeiten ? " · mit Zeitmarken" : " · ohne Zeitmarken") + ` · ${t.teile} Teile` + (t.refrain ? ", mit Refrain" : "")),
        el("div", { class: "zitat" }, "„" + t.ersteZeile + " …“")),
      el("button", { class: "knopf", onclick: async () => { await aendern("lrcnehmen", { nr: t.nr }); if (!Z.song.abschnitte.length) return; Z.ansicht = "teile"; zeigeSong(); } }, s.text ? "Diesen Text statt des alten nehmen" : "Diesen Text nehmen"))));
  };
  feld.addEventListener("keydown", (e) => { if (e.key === "Enter") suchen(); });
  setTimeout(suchen, 0);
  return el("div", {},
    el("p", { class: "hinweis" }, "Treffer aus der freien Songtext-Datenbank LRCLIB, der passendste oben. Danach unter „Teile & Ablauf“ ordnen oder „Ablauf aus Studio One vorschlagen“."),
    el("div", { class: "reihe", style: "margin-bottom:12px" }, feld, el("button", { class: "knopf", onclick: suchen }, "Suchen")),
    liste);
}

function textAnsicht() {
  const s = Z.song;
  const feld = el("textarea", { spellcheck: "false", placeholder: "Text hier einfügen. Teile beginnen mit einer Zeile wie [Strophe 1] oder [Refrain]. Ohne solche Zeilen trennt eine Leerzeile die Teile." });
  feld.value = s.text;
  return el("div", {},
    el("p", { class: "hinweis" }, "Teile beginnen mit einer Zeile in eckigen Klammern, zum Beispiel [Strophe 1], [Refrain], [Bridge]. Text aus einem PDF: PDF öffnen, Text markieren (Strg+A), kopieren (Strg+C), hier einfügen (Strg+V)."),
    feld,
    el("div", { class: "reihe", style: "margin-top:10px" },
      el("button", { class: "knopf voll", onclick: () => aendern("text", { text: feld.value }) }, "Text speichern")));
}

function teileAnsicht() {
  const s = Z.song;
  if (!s.abschnitte.length) return el("p", { class: "leer" }, "Dieser Song hat noch keinen Text. Oben „Text bearbeiten“.");
  return el("div", { class: "spalten" },
    el("div", {}, el("div", { class: "etikett", style: "margin-bottom:8px" }, `Dein Ablauf · ${s.ablauf.length} Teile · so kommt es auf die Bühne`), ...s.ablauf.map(karte)),
    el("div", {}, s1Kasten(), vorratKasten()));
}

function karte(t, i) {
  const s = Z.song;
  const a = t.abschnitt;
  const zeilen = a === INSTR ? [] : s.abschnitte[a].zeilen;
  const s1 = s1TeilBei(t.startMs);
  let nr = -1;
  const waehler = el("select", { title: "Anderen Text auf diesen Platz legen", onchange: (e) => aendern("tauschen", { pos: i, abschnitt: Number(e.target.value) }) },
    ...s.abschnitte.map((ab, k) => el("option", { value: k, selected: k === a }, `${ab.name}: ${(ab.zeilen.find((z) => z.trim()) || "").slice(0, 34)}`)),
    el("option", { value: INSTR, selected: a === INSTR }, "Instrumental (ohne Gesang)"));
  const k = el("div", { class: "karte", draggable: "true" },
    el("div", { class: "nr", title: "Ziehen zum Verschieben" }, String(i + 1)),
    el("div", { class: "oben" },
      pille(a),
      t.startMs >= 0 ? el("span", { class: "zeit" }, zeit(t.startMs), el("small", {}, s.vorlaeufig ? (s1 ? ` · Studio One: ${s1.name}` : " · aus Vorlage") : (t.getippt > 1 ? ` · ${t.getippt} Zeilen getippt` : " · eingemessen"))) : null,
      el("span", { class: "tun" },
        el("button", { class: "knopf klein", disabled: i === 0, title: "Nach oben", onclick: () => schiebe(i, i - 1) }, "▲"),
        el("button", { class: "knopf klein", disabled: i === s.ablauf.length - 1, title: "Nach unten", onclick: () => schiebe(i, i + 1) }, "▼"),
        el("button", { class: "knopf klein", onclick: () => { const f = folge(); f.splice(i + 1, 0, a); aendern("folge", { folge: f }); } }, "Doppeln"),
        el("button", { class: "knopf klein strich", disabled: s.ablauf.length < 2, onclick: () => { const f = folge(); f.splice(i, 1); aendern("folge", { folge: f }); } }, "Weg"))),
    el("div", { class: "zeilen" },
      a === INSTR ? el("div", { class: "hinweis" }, "ohne Gesang") : null,
      ...zeilen.map((z) => {
        if (!z.trim()) return el("div", { class: "zeile luecke" });
        nr++;
        const n = nr;
        return el("div", { class: "zeile" }, n > 0 ? el("span", { class: "schnitt", title: "Den Teil vor dieser Zeile in zwei Teile trennen", onclick: () => aendern("teilen", { abschnitt: a, zeile: n }) }) : null, z);
      }),
      el("div", { class: "reihe", style: "margin-top:8px;align-items:center" },
        // Jan, 13.11.: Teile waren zerstückelt, der Knopf dafür stand nur unten im Vorrat.
        i > 0 && a !== INSTR && a > 0 && s.ablauf[i - 1].abschnitt === a - 1
          ? el("button", { class: "knopf klein", title: "Diesen Text an die Karte darüber hängen: aus zwei Teilen wird einer.", onclick: () => aendern("verbinden", { abschnitt: a }) }, "↑ Mit Teil darüber zusammenfügen") : null,
        a === INSTR ? null : el("button", { class: "knopf klein", onclick: () => { const n = prompt("Neuer Name für diesen Teil (z. B. Strophe, Refrain, Bridge, Solo):", s.abschnitte[a].name); if (n && n.trim()) aendern("etikett", { abschnitt: a, name: n.trim() }); } }, "Umbenennen")),
      el("div", { class: "reihe", style: "margin-top:6px;align-items:center" }, waehler,
        el("button", { class: "knopf klein", title: "Hier ist kein Gesang: Der Text ab dieser Karte rückt einen Platz nach unten. Die Zeiten bleiben an ihren Plätzen.", onclick: () => aendern("ruecken", { pos: i, vor: false }) }, "Text ab hier ↓"),
        a === INSTR ? el("button", { class: "knopf klein", title: "Dieser Platz wird gesungen: Der Text von unten rückt einen Platz herauf.", onclick: () => aendern("ruecken", { pos: i, vor: true }) }, "Text heraufholen ↑") : null)));
  // Ziehen: Karte auf eine andere Karte fallen lassen (obere Hälfte = davor, untere = dahinter).
  k.addEventListener("dragstart", (e) => { e.dataTransfer.setData("text/plain", "karte:" + i); k.classList.add("zieht"); });
  k.addEventListener("dragend", () => k.classList.remove("zieht"));
  k.addEventListener("dragover", (e) => { e.preventDefault(); const oben = e.offsetY < k.offsetHeight / 2; k.classList.toggle("ziel-oben", oben); k.classList.toggle("ziel-unten", !oben); });
  k.addEventListener("dragleave", () => k.classList.remove("ziel-oben", "ziel-unten"));
  k.addEventListener("drop", (e) => {
    e.preventDefault();
    const danach = k.classList.contains("ziel-unten");
    k.classList.remove("ziel-oben", "ziel-unten");
    const [art, wert] = e.dataTransfer.getData("text/plain").split(":");
    const f = folge();
    let ziel = i + (danach ? 1 : 0);
    if (art === "karte") {
      const von = Number(wert);
      const [x] = f.splice(von, 1);
      if (von < ziel) ziel--;
      if (von === ziel) return;
      f.splice(ziel, 0, x);
    } else if (art === "vorrat") f.splice(ziel, 0, Number(wert));
    else return;
    aendern("folge", { folge: f });
  });
  return k;
}

function schiebe(von, nach) { const f = folge(); const [x] = f.splice(von, 1); f.splice(nach, 0, x); aendern("folge", { folge: f }); }
function hintenAn(a) { aendern("folge", { folge: [...folge(), a] }); }

function vorratKasten() {
  const s = Z.song;
  const eintrag = (a) => {
    const ab = a === INSTR ? null : s.abschnitte[a];
    const d = el("div", { class: "vorrat", draggable: "true" },
      el("div", { class: "reihe", style: "align-items:center" }, pille(a),
        el("span", { class: "hinweis", style: "margin:0" }, a === INSTR ? "" : ab.imAblauf === 0 ? "nicht im Ablauf" : `${ab.imAblauf}× im Ablauf`)),
      el("div", { class: "erste" }, a === INSTR ? "Solo, Zwischenspiel, Vorspiel" : ab.zeilen.find((z) => z.trim()) || "(ohne Text)"),
      el("div", { class: "reihe" },
        el("button", { class: "knopf klein", onclick: () => hintenAn(a) }, "+ ans Ende"),
        a === INSTR ? null : el("button", { class: "knopf klein", onclick: () => { const n = prompt("Neuer Name für diesen Teil (z. B. Strophe, Refrain, Bridge, Tag):", ab.name); if (n && n.trim()) aendern("etikett", { abschnitt: a, name: n.trim() }); } }, "Umbenennen"),
        a > 0 ? el("button", { class: "knopf klein strich", title: "Diesen Text an den Teil davor hängen", onclick: () => aendern("verbinden", { abschnitt: a }) }, "An vorigen hängen") : null));
    d.addEventListener("dragstart", (e) => e.dataTransfer.setData("text/plain", "vorrat:" + a));
    return d;
  };
  return el("div", { class: "kasten" },
    el("h3", {}, "Text-Vorrat"),
    el("p", { class: "hinweis" }, "Alle Textteile dieses Songs. In den Ablauf ziehen oder „+ ans Ende“."),
    ...s.abschnitte.map((_, a) => eintrag(a)), eintrag(INSTR));
}

function s1Kasten() {
  const s = Z.song;
  const s1 = s.s1;
  return el("div", { class: "kasten" },
    el("h3", {}, "Studio One"),
    s1 ? el("div", {},
      el("div", { class: "hinweis" }, "Datei: " + s1.name),
      s1.hinweis ? el("div", { class: "hinweis" }, s1.hinweis) : null,
      ...s1.teile.map((t) => el("div", { class: "s1teil" }, el("b", {}, zeit(t.startMs)), t.name)),
      el("div", { class: "reihe", style: "margin-top:10px" },
        el("button", { class: "knopf voll", disabled: !s1.teile.length, onclick: () => aendern("s1vorschlag", {}) }, "Ablauf daraus vorschlagen"),
        el("button", { class: "knopf", onclick: s1Waehlen }, "Andere Datei")))
      : el("div", {}, el("p", { class: "hinweis" }, "Zu diesem Song ist keine Studio-One-Datei gefunden."), el("button", { class: "knopf", onclick: s1Waehlen }, "Datei wählen")));
}

async function s1Waehlen() {
  const liste = el("ul");
  const feld = el("input", { type: "search", placeholder: "Name der Studio-One-Datei oder des Ordners …", style: "width:100%;font:inherit;padding:8px 10px;border:2.5px solid #000;border-radius:10px" });
  const d = el("dialog", {}, el("h3", {}, "Studio-One-Datei für „" + Z.song.titel + "“"), feld, liste,
    el("div", { class: "reihe" },
      el("button", { class: "knopf strich", onclick: async () => { d.close(); await aendern("s1waehlen", { datei: "" }); } }, "Keine Datei"),
      el("button", { class: "knopf", onclick: () => d.close() }, "Abbrechen")));
  const suchen = async () => {
    const j = await ruf("s1liste?q=" + encodeURIComponent(feld.value));
    if (!j) return;
    liste.replaceChildren(...j.dateien.map((f) => el("li", { onclick: async () => { d.close(); await aendern("s1waehlen", { datei: f.pfad }); } }, el("b", {}, f.name), " · ", f.ordner)));
    if (!j.dateien.length) liste.append(el("li", {}, "Nichts gefunden."));
  };
  feld.addEventListener("input", suchen);
  d.addEventListener("close", () => d.remove());
  document.body.append(d);
  d.showModal();
  feld.value = Z.song.titel.split(" ")[0];
  suchen();
}

// ── Sicherungen vom Mudita ───────────────────────────────────────────────────

async function zeigeSicherungen() {
  const j = await ruf("sicherungen");
  if (!j) return;
  Z.song = null; zeigeListe();
  const datum = (ms) => new Date(ms).toLocaleString("de-DE", { weekday: "short", day: "2-digit", month: "2-digit", year: "2-digit", hour: "2-digit", minute: "2-digit" });
  $("#werkbank").replaceChildren(
    el("h2", {}, "Sicherungen vom Mudita"),
    el("p", { class: "unterzeile" }, "Jedes Mal, wenn die Werkstatt Daten vom Mudita holt, legt sie hier eine Kopie ab (nur wenn sich etwas geändert hat). " +
      "Falls der Mudita einmal Daten verliert, spielst du hier einen Stand zurück. Songs, Texte, Zeiten, Gigs und Setlisten kommen dann wieder so, wie sie zu dem Zeitpunkt waren."),
    j.liste.length ? el("ul", { class: "sicherungen" }, ...j.liste.map((s, i) => el("li", {},
      el("div", { class: "weit" }, el("b", {}, datum(s.zeit)), i === 0 ? " (neueste)" : "",
        el("div", { class: "klein" }, `${s.songs} Songs, davon ${s.mitText} mit Text · ${s.gigs} ${s.gigs === 1 ? "Gig" : "Gigs"}`)),
      el("button", { class: "knopf", onclick: () => zurueckspielen(s, datum(s.zeit)) }, "Auf den Mudita zurückspielen"))))
      : el("p", { class: "leer" }, "Noch keine Sicherung. Sie entsteht beim ersten „Vom Mudita holen“."));
}

async function zurueckspielen(s, wann) {
  if (!confirm(`Den Mudita auf den Stand vom ${wann} zurücksetzen?\n\n${s.songs} Songs, ${s.gigs} Gigs.\nAlles, was du seitdem am Mudita geändert hast, ist danach weg (TrikTrak hebt den Stand davor aber auf).`)) return;
  const j = await ruf("wiederherstellen", { datei: s.datei });
  if (!j) return;
  Z.liste = j; zeigeStand(); zeigeListe(); melden(j.gesendet);
  zeigeSicherungen();
}

$("#sicherungen").addEventListener("click", zeigeSicherungen);

// ── Gig-Check ────────────────────────────────────────────────────────────────

async function zeigeGigCheck() {
  const j = await ruf("gigs");
  if (!j) return;
  Z.song = null; zeigeListe();
  const tag = (d) => d ? new Date(d * 86400000).toLocaleDateString("de-DE", { weekday: "short", day: "2-digit", month: "2-digit", year: "numeric" }) : "ohne Datum";
  const stand = (s) => !s.hatText ? "kein Text" : s.eingemessen === 0 ? "nicht eingemessen" : s.vorlaeufig ? "Zeiten nur aus Vorlage" : s.eingemessen < s.teile ? `${s.eingemessen} von ${s.teile} Teilen eingemessen` : "fertig";
  const fertig = (s) => stand(s) === "fertig" && !s.geaendert;
  $("#werkbank").replaceChildren(
    el("h2", {}, "Gig-Check"),
    el("p", { class: "unterzeile" }, "Was den Songs in deinen Sets noch fehlt. Klick auf einen Song öffnet ihn. " +
      "Ob die Musikdatei auf dem Mudita da ist, sieht nur der Mudita selbst."),
    ...(j.gigs.length ? j.gigs.map((g) => el("div", { class: "gig" },
      el("h3", {}, g.name, el("span", { class: "klein" }, "  ·  " + tag(g.datum))),
      ...g.sets.map((set) => {
        const offen = set.songs.filter((s) => !fertig(s)).length;
        const dauer = set.songs.reduce((n, s) => n + s.buehneMs, 0);
        return el("div", { class: "set" },
          el("div", { class: "setkopf" }, el("b", {}, set.name), `  ·  ${set.songs.length} Songs  ·  ca. ${zeit(dauer)} Min.  ·  `,
            offen ? el("b", {}, `${offen} noch nicht fertig`) : "alles fertig"),
          el("ol", {}, ...set.songs.map((s) => el("li", { class: fertig(s) ? "fertig" : "", onclick: () => waehle(s.id) },
            el("span", { class: "weit" }, s.titel), el("span", { class: "klein" }, zeit(s.dauerMs)),
            el("span", { class: "stand" }, stand(s) + (s.geaendert ? " · am Laptop geändert, noch nicht gesendet" : ""))))));
      })))
      : [el("p", { class: "leer" }, "Noch keine Gigs. Die legst du bisher am Mudita an.")]));
}

$("#gigcheck").addEventListener("click", zeigeGigCheck);

ladeListe();
