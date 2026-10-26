package id.ruangputar.inti

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.ruangputar.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Antrean & kendali pemutaran (di atas mpv).
 * Semua status dibaca UI langsung (state Compose), diubah di thread utama.
 */
object Pemutar {
    private var mpv: Mpv? = null

    /** Pesan untuk ditampilkan di bawah (snackbar). */
    var kabar: (String) -> Unit = {}

    /** Dipanggil saat lagu berganti (notifikasi tray). */
    var saatGanti: (Lagu) -> Unit = {}

    var siap by mutableStateOf(false)
        private set
    var galatMesin by mutableStateOf<String?>(null)
        private set

    var laguKini by mutableStateOf<Lagu?>(null)
        private set
    var main by mutableStateOf(false)
        private set
    var memuat by mutableStateOf(false)
        private set
    var posisi by mutableDoubleStateOf(0.0)
        private set
    var durasi by mutableDoubleStateOf(0.0)
        private set
    var antrean by mutableStateOf<List<Lagu>>(emptyList())
        private set
    var indeks by mutableIntStateOf(-1)
        private set
    var acak by mutableStateOf(false)
        private set

    /** 0 = tidak ulang, 1 = ulang antrean, 2 = ulang satu lagu. */
    var ulang by mutableIntStateOf(0)
        private set
    var volume by mutableIntStateOf(70)
        private set
    var timerSampai by mutableLongStateOf(0L)
        private set

    private var tugasTimer: Job? = null
    private var posisiTertunda = 0.0
    private val sudahDicobaUlang = HashSet<String>()

    // ------------------------------------------------------------------ mulai / tutup
    fun mulai() {
        volume = App.data.pengaturan.value.volume
        val m = Mpv.buat()
        if (m == null) {
            galatMesin = Mpv.galatMuat
            return
        }
        mpv = m
        m.atur("volume", volume.toString())
        siap = true
        Thread({ putaranKejadian(m) }, "mpv-kejadian").apply { isDaemon = true }.start()
        App.scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(250)
                val mm = mpv ?: break
                val p = withContext(Dispatchers.IO) {
                    Triple(mm.bacaAngka("time-pos"), mm.bacaAngka("duration"), mm.baca("pause"))
                }
                p.first?.let { posisi = it }
                p.second?.let { if (it > 0) durasi = it }
                if (laguKini != null && !memuat) main = p.third == "no"
            }
        }
    }

    fun tutup() {
        mpv?.tutup()
        mpv = null
    }

    private fun putaranKejadian(m: Mpv) {
        while (true) {
            when (val k = m.tungguKejadian(1.0)) {
                Mpv.Kejadian.Mati -> return
                Mpv.Kejadian.LaguSiap -> App.scope.launch(Dispatchers.Main) {
                    memuat = false
                    main = true
                    val t = posisiTertunda
                    posisiTertunda = 0.0
                    if (t > 1) mpv?.perintah("seek", "%.2f".format(java.util.Locale.ROOT, t), "absolute")
                }
                is Mpv.Kejadian.LaguSelesai -> App.scope.launch(Dispatchers.Main) { laguSelesai(k.alasan, k.galat) }
                null -> Unit
            }
        }
    }

    private fun laguSelesai(alasan: Int, galat: Int) {
        when (alasan) {
            0 -> lanjutOtomatis()                      // habis
            4 -> {                                     // galat
                val l = laguKini ?: return
                memuat = false
                if (!l.lokal && sudahDicobaUlang.add(l.id) && !YouTube.sedangDitahan) {
                    // alamat kedaluwarsa: minta alamat baru, coba sekali lagi
                    YouTube.lupakan(l.id)
                    Proksi.lupakanAlamat(l.id)
                    muatLagu(l, posisiAwal = posisi)
                    return
                }
                kabar(
                    if (YouTube.sedangDitahan) "YouTube sedang menahan permintaan (dikira bot). Coba lagi nanti."
                    else "Gagal memutar “${l.judul}”${mpv?.pesanGalat(galat)?.let { " ($it)" } ?: ""}",
                )
                if (indeks < antrean.size - 1) berikutnya() else main = false
            }
        }
    }

    // ------------------------------------------------------------------ antrean
    fun putar(daftar: List<Lagu>, mulaiDari: Int = 0) {
        if (daftar.isEmpty()) return
        antrean = daftar
        mainkan(mulaiDari.coerceIn(0, daftar.size - 1))
    }

    fun putarAcak(daftar: List<Lagu>) {
        if (daftar.isEmpty()) return
        putar(daftar.shuffled())
    }

    fun putarDiAntrean(i: Int) {
        if (i in antrean.indices) mainkan(i)
    }

    fun antre(lagu: Lagu) {
        if (laguKini == null) return putar(listOf(lagu))
        antrean = antrean + lagu
        kabar("Ditambahkan ke antrean")
    }

    fun putarBerikutnya(lagu: Lagu) {
        if (laguKini == null) return putar(listOf(lagu))
        val d = antrean.toMutableList()
        d.add((indeks + 1).coerceIn(0, d.size), lagu)
        antrean = d
        kabar("Diputar setelah lagu ini")
    }

    fun hapusDiAntrean(i: Int) {
        if (i !in antrean.indices) return
        val d = antrean.toMutableList()
        d.removeAt(i)
        antrean = d
        when {
            i < indeks -> indeks--
            i == indeks -> if (d.isEmpty()) berhenti() else mainkan(i.coerceAtMost(d.size - 1))
        }
    }

    fun geserDiAntrean(dari: Int, ke: Int) {
        if (dari !in antrean.indices || ke !in antrean.indices) return
        val d = antrean.toMutableList()
        val x = d.removeAt(dari)
        d.add(ke, x)
        antrean = d
        indeks = when (indeks) {
            dari -> ke
            in (minOf(dari, ke)..maxOf(dari, ke)) -> if (dari < ke) indeks - 1 else indeks + 1
            else -> indeks
        }
    }

    fun kosongkanAntrean() {
        val kini = laguKini
        antrean = listOfNotNull(kini)
        indeks = if (kini != null) 0 else -1
    }

    private fun mainkan(i: Int) {
        indeks = i
        muatLagu(antrean[i])
    }

    private fun muatLagu(lagu: Lagu, posisiAwal: Double = 0.0) {
        val m = mpv ?: return kabar(galatMesin ?: "Mesin pemutar belum siap")
        laguKini = lagu
        memuat = true
        posisi = posisiAwal
        durasi = lagu.durasi.toDouble()
        Proksi.sedangDiputar = lagu.id
        val sumber = when {
            lagu.lokal -> lagu.uri
            else -> App.data.unduhan.value[lagu.id]?.takeIf { File(it).isFile } ?: Proksi.alamat(lagu.id)
        }
        if (lagu.lokal && !File(lagu.uri).isFile) {
            memuat = false
            kabar("File tidak ditemukan: ${File(lagu.uri).name}")
            return
        }
        posisiTertunda = posisiAwal
        m.perintah("loadfile", sumber, "replace")
        m.atur("pause", "no")
        App.data.catatPutar(lagu)
        saatGanti(lagu)
        // siapkan lagu berikutnya di cache supaya pergantian lagu langsung
        antrean.getOrNull(indeks + 1)?.takeIf { !it.lokal && !App.data.unduhan.value.containsKey(it.id) }?.let { b ->
            App.scope.launch {
                delay(20_000)
                if (antrean.getOrNull(indeks + 1)?.id == b.id) Proksi.siapkan(b.id)
            }
        }
    }

    private fun lanjutOtomatis() {
        when {
            ulang == 2 -> laguKini?.let { muatLagu(it) }
            indeks < antrean.size - 1 -> berikutnya()
            ulang == 1 && antrean.isNotEmpty() -> mainkan(0)
            else -> main = false
        }
    }

    // ------------------------------------------------------------------ kendali
    fun putarJeda() {
        val m = mpv ?: return
        if (laguKini == null) return
        if (!main && m.baca("idle-active") == "yes") {
            laguKini?.let { muatLagu(it, posisi) }
            return
        }
        m.perintah("cycle", "pause")
        main = !main
    }

    fun berikutnya() {
        if (antrean.isEmpty()) return
        val i = if (acak && antrean.size > 1) {
            (antrean.indices - indeks).random()
        } else {
            indeks + 1
        }
        if (i in antrean.indices) mainkan(i) else if (ulang == 1) mainkan(0)
    }

    fun sebelumnya() {
        if (posisi > 4 || indeks <= 0) {
            geserKe(0.0)
            return
        }
        mainkan(indeks - 1)
    }

    fun geserKe(detik: Double) {
        mpv?.perintah("seek", "%.2f".format(java.util.Locale.ROOT, detik.coerceAtLeast(0.0)), "absolute")
        posisi = detik
    }

    fun geser(selisih: Double) = geserKe((posisi + selisih).coerceIn(0.0, maxOf(durasi - 1, 0.0)))

    fun aturVolume(v: Int) {
        volume = v.coerceIn(0, 100)
        mpv?.atur("volume", volume.toString())
        App.data.ubahPengaturan { it.copy(volume = volume) }
    }

    fun alihAcak() {
        acak = !acak
        kabar(if (acak) "Acak: nyala" else "Acak: mati")
    }

    fun alihUlang() {
        ulang = (ulang + 1) % 3
        kabar(listOf("Ulang: mati", "Ulang: semua antrean", "Ulang: lagu ini")[ulang])
    }

    fun berhenti() {
        mpv?.perintah("stop")
        laguKini = null
        main = false
        indeks = -1
    }

    fun setelTimer(menit: Int) {
        tugasTimer?.cancel()
        if (menit <= 0) {
            timerSampai = 0
            kabar("Timer tidur dimatikan")
            return
        }
        timerSampai = System.currentTimeMillis() + menit * 60_000L
        tugasTimer = App.scope.launch(Dispatchers.Main) {
            delay(menit * 60_000L)
            mpv?.atur("pause", "yes")
            main = false
            timerSampai = 0
        }
        kabar("Musik berhenti dalam $menit menit")
    }
}
