plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencies {
        add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.5")
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        buildUponDefaultConfig = true
        parallel = true
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        val ktlintSourceExcludedProjects = setOf(":core:data", ":core:decoders")
        val isKtlintSourceExcludedProject = project.path in ktlintSourceExcludedProjects
        val projectSourcePath = project.projectDir.invariantSeparatorsPath + "/src/"

        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            exclude("**/build/**")
            exclude { fileTreeElement ->
                isKtlintSourceExcludedProject && fileTreeElement.file.invariantSeparatorsPath.startsWith(projectSourcePath)
            }
        }
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs Kotlin style checks, static analysis, Android lint, and unit tests for the project."

    val androidProjects =
        listOf(
            ":app",
            ":core:data",
            ":core:decoders",
            ":core:ui",
            ":feature:details",
            ":feature:radar",
            ":feature:settings",
            ":feature:watchlist",
        )
    val jvmProjects = listOf(":core:domain", ":core:model")

    dependsOn(subprojects.map { "${it.path}:ktlintCheck" })
    dependsOn(subprojects.map { "${it.path}:detekt" })
    dependsOn(androidProjects.map { "$it:lintDebug" })
    dependsOn(androidProjects.map { "$it:testDebugUnitTest" })
    dependsOn(jvmProjects.map { "$it:test" })
}
