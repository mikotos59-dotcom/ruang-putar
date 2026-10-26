package id.ruangputar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.ruangputar.App
import id.ruangputar.inti.DitahanYouTube
import id.ruangputar.inti.Hasil
import id.ruangputar.inti.Lagu
import id.ruangputar.inti.Pemutar
import id.ruangputar.inti.Penyanyi
import id.ruangputar.inti.Rak
import id.ruangputar.inti.Unduhan
import id.ruangputar.inti.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

enum class Tab(val judul: String) { BERANDA("Beranda"), CARI("Cari"), PUSTAKA("Pustaka"), PENGATURAN("Pengaturan") }

enum class TabPustaka(val judul: String) { FAVORIT("Favorit"), RIWAYAT("Riwayat"), OFFLINE("Offline"), PENYANYI("Penyanyi") }

/** Daftar berhalaman (hasil cari / lagu penyanyi) dengan tombol ◀ ▶. */
class Halaman(private val ambil: suspend (org.schabi.newpipe.extractor.Page?) -> Hasil) {
    var daftar by mutableStateOf<List<Hasil>>(emptyList())
        private set
    var indeks by mutableIntStateOf(0)
        private set
    var memuat by mutableStateOf(false)
        private set
    var galat by mutableStateOf<String?>(null)
        private set

    val kini: List<Lagu> get() = daftar.getOrNull(indeks)?.lagu ?: emptyList()
    val bisaMundur: Boolean get() = indeks > 0 && !memuat
    val bisaMaju: Boolean get() = !memuat && (indeks < daftar.size - 1 || daftar.getOrNull(indeks)?.adaBerikut == true)

    fun mulai() {
        daftar = emptyList()
        indeks = 0
        muat(null)
    }

    fun maju() {
        if (indeks < daftar.size - 1) indeks++ else daftar.getOrNull(indeks)?.berikut?.let { muat(it) }
    }

    fun mundur() {
        if (indeks > 0) indeks--
    }

    private fun muat(halaman: org.schabi.newpipe.extractor.Page?) {
        memuat = true
        galat = null
        Status.scope.launch {
            try {
                val h = ambil(halaman)
                daftar = daftar + h
                indeks = daftar.size - 1
            } catch (e: DitahanYouTube) {
                galat = "YouTube sedang menahan permintaan (dikira bot). Coba lagi beberapa saat lagi."
            } catch (e: Exception) {
                galat = "Gagal memuat: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                memuat = false
            }
        }
    }
}

/** Status tampilan: halaman yang dibuka, hasil cari, rak, pesan. */
object Status {
    /** Scope di thread tampilan (Swing/EDT). */
    val scope = MainScope()
    val data get() = App.data

    var tab by mutableStateOf(Tab.BERANDA)
    var tabPustaka by mutableStateOf(TabPustaka.FAVORIT)
    var panelAntrean by mutableStateOf(false)
    var penyanyiDibuka by mutableStateOf<Penyanyi?>(null)
        private set
    var halamanPenyanyi: Halaman? by mutableStateOf(null)
        private set

    /** Naik setiap kali kotak cari perlu difokuskan (Ctrl+F / klik Cari). */
    var mintaFokusCari by mutableIntStateOf(0)

    private val _pesan = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val pesan: SharedFlow<String> = _pesan
    fun kabar(teks: String) {
        _pesan.tryEmit(teks)
    }

    fun buka(t: Tab) {
        penyanyiDibuka = null
        halamanPenyanyi = null
        tab = t
        if (t == Tab.CARI) mintaFokusCari++
    }

    // ------------------------------------------------------------------ favorit, ikuti, unduh
    fun alihFavorit(lagu: Lagu) {
        kabar(if (data.alihkanFavorit(lagu)) "♥ Ditambahkan ke Favorit" else "Dihapus dari Favorit")
    }

    fun alihIkuti(nama: String, url: String) {
        if (url.isEmpty()) return kabar("Channel penyanyi ini tidak diketahui")
        kabar(if (data.alihkanIkuti(nama, url)) "Mengikuti $nama" else "Berhenti mengikuti $nama")
    }

    fun unduh(lagu: Lagu) {
        if (lagu.lokal) return kabar("Lagu ini sudah ada di PC")
        kabar("Mengunduh “${lagu.judul}”…")
        Unduhan.unduh(lagu) { kabar(it) }
    }

    // ------------------------------------------------------------------ cari
    var kueri by mutableStateOf("")
    var judulCari by mutableStateOf("")
        private set
    var hasilCari: Halaman? by mutableStateOf(null)
        private set
    private var cariTerakhir = Triple(0, "", -1)

    /** minDetik: -1 = ikut Pengaturan (durasi minimal). */
    fun cari(teks: String, maksMenit: Int = 0, judul: String = "", minDetik: Int = -1) {
        val t = teks.trim()
        if (t.isEmpty()) return
        if (t.startsWith("http") && YouTube.idDari(t) != null) return bukaTautan(t)
        kueri = t
        judulCari = judul.ifEmpty { "Hasil: $t" }
        val filter = data.pengaturan.value.filterCari
        val min = if (minDetik < 0) data.pengaturan.value.minDetik else minDetik
        cariTerakhir = Triple(maksMenit, judul, minDetik)
        hasilCari = Halaman { p -> YouTube.cari(t, p, filter, maksMenit * 60, min) }.also { it.mulai() }
        penyanyiDibuka = null
        halamanPenyanyi = null
        tab = Tab.CARI
    }

    fun ubahFilterCari(filter: String) {
        data.ubahPengaturan { it.copy(filterCari = filter) }
        if (kueri.isNotEmpty() && hasilCari != null) cari(kueri)
    }

    /** Durasi minimal (Pengaturan) diubah: muat ulang pencarian & rak yang ikut pengaturan. */
    fun ubahMinDurasi(detik: Int) {
        data.ubahPengaturan { it.copy(minDetik = detik) }
        val (maks, judul, min) = cariTerakhir
        if (kueri.isNotEmpty() && hasilCari != null && min < 0) cari(kueri, maks, judul)
        data.rak.value.filter { it.minDetik < 0 }.forEach { muatRak(it, paksa = true) }
    }

    /** Link YouTube ditempel / dibuka: judulnya diambil, lalu langsung diputar. */
    fun bukaTautan(teks: String) {
        val url = Regex("https?://\\S+").find(teks)?.value ?: return kabar("Tidak ada link di teks itu")
        val id = YouTube.idDari(url) ?: return kabar("Link itu bukan link lagu YouTube")
        kabar("Membuka link…")
        scope.launch {
            val lagu = try {
                YouTube.infoLagu(id)
            } catch (e: DitahanYouTube) {
                return@launch kabar("YouTube sedang menahan permintaan. Coba lagi nanti.")
            } catch (e: Exception) {
                Lagu(id = id, judul = "Lagu YouTube")
            }
            Pemutar.putar(listOf(lagu))
        }
    }

    // ------------------------------------------------------------------ penyanyi
    fun bukaPenyanyi(nama: String, url: String) {
        if (url.isEmpty()) return kabar("Channel penyanyi ini tidak diketahui")
        val h = Halaman { p -> YouTube.laguChannel(url, nama, p) }
        halamanPenyanyi = h
        penyanyiDibuka = Penyanyi(nama, url)
        h.mulai()
    }

    fun tutupPenyanyi() {
        penyanyiDibuka = null
        halamanPenyanyi = null
    }

    fun alihRakPenyanyi(p: Penyanyi) {
        val ada = data.rak.value.firstOrNull { it.channel == p.url }
        if (ada != null) {
            data.hapusRak(ada.id)
            kabar("Rak ${p.nama} dihapus dari Beranda")
        } else {
            data.tambahRak(p.nama, p.nama, 10, p.url)
            kabar("Rak ${p.nama} ditambahkan ke Beranda")
        }
    }

    // ------------------------------------------------------------------ rak beranda
    val isiRak = mutableStateMapOf<String, List<Lagu>>()
    val pesanRak = mutableStateMapOf<String, String>()

    fun muatRak(rak: Rak, paksa: Boolean = false) {
        if (!paksa && isiRak.containsKey(rak.id)) return
        pesanRak[rak.id] = "Memuat…"
        scope.launch {
            try {
                isiRak[rak.id] = YouTube.isiRak(rak, paksa)
                pesanRak.remove(rak.id)
            } catch (e: DitahanYouTube) {
                pesanRak[rak.id] = "Ditunda: YouTube sedang membatasi permintaan."
            } catch (e: Exception) {
                pesanRak[rak.id] = "Tidak bisa memuat. Periksa koneksi internet."
            }
        }
    }

    fun lihatSemuaRak(rak: Rak) {
        if (rak.channel.isNotEmpty()) bukaPenyanyi(rak.kueri, rak.channel) else cari(rak.kueri, rak.maksMenit, rak.nama, rak.minDetik)
    }

    // ------------------------------------------------------------------ offline
    var offline by mutableStateOf<List<Lagu>>(emptyList())
        private set
    var memuatOffline by mutableStateOf(false)
        private set

    fun muatOffline() {
        memuatOffline = true
        scope.launch {
            offline = kotlinx.coroutines.withContext(Dispatchers.IO) { Unduhan.daftarOffline() }
            memuatOffline = false
        }
    }
}
