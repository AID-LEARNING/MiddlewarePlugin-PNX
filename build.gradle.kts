plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

allprojects {
    group = "dev.senseitarzan"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        // PowerNukkitX official repository
        maven {
            name = "powerNukkitXReleases"
            url = uri("https://repo.powernukkitx.org/releases")
        }
        maven {
            name = "opencollabSnapshot"
            url = uri("https://repo.opencollab.dev/maven-snapshots/")
        }
        maven {
            name = "opencollabRelease"
            url = uri("https://repo.opencollab.dev/maven-releases/")
        }
    }
}

dependencies {
    compileOnly("org.powernukkitx:server:3.0.5-SNAPSHOT")
    testImplementation("org.powernukkitx:server:3.0.5-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

subprojects {
    apply(plugin = "java")

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "25"
        targetCompatibility = "25"
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "25"
    targetCompatibility = "25"
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}