package id.ruangputar.inti

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import id.ruangputar.App
import java.io.File

/** Fungsi libmpv yang dipakai (lihat mpv/client.h). */
@Suppress("FunctionName")
interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer?
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_error_string(error: Int): String
}

private interface LibC : Library {
    fun setlocale(category: Int, locale: String): String?
}

/**
 * Mesin pemutar suara: libmpv (memutar M4A/Opus dari YouTube, MP3/FLAC/OGG di folder Offline).
 * Windows: libmpv-2.dll ikut di folder aplikasi. Linux: libmpv dari sistem (paket mpv / libmpv2).
 */
class Mpv private constructor(private val lib: LibMpv, private val ctx: Pointer) {

    /** Kejadian dari mpv. */
    sealed class Kejadian {
        data object LaguSiap : Kejadian()
        /** alasan: 0 = habis (EOF), 2 = dihentikan, 4 = galat. */
        data class LaguSelesai(val alasan: Int, val galat: Int) : Kejadian()
        data object Mati : Kejadian()
    }

    @Volatile
    private var hidup = true

    fun perintah(vararg arg: String): Int = if (hidup) lib.mpv_command(ctx, arrayOf(*arg, null)) else -1

    fun atur(nama: String, nilai: String): Int = if (hidup) lib.mpv_set_property_string(ctx, nama, nilai) else -1

    fun baca(nama: String): String? {
        if (!hidup) return null
        val p = lib.mpv_get_property_string(ctx, nama) ?: return null
        return try {
            p.getString(0, "UTF-8")
        } finally {
            lib.mpv_free(p)
        }
    }

    fun bacaAngka(nama: String): Double? = baca(nama)?.toDoubleOrNull()

    fun pesanGalat(kode: Int): String = runCatching { lib.mpv_error_string(kode) }.getOrDefault("galat $kode")

    /** Menunggu kejadian berikutnya (dipanggil dari satu thread khusus). */
    fun tungguKejadian(detik: Double): Kejadian? {
        if (!hidup) return Kejadian.Mati
        val ev = lib.mpv_wait_event(ctx, detik) ?: return null
        return when (ev.getInt(0)) {
            1 -> Kejadian.Mati                                     // MPV_EVENT_SHUTDOWN
            8 -> Kejadian.LaguSiap                                 // MPV_EVENT_FILE_LOADED
            7 -> {                                                 // MPV_EVENT_END_FILE
                val data = ev.getPointer(16)
                if (data == null) Kejadian.LaguSelesai(0, 0) else Kejadian.LaguSelesai(data.getInt(0), data.getInt(4))
            }
            else -> null
        }
    }

    fun tutup() {
        if (!hidup) return
        hidup = false
        runCatching { lib.mpv_wakeup(ctx) }
        runCatching { lib.mpv_terminate_destroy(ctx) }
    }

    companion object {
        /** Membuat mpv; null kalau libmpv tidak ditemukan (pesan galat di [galatMuat]). */
        var galatMuat: String = ""
            private set

        fun buat(): Mpv? {
            val lib = muatLibrary() ?: return null
            if (!App.windows) {
                // libmpv menolak berjalan kalau LC_NUMERIC bukan "C"
                runCatching { Native.load("c", LibC::class.java).setlocale(1, "C") }
            }
            val ctx = lib.mpv_create() ?: run {
                galatMuat = "mpv_create gagal"
                return null
            }
            val opsi = mapOf(
                "vid" to "no",
                "video" to "no",
                "audio-display" to "no",
                "input-default-bindings" to "no",
                "input-vo-keyboard" to "no",
                "osc" to "no",
                "terminal" to "no",
                "idle" to "yes",
                "keep-open" to "no",
                "ytdl" to "no",
                "cache" to "yes",
                "demuxer-max-bytes" to "32MiB",
                "network-timeout" to "30",
                "volume-max" to "100",
                "audio-client-name" to "Ruang Putar",
                "title" to "Ruang Putar",
            )
            opsi.forEach { (k, v) -> lib.mpv_set_option_string(ctx, k, v) }
            val r = lib.mpv_initialize(ctx)
            if (r < 0) {
                galatMuat = "mpv_initialize: ${lib.mpv_error_string(r)}"
                lib.mpv_terminate_destroy(ctx)
                return null
            }
            return Mpv(lib, ctx)
        }

        private fun muatLibrary(): LibMpv? {
            val opsi = mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")
            val calon = ArrayList<String>()
            val folderSumber = System.getProperty("compose.application.resources.dir")
            listOfNotNull(folderSumber, File(System.getProperty("user.dir"), "sumber/windows").path, System.getProperty("user.dir"))
                .forEach { d ->
                    listOf("libmpv-2.dll", "mpv-2.dll", "mpv-1.dll").forEach { n ->
                        val f = File(d, n)
                        if (f.isFile) calon.add(f.absolutePath)
                    }
                }
            if (App.windows) calon += listOf("libmpv-2", "mpv-2", "mpv-1") else calon += listOf("mpv", "libmpv.so.2", "libmpv.so.1")
            val galat = StringBuilder()
            for (c in calon) {
                try {
                    return Native.load(c, LibMpv::class.java, opsi)
                } catch (e: Throwable) {
                    galat.append(c).append(": ").append(e.message?.lineSequence()?.firstOrNull()).append('\n')
                }
            }
            galatMuat = if (App.windows) {
                "File libmpv-2.dll tidak ditemukan di folder aplikasi. Pasang ulang Ruang Putar.\n$galat"
            } else {
                "libmpv tidak ditemukan. Pasang paket mpv (mis. sudo dnf install mpv-libs / sudo apt install libmpv2).\n$galat"
            }
            return null
        }
    }
}
