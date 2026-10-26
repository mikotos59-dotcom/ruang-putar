package id.ruangputar.inti

import id.ruangputar.App
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Impor data dari Ruang Putar versi lama (mesin yt-dlp): %APPDATA%\RuangPutar\data.json.
 * Favorit, riwayat, rak Beranda, penyanyi yang diikuti, hitungan putar, daftar unduhan, dan
 * sebagian pengaturan. File lama tidak diubah.
 */
object Impor {
    private val AUDIO = setOf("mp3", "m4a", "aac", "opus", "ogg", "oga", "flac", "wav", "webm", "wma", "mka")
    private val RAK_VIDEO = setOf("mv", "live", "trailer")

    val fileLama: File get() = File(App.folderData, "data.json")

    fun ada(): Boolean = fileLama.isFile

    /** Kembalikan ringkasan hasil (untuk ditampilkan), atau null kalau tidak ada data lama. */
    fun jalankan(): String? {
        if (!ada()) return null
        val d = JSONObject(fileLama.readText(Charsets.UTF_8))
        val ruangDipakai = ArrayList<JSONObject>()
        val ruang = d.optJSONObject("ruang")
        if (ruang != null) {
            if (d.optBoolean("terhubung", true)) {
                ruang.optJSONObject("bersama")?.let { ruangDipakai += it }
            } else {
                listOf("musik", "video").forEach { k -> ruang.optJSONObject(k)?.let { ruangDipakai += it } }
            }
        } else {
            ruangDipakai += d                           // format lama: daftar langsung di akar
        }

        val favorit = ArrayList<Lagu>()
        val riwayat = ArrayList<Lagu>()
        val rak = ArrayList<Rak>()
        val penyanyi = ArrayList<Penyanyi>()
        for (r in ruangDipakai) {
            favorit += r.optJSONArray("favorit").laguLama()
            riwayat += r.optJSONArray("riwayat").laguLama()
            val daftarRak = r.optJSONArray("rak")
            if (daftarRak != null) {
                for (i in 0 until daftarRak.length()) {
                    val o = daftarRak.optJSONObject(i) ?: continue
                    val kueri = o.optString("kueri").trim()
                    if (kueri.isEmpty() || kueri.startsWith("http") || o.optString("id") in RAK_VIDEO) continue
                    rak += Rak(
                        id = "i" + o.optString("id").ifEmpty { System.nanoTime().toString() },
                        nama = o.optString("nama").ifBlank { kueri.take(30) },
                        kueri = kueri,
                        maksMenit = if (o.has("maks_menit")) o.optInt("maks_menit") else if (o.optBoolean("panjang")) 0 else 10,
                        minDetik = o.optInt("min_detik", -1),
                    )
                }
            }
            val daftarPenyanyi = r.optJSONArray("penyanyi")
            if (daftarPenyanyi != null) {
                for (i in 0 until daftarPenyanyi.length()) {
                    val o = daftarPenyanyi.optJSONObject(i) ?: continue
                    val url = o.optString("url")
                    if (url.contains("youtube.com")) penyanyi += Penyanyi(o.optString("nama").ifBlank { "Penyanyi" }, url)
                }
            }
        }
        App.data.gabungImpor(favorit, riwayat, rak, penyanyi)

        // hitungan putar -> "Sering kamu putar"
        var nHitungan = 0
        d.optJSONObject("hitungan")?.let { h ->
            h.keys().forEach { k ->
                val o = h.optJSONObject(k) ?: return@forEach
                val lagu = o.optJSONObject("lagu")?.let { laguLama(it) } ?: return@forEach
                if (lagu.lokal || lagu.id.isEmpty()) return@forEach
                App.data.tambahHitungan(lagu.id, o.optInt("n"), (o.optDouble("terakhir", 0.0) * 1000).toLong(), lagu)
                nHitungan++
            }
        }

        // daftar unduhan (supaya lagu yang sudah diunduh langsung diputar dari file)
        var nUnduhan = 0
        d.optJSONObject("unduhan")?.let { u ->
            u.keys().forEach { url ->
                val id = YouTube.idDari(url) ?: return@forEach
                val daftar = u.optJSONArray(url) ?: return@forEach
                for (i in 0 until daftar.length()) {
                    val f = File(daftar.optJSONObject(i)?.optString("file") ?: continue)
                    if (f.isFile && f.extension.lowercase() in AUDIO) {
                        App.data.catatUnduhan(id, f.path)
                        nUnduhan++
                        break
                    }
                }
            }
        }

        // pengaturan yang masih berlaku
        val p = d.optJSONObject("pengaturan")
        if (p != null) {
            App.data.ubahPengaturan { s ->
                s.copy(
                    tema = p.optString("tema", s.tema).takeIf { it in setOf("gelap", "terang", "batik", "otomatis") } ?: s.tema,
                    volume = p.optInt("volume", s.volume).coerceIn(0, 100),
                    folderMusik = p.optString("folder_musik").takeIf { it.isNotBlank() && File(it).parentFile?.isDirectory == true } ?: s.folderMusik,
                    minDetik = p.optInt("min_detik", s.minDetik),
                    kecepatan = when (p.optString("kecepatan_unduh")) {
                        "rendah" -> "rendah"
                        "tanpa" -> "tanpa"
                        else -> "normal"
                    },
                    cacheMb = p.optInt("batas_cache_mb", s.cacheMb).coerceIn(256, 4096),
                )
            }
        }
        App.data.ubahPengaturan { it.copy(sudahImpor = true) }
        App.data.simpanSekarang()
        return "Diimpor dari Ruang Putar lama: ${favorit.distinctBy { it.kunci }.size} favorit, " +
            "${riwayat.distinctBy { it.kunci }.size} riwayat, ${rak.size} rak, ${penyanyi.size} penyanyi, " +
            "$nHitungan lagu sering diputar, $nUnduhan unduhan."
    }

    private fun JSONArray?.laguLama(): List<Lagu> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let { laguLama(it) } }
    }

    /** Lagu format lama {url, judul, artis, durasi, thumbnail, file, channel_url, jenis} -> Lagu baru. */
    private fun laguLama(o: JSONObject): Lagu? {
        val file = o.optString("file")
        val judul = o.optString("judul", "Tanpa judul")
        if (file.isNotEmpty()) {
            val f = File(file)
            if (f.extension.lowercase() !in AUDIO) return null          // video lokal: dilewati
            return Lagu(id = "", judul = judul, artis = o.optString("artis"), durasi = o.optLong("durasi"), uri = f.path)
        }
        val id = YouTube.idDari(o.optString("url")) ?: return null      // hanya YouTube
        return Lagu(
            id = id,
            judul = judul,
            artis = o.optString("artis"),
            durasi = o.optLong("durasi"),
            sampul = o.optString("thumbnail").takeIf { it.startsWith("http") } ?: "https://i.ytimg.com/vi/$id/mqdefault.jpg",
            channel = o.optString("channel_url"),
        )
    }
}
