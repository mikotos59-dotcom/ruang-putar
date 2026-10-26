package id.ruangputar.inti

import id.ruangputar.App
import id.ruangputar.Versi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * "Mesin YouTube" = NewPipeExtractor. Di PC mesinnya bisa diganti tanpa memasang ulang aplikasi:
 * file .jar versi baru diunduh ke %APPDATA%\RuangPutar\mesin\<versi>, lalu Peluncur memakainya
 * saat aplikasi dibuka lagi. Kalau versi baru membuat aplikasi gagal dibuka, otomatis kembali
 * ke mesin bawaan.
 */
object Mesin {
    sealed class Status {
        data object Diam : Status()
        data object Mengecek : Status()
        data object Terbaru : Status()
        /** Ada versi baru; [lewatAplikasi] = sudah ada versi aplikasi yang membawanya (lebih aman). */
        data class Tersedia(val versi: String, val lewatAplikasi: Boolean) : Status()
        data class Memasang(val versi: String, val keterangan: String) : Status()
        data class PerluMulaiUlang(val versi: String) : Status()
        data class Galat(val pesan: String) : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Diam)
    val status: StateFlow<Status> = _status
    private var tugas: Job? = null

    val folder: File get() = File(App.folderData, "mesin")

    /** Versi mesin yang sedang dipakai (bawaan atau hasil pembaruan). */
    val versiAktif: String
        get() = System.getProperty("ruangputar.mesin") ?: Versi.NEWPIPE

    val diperbarui: Boolean get() = System.getProperty("ruangputar.mesin") != null

    /** Pembaruan mesin sebelumnya membuat aplikasi gagal dibuka (diisi Peluncur). */
    val gagalSebelumnya: String? get() = System.getProperty("ruangputar.mesin.gagal")

    // ------------------------------------------------------------------ cek versi terbaru
    fun cek(manual: Boolean) {
        if (tugas?.isActive == true) return
        tugas = App.scope.launch(Dispatchers.IO) {
            if (manual) _status.value = Status.Mengecek
            try {
                val terbaru = versiTerbaru()
                if (bandingkan(terbaru, versiAktif) <= 0) {
                    _status.value = if (manual) Status.Terbaru else Status.Diam
                    return@launch
                }
                // Sudah ada versi aplikasi yang membawa mesin ini? (dibuat otomatis tiap Senin)
                val app = Pembaruan.status.value
                val lewatApp = app is Pembaruan.Status.Tersedia && app.npe.isNotEmpty() && bandingkan(app.npe, terbaru) >= 0
                _status.value = Status.Tersedia(terbaru, lewatApp)
            } catch (e: Exception) {
                _status.value = if (manual) Status.Galat("Gagal mengecek mesin: ${e.message ?: e.javaClass.simpleName}") else Status.Diam
            }
        }
    }

    /** Tag rilis terbaru NewPipeExtractor, dari alamat .../releases/latest (tanpa API GitHub). */
    private fun versiTerbaru(): String {
        val klien = Jaringan.api.newBuilder().followRedirects(false).build()
        val req = okhttp3.Request.Builder().url("https://github.com/TeamNewPipe/NewPipeExtractor/releases/latest")
            .header("User-Agent", "RuangPutar/${Versi.NAMA}").build()
        klien.newCall(req).execute().use { r ->
            val lokasi = r.header("Location") ?: throw IllegalStateException("HTTP ${r.code}")
            return lokasi.substringAfterLast("/tag/").takeIf { it.isNotBlank() && it != lokasi }
                ?: throw IllegalStateException("Versi tidak terbaca")
        }
    }

    /** "v0.26.10" vs "v0.26.5" -> positif kalau a lebih baru. */
    fun bandingkan(a: String, b: String): Int {
        fun angka(v: String) = v.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
        val x = angka(a)
        val y = angka(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    // ------------------------------------------------------------------ pasang mesin baru
    private class Modul(val grup: String, val nama: String, val versi: String) {
        val kunci get() = "${grup.lowercase()}:$nama"
        val dariJitpack get() = grup.lowercase().startsWith("com.github.")
        fun alamat(ext: String): String {
            val dasar = if (dariJitpack) "https://jitpack.io/" else "https://repo1.maven.org/maven2/"
            return "$dasar${grup.replace('.', '/')}/$nama/$versi/$nama-$versi.$ext"
        }
    }

    /** Library bawaan aplikasi: grup:nama -> (versi, nama file jar). */
    private fun bawaan(): Map<String, Pair<String, String>> {
        val teks = Mesin::class.java.getResourceAsStream("/library.txt")?.bufferedReader()?.readText() ?: return emptyMap()
        return teks.lines().mapNotNull { b ->
            val p = b.trim().split(':')
            if (p.size >= 4) "${p[0].lowercase()}:${p[1]}" to (p[2] to p[3]) else null
        }.toMap()
    }

    fun pasang(versi: String) {
        if (tugas?.isActive == true) return
        tugas = App.scope.launch(Dispatchers.IO) {
            try {
                _status.value = Status.Memasang(versi, "Membaca daftar file…")
                val lib = bawaan()
                val tujuan = File(folder, versi).apply { deleteRecursively(); mkdirs() }
                val jar = ArrayList<String>()
                val ganti = ArrayList<String>()
                val dilihat = HashSet<String>()
                val antre = ArrayDeque<Pair<Modul, Int>>()
                antre += Modul("com.github.teamnewpipe", "NewPipeExtractor", versi) to 0
                while (antre.isNotEmpty()) {
                    val (m, tingkat) = antre.removeFirst()
                    if (!dilihat.add(m.kunci)) continue
                    val ada = lib[m.kunci]
                    if (ada != null && ada.first == m.versi && tingkat > 0) continue      // sama dengan bawaan: pakai yang ada
                    _status.value = Status.Memasang(versi, "Mengunduh ${m.nama} ${m.versi}…")
                    val pom = ambilTeks(m.alamat("pom"))
                    val bytes = ambilBytes(m.alamat("jar"))
                    if (bytes != null) {
                        val f = File(tujuan, "${m.nama}-${m.versi}.jar")
                        f.writeBytes(bytes)
                        jar += f.name
                        if (ada != null) ganti += m.kunci
                    }
                    if (pom != null && tingkat < 4) {
                        ketergantungan(pom, m.versi).forEach { antre += it to tingkat + 1 }
                    }
                }
                if (jar.none { it.startsWith("extractor-") || it.startsWith("NewPipeExtractor-") }) {
                    throw IllegalStateException("File mesin $versi belum tersedia di JitPack. Coba lagi beberapa menit lagi.")
                }
                // uji: kelas utama mesin bisa dimuat dari file baru
                _status.value = Status.Memasang(versi, "Memeriksa…")
                val url = tujuan.listFiles()!!.filter { it.extension == "jar" }.map { it.toURI().toURL() }
                java.net.URLClassLoader(url.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { cl ->
                    Class.forName("org.schabi.newpipe.extractor.NewPipe", false, cl)
                }
                // nama file jar bawaan yang diganti (dibuang dari classpath oleh Peluncur)
                val buang = ganti.mapNotNull { lib[it]?.second } +
                    lib.filterKeys { it.startsWith("com.github.teamnewpipe") }.values.map { it.second }
                File(folder, "aktif.json").writeText(
                    JSONObject()
                        .put("versi", versi)
                        .put("bawaan", Versi.NEWPIPE)
                        .put("folder", tujuan.name)
                        .put("jar", JSONArray(jar))
                        .put("buang", JSONArray(buang.distinct()))
                        .toString(2),
                )
                File(folder, "gagal.json").delete()
                _status.value = Status.PerluMulaiUlang(versi)
            } catch (e: Exception) {
                _status.value = Status.Galat("Gagal memasang mesin $versi: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Kembali ke mesin bawaan aplikasi. */
    fun kembaliKeBawaan() {
        File(folder, "aktif.json").delete()
        _status.value = Status.PerluMulaiUlang(Versi.NEWPIPE)
    }

    private fun ketergantungan(pom: String, versiInduk: String): List<Modul> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder().parse(pom.byteInputStream())
        val akar = doc.documentElement
        val sifat = HashMap<String, String>()
        sifat["project.version"] = versiInduk
        (akar.getElementsByTagName("properties").item(0) as? Element)?.childNodes?.let { n ->
            for (i in 0 until n.length) (n.item(i) as? Element)?.let { sifat[it.tagName] = it.textContent.trim() }
        }
        fun isi(t: String?): String? {
            if (t == null) return null
            var s = t.trim()
            Regex("\\$\\{([^}]+)}").findAll(s).forEach { m -> sifat[m.groupValues[1]]?.let { s = s.replace(m.value, it) } }
            return if (s.contains("\${")) null else s
        }
        val hasil = ArrayList<Modul>()
        val daftar = akar.getElementsByTagName("dependency")
        for (i in 0 until daftar.length) {
            val d = daftar.item(i) as Element
            if ((d.parentNode?.parentNode as? Element)?.tagName == "dependencyManagement") continue
            fun anak(nama: String) = d.getElementsByTagName(nama).item(0)?.textContent?.trim()
            val lingkup = anak("scope") ?: "compile"
            if (lingkup !in setOf("compile", "runtime") || anak("optional") == "true") continue
            val g = isi(anak("groupId")) ?: continue
            val a = isi(anak("artifactId")) ?: continue
            val v = isi(anak("version")) ?: continue
            hasil += Modul(g, a, v)
        }
        return hasil
    }

    private fun ambilTeks(url: String): String? = ambilBytes(url)?.toString(Charsets.UTF_8)

    private fun ambilBytes(url: String): ByteArray? {
        val klien = Jaringan.api.newBuilder().readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", "RuangPutar/${Versi.NAMA}").build()
        klien.newCall(req).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code} ($url)")
            return r.body?.bytes()
        }
    }
}
