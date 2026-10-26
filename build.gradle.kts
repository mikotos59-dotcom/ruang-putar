import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.8.2"
}

// ---------------------------------------------------------------- versi
val versiApp = providers.gradleProperty("versiApp").get()
val versiNewPipe = providers.gradleProperty("npeVersi").get()
// Nomor build: di GitHub Actions = 100 + nomor run (selalu naik); di PC = 1.
val nomorBuild = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100)) ?: 1
// Repo tempat aplikasi mengecek pembaruan (otomatis dari Actions).
val repoGithub = System.getenv("GITHUB_REPOSITORY") ?: "mikotos59-dotcom/ruang-putar"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    implementation("com.github.teamnewpipe:NewPipeExtractor:$versiNewPipe")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("net.java.dev.jna:jna:5.16.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// Versi.kt dibuat otomatis: nama versi, nomor build, versi mesin, repo GitHub.
val buatVersi by tasks.registering {
    val keluar = layout.buildDirectory.dir("generated/versi")
    val isi = """
        |package id.ruangputar
        |
        |/** Dibuat otomatis oleh Gradle — jangan diubah manual. */
        |object Versi {
        |    const val NAMA = "$versiApp"
        |    const val KODE = $nomorBuild
        |    const val NEWPIPE = "$versiNewPipe"
        |    const val REPO_GITHUB = "$repoGithub"
        |}
        |""".trimMargin()
    inputs.property("isi", isi)
    outputs.dir(keluar)
    doLast {
        val f = keluar.get().file("id/ruangputar/Versi.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(isi)
    }
}
kotlin.sourceSets.main { kotlin.srcDir(buatVersi) }

// Daftar library bawaan (grup:nama:versi) — dipakai fitur "perbarui mesin YouTube" tanpa pasang ulang.
val daftarLibrary by tasks.registering {
    val keluar = layout.buildDirectory.dir("generated/library")
    val cp = configurations.runtimeClasspath
    outputs.dir(keluar)
    doLast {
        val baris = cp.get().resolvedConfiguration.resolvedArtifacts
            .map { a -> "${a.moduleVersion.id.group}:${a.moduleVersion.id.name}:${a.moduleVersion.id.version}:${a.file.name}" }
            .sorted()
        val f = keluar.get().file("library.txt").asFile
        f.parentFile.mkdirs()
        f.writeText(baris.joinToString("\n"))
    }
}
sourceSets.main { resources.srcDir(daftarLibrary) }

compose.desktop {
    application {
        mainClass = "id.ruangputar.PeluncurKt"
        jvmArgs += listOf("-Xmx512m", "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "RuangPutar"
            packageVersion = versiApp
            description = "Ruang Putar — pemutar musik"
            vendor = "@SuS_X_x"
            copyright = "Dibuat oleh @SuS_X_x"
            // Semua modul Java ikut (lebih besar sedikit, tapi pasti tidak ada yang kurang)
            includeAllModules = true
            // libmpv (mesin pemutar suara) diletakkan di sumber/windows oleh skrip build
            appResourcesRootDir.set(project.layout.projectDirectory.dir("sumber"))
            windows {
                iconFile.set(project.file("installer/ruangputar.ico"))
                menuGroup = "Ruang Putar"
                upgradeUuid = "8D6E3C2A-41F5-4B7B-9A1E-6C2F0D3B7E51"
            }
            linux {
                iconFile.set(project.file("src/main/resources/ikon.png"))
            }
        }
    }
}
