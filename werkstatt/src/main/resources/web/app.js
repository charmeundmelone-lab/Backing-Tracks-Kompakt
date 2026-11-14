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
  const offen = l.offen ?? l.songs.filter((s) => s.geaendert).length;
  $("#stand").textContent = wann + (offen ? `  ·  ${offen} ${offen === 1 ? "Änderung" : "Änderungen"} hier, noch nicht auf dem Mudita` : "") + (l.mudita ? "  ·  Mudita ist angeschlossen" : "  ·  Mudita nicht angeschlossen");
  const b = $("#senden");
  b.disabled = !offen;
  b.textContent = offen ? `Zum Mudita senden (${offen})` : "Zum Mudita senden";
  b.title = offen ? "Schickt geänderte Songs (Text, Teile, Tonart, Kapo) und Gigs an TrikTrak. Deine Zeiten vom Einmessen bleiben, wo der Text gleich bleibt." : "Nichts zu senden: Am Laptop ist nichts geändert.";
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

/** B8: Name eines Platzes ohne Gesang, so steht er auf der Bühne (statt "Instrumental"). */
function nameOhneGesang(pos, a) {
  const jetzt = a === INSTR ? "Instrumental" : Z.song.abschnitte[a].name;
  const namen = ["Instrumental", "Intro", "Solo", "Zwischenspiel", "Outro"];
  if (!namen.includes(jetzt)) namen.push(jetzt);
  return el("label", { class: "klein", title: "So heißt dieser Platz auf der Bühne" }, "Auf der Bühne: ",
    el("select", {
      onchange: (e) => {
        let n = e.target.value;
        if (n === "?") { n = prompt("Name für diesen Platz ohne Gesang (z. B. Intro, Solo, Outro):", jetzt); if (!n || !n.trim()) { e.target.value = jetzt; return; } }
        aendern("ohnegesang", { pos, name: n.trim() });
      },
    }, ...namen.map((n) => { const o = el("option", { value: n }, n); if (n === jetzt) o.selected = true; return o; }),
    el("option", { value: "?" }, "Eigener Name …")));
}

function karte(t, i) {
  const s = Z.song;
  const a = t.abschnitt;
  const zeilen = a === INSTR ? [] : s.abschnitte[a].zeilen;
  const leer = !zeilen.some((z) => z.trim());
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
      leer ? el("div", { class: "hinweis" }, "ohne Gesang") : null,
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
        leer ? nameOhneGesang(i, a) : el("button", { class: "knopf klein", onclick: () => { const n = prompt("Neuer Name für diesen Teil (z. B. Strophe, Refrain, Bridge, Solo):", s.abschnitte[a].name); if (n && n.trim()) aendern("etikett", { abschnitt: a, name: n.trim() }); } }, "Umbenennen")),
      el("div", { class: "reihe", style: "margin-top:6px;align-items:center" }, waehler,
        leer ? null : el("button", { class: "knopf klein", title: "Hier ist kein Gesang: Der gesungene Text ab dieser Karte rückt einen Platz weiter. Karten ohne Gesang (Intro, Solo …) und alle Zeiten bleiben stehen.", onclick: () => aendern("ruecken", { pos: i, vor: false }) }, "Text ab hier ↓"),
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


// ── Songdaten: Tonart und Kapo für alle Songs (B22) ──────────────────────────

async function zeigeSongdaten() {
  const j = await ruf("songdaten");
  if (!j) return;
  Z.song = null; Z.liste = j; zeigeStand(); zeigeListe();
  const kapoWahl = (wert) => el("select", { class: "kapo" },
    ...Array.from({ length: 13 }, (_, n) => { const o = el("option", { value: String(n) }, n === 0 ? "ohne" : String(n)); if (n === wert) o.selected = true; return o; }));
  const zeilen = j.daten.map((s) => {
    const titel = el("input", { class: "titel", value: s.titel, maxlength: "80", autocomplete: "off" });
    const ton = el("input", { class: "tonart", value: s.tonart, placeholder: "z. B. Cm", maxlength: "12", autocomplete: "off" });
    const bpm = el("input", { class: "bpm", value: s.bpm ? String(s.bpm) : "", placeholder: "–", inputmode: "numeric", maxlength: "3", autocomplete: "off" });
    const kapo = kapoWahl(s.kapo);
    const merk = el("span", { class: "klein" }, s.geaendert ? "geändert" : "");
    const tr = el("tr", {}, el("td", { class: "weit" }, titel), el("td", {}, ton), el("td", {}, kapo), el("td", {}, bpm), el("td", {}, merk));
    const speichern = async () => {
      if (titel.value.trim() === s.titel && ton.value.trim() === s.tonart && Number(kapo.value) === s.kapo && (Number(bpm.value) || 0) === s.bpm) return;
      const r = await ruf("songdaten", { id: s.id, titel: titel.value, tonart: ton.value, kapo: Number(kapo.value), bpm: Number(bpm.value) || 0 });
      if (!r) return;
      const neu = r.daten.find((x) => x.id === s.id);
      Object.assign(s, neu); titel.value = s.titel; ton.value = s.tonart; bpm.value = s.bpm ? String(s.bpm) : ""; merk.textContent = s.geaendert ? "geändert" : "";
      Z.liste = r; zeigeStand(); zeigeListe();
    };
    ton.addEventListener("change", speichern);
    titel.addEventListener("change", speichern);
    bpm.addEventListener("change", speichern);
    kapo.addEventListener("change", speichern);
    // Enter springt eine Zeile tiefer ins selbe Feld: so tippt man die Liste schnell durch.
    for (const feld of [titel, ton, kapo, bpm]) feld.addEventListener("keydown", (e) => {
      if (e.key !== "Enter") return;
      e.preventDefault(); speichern();
      const naechste = tr.nextElementSibling;
      if (naechste) naechste.querySelector(feld === kapo ? "select" : "input." + feld.className).focus();
    });
    return tr;
  });
  $("#werkbank").replaceChildren(
    el("h2", {}, "Songdaten"),
    el("p", { class: "unterzeile" }, "Titel, Tonart, Kapo und Tempo (BPM) für alle Songs. TrikTrak zeigt Kapo und Tonart groß in der Pause und auf der Bühne, bevor du Play drückst. " +
      "Jede Änderung wird sofort gemerkt. Enter springt eine Zeile tiefer. Danach oben „Zum Mudita senden“."),
    el("table", { class: "songdaten" },
      el("thead", {}, el("tr", {}, el("th", {}, "Song"), el("th", {}, "Tonart"), el("th", {}, "Kapo"), el("th", {}, "BPM"), el("th", {}, ""))),
      el("tbody", {}, ...zeilen)));
  const erstes = $("#werkbank input.tonart");
  if (erstes) erstes.focus();
}

$("#songdaten").addEventListener("click", zeigeSongdaten);


// ── Gigs und Setlisten (B11) ─────────────────────────────────────────────────
//
// Regel (Jans Wahl 14.11.): Der Mudita wird geschützt. Hat er einen Gig seit dem letzten Holen
// selbst geändert, bleibt seiner, und die Werkstatt sagt Bescheid.

const tagAus = (d) => (d ? new Date(d * 86400000).toISOString().slice(0, 10) : "");
const tagEin = (v) => (v ? Math.round(Date.parse(v) / 86400000) : 0);
const tagLang = (d) => (d ? new Date(d * 86400000).toLocaleDateString("de-DE", { weekday: "short", day: "2-digit", month: "2-digit", year: "numeric", timeZone: "UTC" }) : "ohne Datum");

async function gigTun(daten) {
  const j = await ruf("gig", daten);
  if (!j) return;
  Z.plan = j; Z.liste = j; zeigeStand(); zeigeListe(); melden(""); zeigeGigs();
}

async function zeigeGigplan() {
  const j = await ruf("gigplan");
  if (!j) return;
  Z.song = null; Z.plan = j; Z.liste = j; zeigeStand(); zeigeListe();
  if (Z.gig !== undefined && !j.gigs.some((g) => g.id === Z.gig)) Z.gig = undefined;
  if (Z.gig === undefined && j.gigs.length) Z.gig = j.gigs[0].id;
  zeigeGigs();
}

function zeigeGigs() {
  const p = Z.plan;
  const g = p.gigs.find((x) => x.id === Z.gig);
  const neuerGig = async () => {
    const name = prompt("Name des neuen Gigs:");
    if (!name || !name.trim()) return;
    const datum = prompt("Datum (TT.MM.JJJJ), leer lassen = ohne Datum:", "");
    let tag = 0;
    if (datum && datum.trim()) {
      const m = datum.trim().match(/^(\d{1,2})\.(\d{1,2})\.(\d{2,4})$/);
      if (!m) { melden("Das Datum bitte so: 21.11.2026"); return; }
      const jahr = m[3].length === 2 ? 2000 + Number(m[3]) : Number(m[3]);
      tag = Math.round(Date.UTC(jahr, Number(m[2]) - 1, Number(m[1])) / 86400000);
    }
    const vorher = new Set(p.gigs.map((x) => x.id));
    await gigTun({ tun: "neu", name: name.trim(), datum: tag });
    const neu = Z.plan.gigs.find((x) => !vorher.has(x.id));
    if (neu) { Z.gig = neu.id; zeigeGigs(); }
  };
  $("#werkbank").replaceChildren(
    el("div", { class: "kopf" },
      el("div", { class: "weit" }, el("h2", {}, "Gigs"),
        el("p", { class: "unterzeile" }, "Gigs und Setlisten bearbeiten. Danach oben „Zum Mudita senden“. Hast du denselben Gig inzwischen am Mudita geändert, bleibt der Mudita-Stand, und du bekommst Bescheid.")),
      el("button", { class: "knopf", onclick: neuerGig }, "+ Neuer Gig")),
    el("div", { class: "reihe", style: "margin-bottom:14px" }, ...p.gigs.map((x) =>
      el("span", { class: "chip" + (x.id === Z.gig ? " an" : ""), onclick: () => { Z.gig = x.id; zeigeGigs(); } }, x.name + (x.geaendert ? " •" : "")))),
    g ? gigKasten(g) : el("p", { class: "leer" }, p.gigs.length ? "Oben einen Gig wählen." : "Noch keine Gigs. Oben „+ Neuer Gig“."));
}

function gigKasten(g) {
  const auto = el("input", { type: "checkbox" });
  auto.checked = g.autoWeiter;
  auto.addEventListener("change", () => gigTun({ tun: "aendern", gig: g.id, autoWeiter: auto.checked }));
  const datum = el("input", { type: "date", value: tagAus(g.datum) });
  datum.addEventListener("change", () => gigTun({ tun: "aendern", gig: g.id, datum: tagEin(datum.value) }));
  return el("div", { class: "gigplan" },
    el("div", { class: "kopf" },
      el("div", { class: "weit" },
        el("h3", {}, g.name, el("span", { class: "klein" }, "  ·  " + tagLang(g.datum) + (g.geaendert ? "  ·  hier geändert, noch nicht gesendet" : ""))),
        el("div", { class: "reihe", style: "align-items:center;margin-top:6px" },
          el("button", { class: "knopf klein", onclick: () => { const n = prompt("Neuer Name:", g.name); if (n && n.trim()) gigTun({ tun: "aendern", gig: g.id, name: n.trim() }); } }, "Umbenennen"),
          el("label", { class: "klein" }, "Datum: ", datum),
          el("label", { class: "klein" }, auto, " Nach einem Set automatisch ins nächste"))),
      el("button", { class: "knopf klein strich", onclick: () => { if (confirm(`Gig „${g.name}“ mit allen Sets löschen?\nDie Songs bleiben im Archiv. Auf dem Mudita verschwindet er erst nach „Zum Mudita senden“.`)) gigTun({ tun: "loeschen", gig: g.id }); } }, "Gig löschen")),
    ...g.sets.map((s, i) => setKasten(g, s, i)),
    el("button", { class: "knopf", style: "margin-top:12px", onclick: () => gigTun({ tun: "setNeu", gig: g.id }) }, "+ Set hinzufügen"));
}

function setKasten(g, s, i) {
  const dauer = s.songs.reduce((n, x) => n + x.dauerMs, 0);
  const drin = new Set(s.songs.map((x) => x.id));
  const wahl = el("select", {}, el("option", { value: "" }, "Song hinzufügen …"),
    ...Z.plan.alleSongs.filter((x) => !drin.has(x.id)).map((x) => el("option", { value: String(x.id) }, x.titel)));
  wahl.addEventListener("change", () => { if (wahl.value) gigTun({ tun: "songHinzu", gig: g.id, set: s.id, song: Number(wahl.value) }); });
  const zeilen = s.songs.map((x, k) => {
    const weiter = el("input", { type: "checkbox", title: "Danach startet der nächste Song von selbst (mit Ansage/Pause)" });
    weiter.checked = x.weiter;
    weiter.addEventListener("change", () => gigTun({ tun: "songEnde", gig: g.id, set: s.id, song: x.id, weiter: weiter.checked }));
    const li = el("li", { class: x.gespielt ? "gespielt" : "", draggable: "true" },
      el("span", { class: "nr" }, String(k + 1)),
      el("span", { class: "weit" }, x.titel, x.gespielt ? el("span", { class: "klein" }, "  · gespielt") : null),
      el("span", { class: "klein" }, zeit(x.dauerMs)),
      el("label", { class: "klein" }, weiter, " danach weiter"),
      el("button", { class: "knopf klein", disabled: k === 0, title: "Nach oben", onclick: () => gigTun({ tun: "songSchieben", gig: g.id, set: s.id, song: x.id, richtung: -1 }) }, "▲"),
      el("button", { class: "knopf klein", disabled: k === s.songs.length - 1, title: "Nach unten", onclick: () => gigTun({ tun: "songSchieben", gig: g.id, set: s.id, song: x.id, richtung: 1 }) }, "▼"),
      el("button", { class: "knopf klein strich", title: "Aus diesem Set nehmen (bleibt im Archiv)", onclick: () => gigTun({ tun: "songWeg", gig: g.id, set: s.id, song: x.id }) }, "Weg"));
    // Ziehen und auf einen anderen Song fallen lassen: landet an dessen Platz.
    li.addEventListener("dragstart", (e) => e.dataTransfer.setData("text/plain", `${s.id}:${x.id}`));
    li.addEventListener("dragover", (e) => { e.preventDefault(); li.classList.add("ziel-oben"); });
    li.addEventListener("dragleave", () => li.classList.remove("ziel-oben"));
    li.addEventListener("drop", (e) => {
      e.preventDefault(); li.classList.remove("ziel-oben");
      const [setId, songId] = e.dataTransfer.getData("text/plain").split(":").map(Number);
      if (setId !== s.id || songId === x.id) return;
      gigTun({ tun: "songSchieben", gig: g.id, set: s.id, song: songId, nach: k });
    });
    return li;
  });
  return el("div", { class: "set" },
    el("div", { class: "setkopf reihe", style: "align-items:center" },
      el("b", {}, s.name), el("span", { class: "klein" }, `${s.songs.length} Songs · ca. ${zeit(dauer)} Min.`),
      el("button", { class: "knopf klein", onclick: () => { const n = prompt("Name des Sets:", s.name); if (n && n.trim()) gigTun({ tun: "setName", gig: g.id, set: s.id, name: n.trim() }); } }, "Umbenennen"),
      el("button", { class: "knopf klein", disabled: i === 0, onclick: () => gigTun({ tun: "setSchieben", gig: g.id, set: s.id, richtung: -1 }) }, "▲"),
      el("button", { class: "knopf klein", disabled: i === g.sets.length - 1, onclick: () => gigTun({ tun: "setSchieben", gig: g.id, set: s.id, richtung: 1 }) }, "▼"),
      el("button", { class: "knopf klein strich", onclick: () => { if (confirm(`Set „${s.name}“ löschen? Die Songs bleiben im Archiv.`)) gigTun({ tun: "setLoeschen", gig: g.id, set: s.id }); } }, "Set löschen")),
    s.songs.length ? el("ol", { class: "setliste" }, ...zeilen) : el("p", { class: "klein" }, "Noch leer."),
    el("div", { class: "reihe", style: "margin-top:6px" }, wahl));
}

$("#gigplan").addEventListener("click", zeigeGigplan);


// ── Neue Songs vom Laptop auf den Mudita (B12) ───────────────────────────────

const mb = (b) => (b / 1048576).toFixed(0) + " MB";

async function zeigeNeueSongs(fertig) {
  const j = fertig || (await ruf("neuesongs"));
  if (!j) return;
  Z.song = null; Z.liste = j; zeigeStand(); zeigeListe();
  const STAND = {
    NEU: ["neu", "Noch nicht auf dem Mudita"],
    ANDERS: ["anders", "Auf dem Mudita liegt eine andere Fassung (neu exportiert?)"],
    GLEICH: ["gleich", "Schon genau so auf dem Mudita"],
    UNBEKANNT: ["bekannt", "Steht in TrikTrak. Ob die Datei gleich ist, sieht man nur mit angeschlossenem Mudita."],
  };
  const haken = [];
  const zeilen = j.dateien.map((d) => {
    const h = el("input", { type: "checkbox", value: d.name });
    h.checked = d.stand === "NEU" || d.stand === "ANDERS";
    haken.push(h);
    return el("tr", { class: d.stand === "GLEICH" ? "fertig" : "" },
      el("td", {}, h),
      el("td", { class: "weit" }, d.name, d.titel ? el("div", { class: "klein" }, "in TrikTrak: " + d.titel) : null),
      el("td", { class: "klein" }, mb(d.groesse)),
      el("td", { title: STAND[d.stand][1] }, el("span", { class: "pille " + (d.stand === "GLEICH" ? "ohne" : "refrain") }, STAND[d.stand][0])));
  });
  const knopf = el("button", { class: "knopf" }, "Angekreuzte auf den Mudita kopieren");
  knopf.addEventListener("click", async () => {
    const dateien = haken.filter((h) => h.checked).map((h) => h.value);
    if (!dateien.length) { melden("Bitte mindestens einen Song ankreuzen."); return; }
    const gross = j.dateien.filter((d) => dateien.includes(d.name)).reduce((n, d) => n + d.groesse, 0);
    if (!confirm(`${dateien.length} ${dateien.length === 1 ? "Datei" : "Dateien"} (${mb(gross)}) auf den Mudita kopieren?\n\nGleichnamige Dateien dort werden ersetzt. Danach liest TrikTrak den Musikordner neu, sobald die App offen ist und nichts spielt. Texte, Zeiten und Setlisten bleiben.`)) return;
    knopf.disabled = true; knopf.textContent = "Kopiere … (das dauert pro Song ein paar Sekunden)";
    const r = await ruf("neuesongs", { dateien });
    knopf.disabled = false; knopf.textContent = "Angekreuzte auf den Mudita kopieren";
    if (!r) return;
    melden(r.gesendet || "");
    zeigeNeueSongs(Object.assign({}, r, { dateien: r.neueSongs.dateien, laptopOrdner: r.neueSongs.laptopOrdner, muditaOrdner: r.neueSongs.muditaOrdner, verglichen: r.neueSongs.verglichen }));
  });
  $("#werkbank").replaceChildren(
    el("h2", {}, "Neue Songs"),
    el("p", { class: "unterzeile" }, `Deine fertigen Songs am Laptop (${j.laptopOrdner}) im Vergleich mit dem Mudita (${j.muditaOrdner}). ` +
      (j.verglichen ? "Angekreuzt sind die, die dort fehlen oder anders sind." : "Der Mudita ist nicht angeschlossen: Es lässt sich nur sagen, welche Songs TrikTrak schon kennt.")),
    j.dateien.length ? el("table", { class: "songdaten" },
      el("thead", {}, el("tr", {}, el("th", {}, ""), el("th", {}, "Datei"), el("th", {}, "Größe"), el("th", {}, "Stand"))),
      el("tbody", {}, ...zeilen)) : el("p", { class: "leer" }, "Im Laptop-Ordner liegen keine Musikdateien."),
    j.verglichen ? el("div", { class: "reihe", style: "margin-top:14px" }, knopf) : null);
}

$("#neuesongs").addEventListener("click", () => zeigeNeueSongs());

ladeListe();
