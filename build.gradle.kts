plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Keep license sources at the repository root and include them in every published jar.
val licenseFiles = copySpec {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from("LICENSE")
    from("THIRD_PARTY_NOTICES.md") { into("META-INF/tenkeitech") }
    from("LICENSES") { into("META-INF/tenkeitech/licenses") }
}

tasks.withType<Jar>().configureEach {
    with(licenseFiles)
}

tasks.named("compileJava") {
    dependsOn("spotlessApply")
}

tasks.withType<Test>().configureEach {
    enabled = false
}
