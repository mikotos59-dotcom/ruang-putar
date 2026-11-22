package id.ruangputar.inti

import id.ruangputar.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Unduh lagu ke folder Offline (bawaan: Music\RuangPutar) — suara asli YouTube (M4A bila ada),
 * tanpa ubah format, jadi tidak butuh ffmpeg. Lagu diambil lewat cache: yang sudah diputar penuh
 * tinggal disalin.
 */
object Unduhan {
    private val _proses = MutableStateFlow<Map<String, Int>>(emptyMap())   // id -> persen

    val proses: StateFlow<Map<String, Int>> = _proses

    private val EKSTENSI = setOf("mp3", "m4a", "aac", "opus", "ogg", "oga", "flac", "wav", "webm", "wma", "mka")

    fun folder(): File = File(App.data.pengaturan.value.folderMusik)

    /** Sudah ada file unduhannya di PC? */
    fun sudahDiunduh(id: String): Boolean = id.isNotEmpty() && App.data.unduhan.value[id]?.let { File(it).isFile } == true

    /**
     * Unduh satu lagu sekarang juga (dipanggil oleh [AntreanUnduh], yang mengatur jeda & antrean).
     * Mengembalikan path file. Galat dilempar ke pemanggil.
     */
    suspend fun unduhSekarang(lagu: Lagu): String = withContext(Dispatchers.IO) {
        App.data.unduhan.value[lagu.id]?.let { if (File(it).isFile) return@withContext it }
        _proses.value = _proses.value + (lagu.id to 0)
        try {
            val cache = Proksi.tungguLengkap(lagu.id) { p -> _proses.value = _proses.value + (lagu.id to p) }
            val dir = folder().apply { mkdirs() }
            val nama = aman("${lagu.artis.ifBlank { "Tanpa artis" }} - ${lagu.judul}")
            var tujuan = File(dir, "$nama.${cache.extension}")
            var n = 2
            while (tujuan.exists()) tujuan = File(dir, "$nama ($n).${cache.extension}").also { n++ }
            cache.copyTo(tujuan)
            simpanSampul(lagu)
            App.data.catatUnduhan(lagu.id, tujuan.path)
            tujuan.path
        } finally {
            _proses.value = _proses.value - lagu.id
        }
    }

    /** Hapus file unduhan sebuah lagu (dan sampulnya). Lagu tetap bisa diputar online. */
    fun hapusFile(id: String): Boolean {
        val path = App.data.unduhan.value[id] ?: return false
        val ok = runCatching { File(path).delete() }.getOrDefault(false)
        runCatching { fileSampul(id).delete() }
        App.data.lupakanUnduhan(id)
        return ok
    }

    private fun aman(nama: String) = nama.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(120)

    // ------------------------------------------------------------------ sampul
    fun fileSampul(id: String) = File(File(App.folderData, "sampul"), "$id.jpg")

    private fun simpanSampul(lagu: Lagu) {
        if (lagu.id.isEmpty()) return
        val calon = listOf(
            "https://i.ytimg.com/vi/${lagu.id}/hq720.jpg",
            "https://i.ytimg.com/vi/${lagu.id}/mqdefault.jpg",
            lagu.sampul,
        ).filter { it.startsWith("http") }
        for (url in calon) {
            try {
                val req = okhttp3.Request.Builder().url(url).header("User-Agent", Jaringan.AGEN).build()
                val bytes = Jaringan.api.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null } ?: continue
                val f = fileSampul(lagu.id)
                f.parentFile.mkdirs()
                f.writeBytes(bytes)
                return
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------ daftar Offline
    /** Semua file musik di folder Offline (sampai 2 tingkat sub-folder), terbaru dulu. */
    fun daftarOffline(): List<Lagu> {
        val keId = App.data.unduhan.value.entries.associate { (id, path) -> File(path).absolutePath.lowercase() to id }
        val hasil = ArrayList<Pair<Long, Lagu>>()
        fun telusuri(d: File, tingkat: Int) {
            d.listFiles()?.forEach { f ->
                if (f.isDirectory && tingkat < 2) telusuri(f, tingkat + 1)
                else if (f.isFile && f.extension.lowercase() in EKSTENSI) {
                    val id = keId[f.absolutePath.lowercase()] ?: ""
                    val dasar = f.nameWithoutExtension
                    val (artis, judul) = dasar.split(" - ", limit = 2).let { if (it.size == 2) it[0] to it[1] else "" to dasar }
                    val sampul = when {
                        id.isNotEmpty() && fileSampul(id).isFile -> fileSampul(id).toURI().toString()
                        id.isNotEmpty() -> "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                        else -> ""
                    }
                    hasil += f.lastModified() to Lagu(id = id, judul = judul, artis = artis, sampul = sampul, uri = f.absolutePath)
                }
            }
        }
        telusuri(folder(), 0)
        return hasil.sortedByDescending { it.first }.map { it.second }
    }
}
