plugins {
    java
}

group = "de.peakverse"
version = property("bundle_version") as String

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("de.peakverse:core-plugin")
    // Eingebaute Module: neue Module hier ergaenzen (und ihren Schalter in der config.yml des Cores)
    implementation("de.peaksound:paper-plugin")
    implementation("de.peakverse:texturepeak")
    implementation("de.peakverse:peakboard")

    testImplementation("org.yaml:snakeyaml:2.4")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("plugin.yml") { expand("version" to version) }
}

// Das Jar enthaelt nur unsere eigenen Projekte (Core, API, Protokoll, Module). Fremde Bibliotheken (die LGPL-Audio-Bibliotheken)
// werden NICHT eingeschmolzen, sondern ueber "libraries" in der plugin.yml beim Serverstart von Maven Central geladen.
// Die plugin.yml und config.yml dieses Projekts gewinnen gegen gleichnamige Dateien in den Teil-Jars.
// Jedes Modul bringt eine eigene Service-Datei mit; mit "erste gewinnt" ginge sonst nur ein Modul ins Gesamt-Jar. Darum werden sie zusammengefuehrt.
val ownJars = provider {
    val own = rootDir.parentFile.absolutePath
    configurations.runtimeClasspath.get().files.filter { it.name.endsWith(".jar") && it.absolutePath.startsWith(own) }
}
val moduleServiceName = "META-INF/services/de.peakverse.core.api.module.PeakModule"

val mergeModuleServices by tasks.registering {
    val out = layout.buildDirectory.file("generated/services/$moduleServiceName")
    dependsOn(configurations.runtimeClasspath) // baut die Jars der Teil-Projekte (sonst steckt ein veraltetes Jar im Gesamt-Jar)
    inputs.files(ownJars)
    outputs.file(out)
    doLast {
        val lines = sortedSetOf<String>()
        for (jar in ownJars.get()) {
            zipTree(jar).matching { include(moduleServiceName) }.files.forEach { f ->
                f.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
            }
        }
        out.get().asFile.apply { parentFile.mkdirs(); writeText(lines.joinToString("\n") + "\n") }
    }
}

tasks.jar {
    archiveBaseName = "PeakCore"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(mergeModuleServices, configurations.runtimeClasspath)
    from({ ownJars.get().map { zipTree(it).matching { exclude(moduleServiceName) } } })
    from(layout.buildDirectory.dir("generated/services"))
    exclude("META-INF/MANIFEST.MF")
}