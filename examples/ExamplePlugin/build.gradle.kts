plugins {
    `java-library`
}

dependencies {
    compileOnly("org.powernukkitx:server:3.0.5-SNAPSHOT")
    implementation(project(":"))
}
