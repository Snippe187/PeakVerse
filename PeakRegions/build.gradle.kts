plugins {
    `java-library`
}

group = "de.peakverse"
version = "0.1.8"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

// Ein Modul ist kein eigenes Plugin-Jar: Das Projekt PeakVerse/Bundle setzt es mit dem Core zum Jar PeakCore-<Version>.jar zusammen.
dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    // Die PeakCore-API ist im fertigen Jar schon dabei, deshalb nur zum Kompilieren
    compileOnly("de.peakverse:core-api")
    // WorldGuard, WorldEdit und SquareMap sind eigene Plugins des Servers; ihre APIs werden nur benutzt, wenn sie installiert sind
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.21")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.6")
    compileOnly("xyz.jpenilla:squaremap-api:1.4.3")

    testCompileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    testImplementation("io.papermc.paper:paper-api:${property("paper_api_version")}")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("de.peakverse:core-api")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
