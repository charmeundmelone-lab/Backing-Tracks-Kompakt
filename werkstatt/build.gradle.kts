// TrikTrak Werkstatt: das Laptop-Programm. Läuft als kleine Seite im Browser (nur auf diesem
// Rechner erreichbar) und benutzt denselben Text- und Ablauf-Code wie die Kompakt-App.
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    sourceSets["main"].kotlin.apply {
        // Geteilter Code aus der App: nur die Dateien ohne Android-Teile.
        srcDir("../app/src/main/java")
        include(
            "show/jangregersen/werkstatt/**",
            "show/jangregersen/buehne/lyrics/**",
            "show/jangregersen/buehne/daten/Modell.kt",
            "show/jangregersen/buehne/daten/Aenderungen.kt",
            "show/jangregersen/buehne/daten/BestandJson.kt",
            "show/jangregersen/buehne/daten/GigAbgleich.kt",
            "show/jangregersen/buehne/netz/Lrclib.kt"
        )
    }
}

application { mainClass.set("show.jangregersen.werkstatt.MainKt") }

tasks.processResources {
    // Dieselben Schriften wie auf dem Kompakt.
    from("../app/src/main/res/font") { into("web/schrift") }
}

dependencies {
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
