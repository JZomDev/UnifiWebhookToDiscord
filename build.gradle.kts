import org.gradle.api.JavaVersion.VERSION_11

plugins {
    id("org.springframework.boot") version("2.7.8")
    application
}

group = "org.unifiwebhookdiscord"
version = ""

description = "A webhook forwarder to Discord from Unifi"

java {
    sourceCompatibility = VERSION_11
}

// Javacord is on Maven central
repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:2.7.8"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-test")
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
    implementation("com.google.code.gson:gson:2.10.1")

    runtimeOnly("org.apache.logging.log4j:log4j-core:2.19.0")
}

application {
    mainClass.set("org.unifiwebhook.Main")
}



tasks.withType<org.gradle.jvm.tasks.Jar>() {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/BC1024KE.RSA", "META-INF/BC1024KE.SF", "META-INF/BC1024KE.DSA")
    exclude("META-INF/BC2048KE.RSA", "META-INF/BC2048KE.SF", "META-INF/BC2048KE.DSA")
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}


val fatJar = task("fatJar", type = Jar::class) {
//    baseName = "${project.name}"
    manifest {
        attributes["Implementation-Title"] = "Gradle Jar File Example"
        attributes["Implementation-Version"] = version
        attributes["Main-Class"] = "org.javacord.unifiwebhook.Main"
    }
    from(configurations.runtimeClasspath.get().map({ if (it.isDirectory) it else zipTree(it) }))
    with(tasks.jar.get() as CopySpec)
}