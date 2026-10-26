package id.ruangputar.inti

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import id.ruangputar.App
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Cache lagu + server kecil di 127.0.0.1 untuk mpv.
 *
 * mpv memutar http://127.0.0.1:PORT/lagu/ID. Lagu diunduh berurutan per 1 MB ke folder cache
 * (lewat batas kecepatan bersama), dan mpv membaca dari file itu sambil jalan.
 * Lagu yang sudah lengkap bisa diputar ulang tanpa internet, dan "Unduh" tinggal menyalin.
 */
object Proksi {
    private const val POTONG = 1L shl 20

    private lateinit var server: HttpServer
    var port = 0
        private set

    private val entri = ConcurrentHashMap<String, Entri>()

    fun mulai() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16)
        server.executor = Executors.newCachedThreadPool { r -> Thread(r, "proksi").apply { isDaemon = true } }
        server.createContext("/lagu/") { ex -> layani(ex) }
        server.start()
        port = server.address.port
        Thread({ runCatching { rapikan(-1) } }, "rapikan-cache").apply { isDaemon = true }.start()
    }

    fun alamat(id: String) = "http://127.0.0.1:$port/lagu/$id"

    /** File cache yang sudah lengkap untuk lagu ini (null kalau belum). */
    fun fileLengkap(id: String): File? = muatEntri(id).takeIf { it.selesai && it.file.exists() }?.file

    /** Ekstensi file cache (m4a / webm). */
    fun ekstensi(id: String): String = muatEntri(id).ext

    /** Lagu yang sedang diputar tidak dihapus saat cache dirapikan. */
    @Volatile
    var sedangDiputar: String = ""

    // ------------------------------------------------------------------ entri cache
    private class Entri(val id: String) {
        val kunci = Object()
        val meta = File(App.folderCache, "$id.json")
        var ext = "m4a"
        var mime = "audio/mp4"
        var total = -1L
        var tersedia = 0L
        var selesai = false
        var galat: String? = null
        var url: String? = null
        @Volatile var mengunduh = false
        val file: File get() = File(App.folderCache, "$id.$ext")

        fun simpanMeta() {
            runCatching {
                meta.writeText(JSONObject().put("ext", ext).put("mime", mime).put("total", total)
                    .put("tersedia", tersedia).put("selesai", selesai).toString())
            }
        }
    }

    private fun muatEntri(id: String): Entri = entri.getOrPut(id) {
        Entri(id).apply {
            runCatching {
                if (meta.exists()) {
                    val o = JSONObject(meta.readText())
                    ext = o.optString("ext", "m4a")
                    mime = o.optString("mime", "audio/mp4")
                    total = o.optLong("total", -1)
                    selesai = o.optBoolean("selesai")
                    tersedia = if (file.exists()) minOf(o.optLong("tersedia"), file.length()) else 0
                    if (selesai && file.length() != total) {
                        selesai = false
                        tersedia = minOf(tersedia, file.length())
                    }
                }
            }
        }
    }

    /** Mulai (atau lanjutkan) mengunduh lagu ke cache. */
    fun siapkan(id: String) {
        val e = muatEntri(id)
        synchronized(e.kunci) {
            if (e.selesai || e.mengunduh) return
            e.mengunduh = true
            e.galat = null
        }
        Thread({ unduh(e) }, "cache-$id").apply { isDaemon = true }.start()
    }

    private fun unduh(e: Entri) {
        var cobaUlang = 0
        try {
            while (true) {
                try {
                    if (e.url == null) {
                        val s = runBlocking { YouTube.suara(e.id, hemat = App.data.pengaturan.value.kualitas == "hemat", untukUnduh = true) }
                        synchronized(e.kunci) {
                            if (e.tersedia == 0L) {
                                e.ext = s.ext
                                e.mime = s.mime
                            } else if (e.ext != s.ext) {
                                // format berbeda dari sisa unduhan sebelumnya: mulai dari awal
                                e.file.delete()
                                e.tersedia = 0
                                e.total = -1
                                e.ext = s.ext
                                e.mime = s.mime
                            }
                            e.url = s.url
                        }
                    }
                    tarik(e)
                    synchronized(e.kunci) {
                        e.selesai = true
                        e.simpanMeta()
                        e.kunci.notifyAll()
                    }
                    rapikan(App.data.pengaturan.value.cacheMb.toLong() * 1024 * 1024)
                    return
                } catch (x: Ditolak) {
                    // alamat kedaluwarsa / ditolak (403): minta alamat baru, lanjut dari posisi terakhir
                    if (++cobaUlang > 2) throw IllegalStateException("YouTube menolak alamat lagu (HTTP ${x.kode})")
                    YouTube.lupakan(e.id)
                    e.url = null
                }
            }
        } catch (x: Exception) {
            synchronized(e.kunci) {
                e.galat = if (YouTube.adalahGalatBot(x)) "YouTube sedang menahan permintaan (dikira bot)" else (x.message ?: x.javaClass.simpleName)
                e.simpanMeta()
                e.kunci.notifyAll()
            }
        } finally {
            e.mengunduh = false
        }
    }

    private class Ditolak(val kode: Int) : Exception("HTTP $kode")

    private fun tarik(e: Entri) {
        val url = e.url ?: return
        if (e.total < 0) e.total = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: -1
        RandomAccessFile(e.file, "rw").use { raf ->
            while (e.total < 0 || e.tersedia < e.total) {
                val awal = e.tersedia
                val akhir = if (e.total > 0) minOf(awal + POTONG, e.total) - 1 else awal + POTONG - 1
                val req = okhttp3.Request.Builder().url(url).header("Range", "bytes=$awal-$akhir")
                    .header("User-Agent", Jaringan.AGEN).build()
                // potongan pertama tanpa batas kecepatan: lagu langsung mulai
                val klien = if (awal == 0L) Jaringan.api else Jaringan.media
                klien.newCall(req).execute().use { r ->
                    if (r.code == 403 || r.code == 410) throw Ditolak(r.code)
                    if (r.code !in 200..299) throw IOException("HTTP ${r.code}")
                    if (e.total < 0) {
                        e.total = r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                            ?: r.body?.contentLength()?.takeIf { it > 0 && r.code == 200 } ?: throw IOException("Ukuran lagu tidak diketahui")
                    }
                    val masuk = r.body?.byteStream() ?: throw IOException("Tidak ada data")
                    raf.seek(awal)
                    val buf = ByteArray(64 * 1024)
                    var posisi = awal
                    masuk.use {
                        while (true) {
                            val n = it.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            posisi += n
                            synchronized(e.kunci) {
                                e.tersedia = posisi
                                e.kunci.notifyAll()
                            }
                        }
                    }
                    if (posisi == awal) throw IOException("Unduhan terputus")
                }
                e.simpanMeta()
            }
        }
    }

    /** Alamat stream lagu ini kedaluwarsa: ambil yang baru saat dibutuhkan lagi. */
    fun lupakanAlamat(id: String) {
        entri[id]?.let { synchronized(it.kunci) { it.url = null; it.galat = null } }
    }

    /**
     * Pastikan lagu lengkap di cache (dipakai fitur Unduh), lalu kembalikan file-nya.
     * [kemajuan] menerima persen 0–100.
     */
    fun tungguLengkap(id: String, kemajuan: (Int) -> Unit): File {
        siapkan(id)
        val e = muatEntri(id)
        synchronized(e.kunci) {
            while (!e.selesai) {
                if (!e.mengunduh) {
                    e.galat?.let { throw IOException(it) }
                    siapkan(id)
                }
                e.kunci.wait(500)
                if (e.total > 0) kemajuan(((e.tersedia * 100) / e.total).toInt().coerceIn(0, 100))
            }
        }
        return e.file
    }

    // ------------------------------------------------------------------ server untuk mpv
    private fun layani(ex: HttpExchange) {
        try {
            val id = ex.requestURI.path.removePrefix("/lagu/").substringBefore('.').substringBefore('/')
            if (id.isEmpty()) return balas(ex, 404)
            val e = muatEntri(id)
            if (!e.selesai) siapkan(id)
            // tunggu ukuran lagu diketahui
            synchronized(e.kunci) {
                val batas = System.currentTimeMillis() + 45_000
                while (e.total < 0 && e.galat == null && System.currentTimeMillis() < batas) e.kunci.wait(500)
                if (e.total < 0) {
                    ex.responseHeaders.add("X-Galat", e.galat ?: "waktu habis")
                    return balas(ex, 502)
                }
            }
            val total = e.total
            val (awal, akhir) = rentang(ex.requestHeaders.getFirst("Range"), total)
            if (awal >= total) {
                ex.responseHeaders.add("Content-Range", "bytes */$total")
                return balas(ex, 416)
            }
            val panjang = akhir - awal + 1
            ex.responseHeaders.add("Content-Type", e.mime)
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            val adaRange = ex.requestHeaders.getFirst("Range") != null
            if (adaRange) ex.responseHeaders.add("Content-Range", "bytes $awal-$akhir/$total")
            ex.sendResponseHeaders(if (adaRange) 206 else 200, panjang)
            ex.responseBody.use { keluar ->
                RandomAccessFile(e.file.also { it.parentFile.mkdirs(); if (!it.exists()) it.createNewFile() }, "r").use { raf ->
                    var pos = awal
                    val buf = ByteArray(64 * 1024)
                    while (pos <= akhir) {
                        val bisa: Long
                        synchronized(e.kunci) {
                            val batas = System.currentTimeMillis() + 60_000
                            while (e.tersedia <= pos && e.galat == null && System.currentTimeMillis() < batas) {
                                if (!e.mengunduh && !e.selesai) siapkan(id)
                                e.kunci.wait(500)
                            }
                            bisa = e.tersedia
                        }
                        if (bisa <= pos) return             // galat / waktu habis: hentikan aliran
                        raf.seek(pos)
                        val n = raf.read(buf, 0, minOf(buf.size.toLong(), bisa - pos, akhir - pos + 1).toInt())
                        if (n <= 0) return
                        keluar.write(buf, 0, n)
                        pos += n
                    }
                }
            }
            e.file.setLastModified(System.currentTimeMillis())
        } catch (_: IOException) {
            // mpv menutup sambungan (geser / ganti lagu): biasa
        } catch (_: InterruptedException) {
        } finally {
            ex.close()
        }
    }

    private fun rentang(range: String?, total: Long): Pair<Long, Long> {
        val m = range?.let { Regex("bytes=(\\d*)-(\\d*)").find(it) } ?: return 0L to total - 1
        val a = m.groupValues[1]
        val b = m.groupValues[2]
        return when {
            a.isEmpty() && b.isNotEmpty() -> maxOf(0, total - b.toLong()) to total - 1      // akhiran: N byte terakhir
            else -> (a.toLongOrNull() ?: 0L) to minOf(b.toLongOrNull() ?: (total - 1), total - 1)
        }
    }

    private fun balas(ex: HttpExchange, kode: Int) {
        ex.sendResponseHeaders(kode, -1)
    }

    // ------------------------------------------------------------------ rapikan / kosongkan
    /** Hapus lagu terlama sampai ukuran cache di bawah batas (batas < 0 = baca dari pengaturan). */
    @Synchronized
    fun rapikan(batas: Long) {
        val maks = if (batas < 0) App.data.pengaturan.value.cacheMb.toLong() * 1024 * 1024 else batas
        val file = App.folderCache.listFiles { f -> f.isFile && !f.name.endsWith(".json") } ?: return
        var ukuran = file.sumOf { it.length() }
        for (f in file.sortedBy { it.lastModified() }) {
            if (ukuran <= maks) break
            val id = f.nameWithoutExtension
            if (id == sedangDiputar || entri[id]?.mengunduh == true) continue
            ukuran -= f.length()
            f.delete()
            File(App.folderCache, "$id.json").delete()
            entri.remove(id)
        }
    }

    fun ukuranCache(): Long = App.folderCache.listFiles()?.sumOf { it.length() } ?: 0L

    fun kosongkan() {
        App.folderCache.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension
            if (id != sedangDiputar && entri[id]?.mengunduh != true) {
                f.delete()
                entri.remove(id)
            }
        }
    }
}
