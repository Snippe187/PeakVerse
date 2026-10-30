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
tasks.jar {
    archiveBaseName = "PeakCore"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val own = rootDir.parentFile.absolutePath
    from({
        configurations.runtimeClasspath.get().files
            .filter { it.name.endsWith(".jar") && it.absolutePath.startsWith(own) }
            .map { zipTree(it) }
    })
    exclude("META-INF/MANIFEST.MF")
}
