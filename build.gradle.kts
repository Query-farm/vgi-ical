import java.util.zip.ZipFile

plugins {
    java
    application
    // Fat/shaded JAR: `./gradlew shadowJar` -> build/libs/vgi-ical-<ver>-all.jar
    id("com.gradleup.shadow") version "9.4.2"
}

group = "farm.query"
version = "0.1.0-SNAPSHOT"

repositories {
    // The VGI Java SDK is published to Maven Central as farm.query:vgi /
    // farm.query:vgirpc, so the build is fully self-contained — no mavenLocal,
    // no sibling checkout, no composite build.
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all,-serial,-processing",
            "-parameters", // ScalarFn reflects parameter names off @Vector/@Const/@Setting
        )
    )
    options.encoding = "UTF-8"
}

dependencies {
    // VGI Java SDK from Maven Central. `vgi` is the worker/catalog API and pulls
    // in farm.query:vgirpc transitively; vgirpc is declared explicitly because
    // the code imports farm.query.vgirpc.* directly.
    implementation("farm.query:vgi:0.6.0")
    implementation("farm.query:vgirpc:0.10.2")

    // iCal4j — BSD-3-Clause (permissive). The RFC 5545 (iCalendar) parser:
    // CalendarBuilder + the net.fortuna.ical4j.model object tree (VEvent, VToDo,
    // VTimeZone, ...). Pulls in commons-lang3/-codec, threeten-extra, caffeine,
    // jparsec, groovy transitively (all permissive: Apache-2.0 / BSD / EPL).
    implementation("org.mnode.ical4j:ical4j:4.2.5")

    // slf4j-simple sends ALL log output to System.err. A stdio VGI worker speaks
    // Arrow-IPC on stdout, so any library logging that lands on stdout corrupts
    // the transport and hangs the worker. iCal4j (and caffeine) log via slf4j;
    // binding slf4j-simple keeps every line on stderr.
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("farm.query.vgi.ical.Main")
    applicationDefaultJvmArgs = listOf("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

// Concatenate every dependency's META-INF/services/* SPI file into a generated
// resources dir, then feed it to the shaded JAR. Several deps (vgi SDK, Arrow,
// slf4j) ship the SAME service interface across jars; shadow's
// mergeServiceFiles() alone can collapse them to a single surviving entry.
// Pre-merging into project resources is deterministic and version-agnostic.
val generatedSpiDir = layout.buildDirectory.dir("generated/spi")
val generateMergedSpi = tasks.register("generateMergedSpi") {
    val runtime = configurations.named("runtimeClasspath")
    inputs.files(runtime)
    outputs.dir(generatedSpiDir)
    doLast {
        val servicesByName = linkedMapOf<String, LinkedHashSet<String>>()
        runtime.get().files.filter { it.name.endsWith(".jar") }.forEach { jar ->
            ZipFile(jar).use { zf ->
                zf.entries().asSequence()
                    .filter { e ->
                        !e.isDirectory && e.name.startsWith("META-INF/services/") &&
                            e.name.removePrefix("META-INF/services/").isNotEmpty()
                    }
                    .forEach { e ->
                        val svc = e.name.removePrefix("META-INF/services/")
                        val lines = zf.getInputStream(e).bufferedReader().readLines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                        if (lines.isNotEmpty()) {
                            servicesByName.getOrPut(svc) { linkedSetOf() }.addAll(lines)
                        }
                    }
            }
        }
        val outRoot = generatedSpiDir.get().dir("META-INF/services").asFile
        outRoot.deleteRecursively()
        outRoot.mkdirs()
        servicesByName.forEach { (svc, impls) ->
            outRoot.resolve(svc).writeText(impls.joinToString("\n", postfix = "\n"))
        }
        logger.lifecycle("generateMergedSpi: merged ${servicesByName.size} service files")
    }
}

tasks.shadowJar {
    archiveBaseName.set("vgi-ical")
    archiveClassifier.set("all")
    dependsOn(generateMergedSpi)
    // The pre-merged SPI files override the per-jar ones; mergeServiceFiles()
    // still concatenates anything they miss.
    from(generatedSpiDir)
    mergeServiceFiles()
    manifest {
        attributes(
            "Main-Class" to "farm.query.vgi.ical.Main",
            "Multi-Release" to "true",
            // Arrow's off-heap MemoryUtil needs java.nio reflectively opened. Bake
            // it into the manifest so a bare `java -jar vgi-ical-all.jar` works as
            // a VGI LOCATION without the caller passing --add-opens.
            "Add-Opens" to "java.base/java.nio",
        )
    }
}

// Make `build` produce the fat jar.
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}

// Regenerate the committed SQL E2E fixtures (test/sql/data/*.ics) from the same
// in-test iCalendar builder the JUnit tests use, so fixtures are reproducible
// from source rather than opaque committed files.
tasks.register<JavaExec>("generateSqlFixtures") {
    group = "verification"
    description = "Generate test/sql/data fixtures (*.ics)."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("farm.query.vgi.ical.SqlFixtureGenerator")
    args(layout.projectDirectory.dir("test/sql/data").asFile.absolutePath)
}
