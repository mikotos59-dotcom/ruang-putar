package id.ruangputar.inti

import id.ruangputar.App
import id.ruangputar.Versi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/** Catatan kecil (waktu cek terakhir, versi yang dilewati, …) di %APPDATA%\RuangPutar\musik-status.properties. */
object Catatan {
    private val file get() = File(App.folderData, "musik-status.properties")
    private val p = Properties()
    private var dimuat = false

    @Synchronized
    private fun muat() {
        if (dimuat) return
        dimuat = true
        runCatching { file.inputStream().use { p.load(it) } }
    }

    @Synchronized
    fun ambil(k: String, bawaan: String = ""): String {
        muat()
        return p.getProperty(k, bawaan)
    }

    @Synchronized
    fun simpan(k: String, v: String) {
        muat()
        p.setProperty(k, v)
        runCatching { file.outputStream().use { p.store(it, "Ruang Putar") } }
    }

    fun angka(k: String): Long = ambil(k, "0").toLongOrNull() ?: 0L
}

/**
 * Pembaruan aplikasi lewat GitHub Releases.
 *  - GitHub Actions membuat Release bertag "b<kode>" berisi installer + versi.json.
 *  - Aplikasi mengecek: saat dibuka (paling sering sekali sehari), saat sering gagal, atau lewat tombol.
 *  - Ada versi baru -> tawarkan -> unduh installer -> pasang diam-diam -> Ruang Putar dibuka lagi.
 */
object Pembaruan {
    sealed class Status {
        data object Diam : Status()
        data object Mengecek : Status()
        data object Terbaru : Status()
        data class Tersedia(
            val nama: String, val kode: Int, val url: String, val catatan: String, val ukuran: Long, val npe: String,
        ) : Status()
        data class Mengunduh(val info: Tersedia, val persen: Int) : Status()
        data class Siap(val info: Tersedia, val file: File) : Status()
        data class Galat(val pesan: String) : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Diam)
    val status: StateFlow<Status> = _status

    /** true = jendela tawaran sedang ditampilkan. */
    val tawarkan = MutableStateFlow(false)

    /** true = tawaran muncul karena aplikasi sering gagal memutar/mencari. */
    val karenaMasalah = MutableStateFlow(false)

    private const val SEHARI = 24 * 3600_000L
    private const val SEJAM = 3600_000L
    private var tugas: Job? = null
    private val gagalBeruntun = AtomicInteger(0)

    private fun folder() = File(App.folderCache.parentFile, "pembaruan").apply { mkdirs() }

    /** Saat aplikasi dibuka: cek paling sering sekali sehari. Sisa installer lama dibersihkan. */
    fun cekOtomatis() {
        if (Catatan.ambil("kode_terpasang") != Versi.KODE.toString()) {
            folder().listFiles()?.forEach { it.delete() }
            Catatan.simpan("kode_terpasang", Versi.KODE.toString())
        }
        if (System.currentTimeMillis() - Catatan.angka("terakhir_cek") < SEHARI) return
        cek(manual = false)
    }

    fun catatBerhasil() = gagalBeruntun.set(0)

    fun catatGagal() {
        if (gagalBeruntun.incrementAndGet() < 3) return
        gagalBeruntun.set(0)
        if (System.currentTimeMillis() - Catatan.angka("terakhir_cek_masalah") < SEJAM) return
        Catatan.simpan("terakhir_cek_masalah", System.currentTimeMillis().toString())
        karenaMasalah.value = true
        cek(manual = false)
        Mesin.cek(manual = false)
    }

    fun cek(manual: Boolean) {
        if (tugas?.isActive == true) return
        tugas = App.scope.launch(Dispatchers.IO) {
            val sebelum = _status.value
            if (sebelum is Status.Mengunduh) return@launch
            if (manual) _status.value = Status.Mengecek
            try {
                val info = ambilRilisTerbaru()
                Catatan.simpan("terakhir_cek", System.currentTimeMillis().toString())
                if (info != null && info.kode > Versi.KODE) {
                    _status.value = info
                    val dilewati = Catatan.ambil("dilewati").toIntOrNull() ?: 0
                    if (manual || karenaMasalah.value || info.kode != dilewati) tawarkan.value = true
                } else {
                    _status.value = if (manual) Status.Terbaru else Status.Diam
                    karenaMasalah.value = false
                }
            } catch (e: Exception) {
                _status.value = if (manual) Status.Galat("Gagal mengecek pembaruan: ${e.message ?: e.javaClass.simpleName}") else sebelum
            }
        }
    }

    /** Rilis terbaru dari versi.json di GitHub Releases (tanpa API GitHub, tidak kena batas 60 cek/jam). */
    private fun ambilRilisTerbaru(): Status.Tersedia? {
        val repo = Versi.REPO_GITHUB
        val req = okhttp3.Request.Builder()
            .url("https://github.com/$repo/releases/latest/download/versi.json")
            .header("User-Agent", "RuangPutar/${Versi.NAMA}")
            .build()
        val teks = Jaringan.api.newCall(req).execute().use { r ->
            when {
                r.code == 404 -> return null
                r.code == 403 || r.code == 429 -> throw IllegalStateException("GitHub sedang membatasi pengecekan. Coba lagi nanti.")
                !r.isSuccessful -> throw IllegalStateException("HTTP ${r.code}")
                else -> r.body?.string() ?: return null
            }
        }
        val o = JSONObject(teks)
        val kode = o.optInt("kode", -1).takeIf { it > 0 } ?: return null
        val file = o.optString("file").ifEmpty { return null }
        return Status.Tersedia(
            nama = o.optString("nama").ifEmpty { "build $kode" },
            kode = kode,
            url = "https://github.com/$repo/releases/download/b$kode/$file",
            catatan = o.optString("catatan"),
            ukuran = o.optLong("ukuran"),
            npe = o.optString("npe"),
        )
    }

    /** Tombol "Nanti": jangan tawarkan versi ini lagi secara otomatis. */
    fun nanti() {
        (_status.value as? Status.Tersedia)?.let { Catatan.simpan("dilewati", it.kode.toString()) }
        tawarkan.value = false
        karenaMasalah.value = false
    }

    fun perbarui() {
        when (val s = _status.value) {
            is Status.Siap -> pasang(s.file)
            is Status.Tersedia -> {
                tawarkan.value = true
                tugas = App.scope.launch(Dispatchers.IO) {
                    try {
                        val f = unduh(s)
                        _status.value = Status.Siap(s, f)
                        pasang(f)
                    } catch (e: Exception) {
                        _status.value = Status.Galat("Gagal mengunduh pembaruan: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            }
            else -> Unit
        }
    }

    private fun unduh(info: Status.Tersedia): File {
        val nama = info.url.substringAfterLast('/')
        val tujuan = File(folder(), nama)
        if (tujuan.exists() && info.ukuran > 0 && tujuan.length() == info.ukuran) return tujuan
        val sementara = File(folder(), "$nama.part")
        _status.value = Status.Mengunduh(info, 0)
        val req = okhttp3.Request.Builder().url(info.url).header("User-Agent", "RuangPutar/${Versi.NAMA}").build()
        Jaringan.api.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            val badan = r.body ?: throw IllegalStateException("Tidak ada data")
            val total = badan.contentLength().takeIf { it > 0 } ?: info.ukuran
            badan.byteStream().use { masuk ->
                sementara.outputStream().use { keluar ->
                    val buf = ByteArray(256 * 1024)
                    var dapat = 0L
                    while (true) {
                        val n = masuk.read(buf)
                        if (n < 0) break
                        keluar.write(buf, 0, n)
                        dapat += n
                        if (total > 0) _status.value = Status.Mengunduh(info, ((dapat * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        }
        if (info.ukuran > 0 && sementara.length() != info.ukuran) {
            sementara.delete()
            throw IllegalStateException("Unduhan tidak lengkap")
        }
        tujuan.delete()
        sementara.renameTo(tujuan)
        return tujuan
    }

    /** Jalankan installer secara diam; installer menutup Ruang Putar lalu membukanya lagi. */
    private fun pasang(file: File) {
        if (!App.windows) {
            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://github.com/${Versi.REPO_GITHUB}/releases/latest")) }
            return
        }
        try {
            App.data.simpanSekarang()
            ProcessBuilder(file.absolutePath, "/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CLOSEAPPLICATIONS")
                .directory(file.parentFile)
                .start()
            App.scope.launch {
                delay(1500)
                exitProcess(0)
            }
        } catch (e: Exception) {
            _status.value = Status.Galat("Tidak bisa menjalankan installer: ${e.message}")
        }
    }
}
