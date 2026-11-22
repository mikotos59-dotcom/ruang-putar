package id.ruangputar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.ruangputar.App
import id.ruangputar.inti.AntreanUnduh
import id.ruangputar.inti.DitahanYouTube
import id.ruangputar.inti.InfoPlaylist
import id.ruangputar.inti.Playlist
import id.ruangputar.inti.Hasil
import id.ruangputar.inti.Lagu
import id.ruangputar.inti.Pemutar
import id.ruangputar.inti.Penyanyi
import id.ruangputar.inti.Rak
import id.ruangputar.inti.Unduhan
import id.ruangputar.inti.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

enum class Tab(val judul: String) { BERANDA("Beranda"), CARI("Cari"), PUSTAKA("Pustaka"), PENGATURAN("Pengaturan") }

enum class TabPustaka(val judul: String) { FAVORIT("Favorit"), PLAYLIST("Playlist"), RIWAYAT("Riwayat"), OFFLINE("Offline"), PENYANYI("Penyanyi") }

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

/** Halaman playlist YouTube yang dibuka dari link: dimuat bertahap (± 100 lagu per halaman). */
class HalamanPlaylistYt(val url: String) {
    var info by mutableStateOf<InfoPlaylist?>(null)
    var lagu by mutableStateOf<List<Lagu>>(emptyList())
    var berikut: org.schabi.newpipe.extractor.Page? = null
    var adaBerikut by mutableStateOf(false)
    var memuat by mutableStateOf(false)
    var galat by mutableStateOf<String?>(null)
    /** Sedang diimpor: jumlah lagu yang sudah terkumpul (-1 = tidak sedang mengimpor). */
    var impor by mutableIntStateOf(-1)
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
        playlistDibuka = null
        playlistYt = null
        tab = t
        if (t == Tab.CARI) mintaFokusCari++
    }

    // ------------------------------------------------------------------ favorit, ikuti, unduh
    fun alihFavorit(lagu: Lagu) {
        kabar(if (data.alihkanFavorit(lagu)) "♥ Ditambahkan ke Favorit" else "Dihapus dari Favorit")
    }

    fun alihIkuti(nama: String, url: String) {
        if (url.isEmpty()) return kabar("Channel penyanyi ini tidak diketahui")
        val ikut = data.alihkanIkuti(nama, url, YouTube.fotoTersimpan(url) ?: "")
        kabar(if (ikut) "Mengikuti $nama" else "Berhenti mengikuti $nama")
        if (ikut && YouTube.fotoTersimpan(url) == null) {
            scope.launch { runCatching { YouTube.fotoChannel(url) }.getOrNull()?.let { data.aturFotoPenyanyi(url, it) } }
        }
    }

    /** Penyanyi yang diikuti tapi belum punya foto profil: dilengkapi pelan-pelan (sekali saat aplikasi dibuka). */
    fun lengkapiFotoPenyanyi() {
        scope.launch {
            for (p in data.penyanyi.value.filter { it.foto.isEmpty() }) {
                if (YouTube.sedangDitahan) break
                runCatching { YouTube.fotoChannel(p.url) }.getOrNull()?.let { data.aturFotoPenyanyi(p.url, it) }
                delay(2500)
            }
        }
    }

    fun unduh(lagu: Lagu) {
        if (lagu.lokal) return kabar("Lagu ini sudah ada di PC")
        if (Unduhan.sudahDiunduh(lagu.id)) return kabar("Sudah ada di Offline")
        if (AntreanUnduh.tambah(listOf(lagu)) == 0) return kabar("Sudah ada di antrean unduhan")
        kabar(if (AntreanUnduh.info.value.antre.size > 1) "Ditambahkan ke antrean unduhan" else "Mengunduh “${lagu.judul}”…")
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
        if (t.startsWith("http") && (YouTube.idDari(t) != null || YouTube.adalahPlaylist(t))) return bukaTautan(t)
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
        val id = YouTube.idDari(url)
        if (id == null) {
            if (YouTube.adalahPlaylist(url)) return bukaPlaylistYouTube(url)
            return kabar("Link itu bukan link lagu / playlist YouTube")
        }
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
        val h = Halaman { p ->
            YouTube.laguChannel(url, nama, p).also {
                // foto profil ikut diketahui saat channel dibuka
                YouTube.fotoTersimpan(url)?.let { f ->
                    if (penyanyiDibuka?.url == url) penyanyiDibuka = penyanyiDibuka?.copy(foto = f)
                    data.aturFotoPenyanyi(url, f)
                }
            }
        }
        halamanPenyanyi = h
        val foto = data.penyanyi.value.firstOrNull { it.url == url }?.foto?.ifEmpty { null } ?: YouTube.fotoTersimpan(url) ?: ""
        penyanyiDibuka = Penyanyi(nama, url, foto)
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

    // ------------------------------------------------------------------ playlist sendiri
    /** Playlist yang sedang dibuka (id), di dalam tab Pustaka. */
    var playlistDibuka by mutableStateOf<String?>(null)
        private set

    /** Lagu yang sedang mau dimasukkan ke playlist (jendela "Tambah ke playlist"). */
    var tambahKePlaylist by mutableStateOf<List<Lagu>?>(null)

    /** Playlist yang sedang ditawarkan untuk diunduh (jendela "Unduh playlist"). */
    var unduhPlaylist by mutableStateOf<String?>(null)

    fun bukaPlaylist(id: String) {
        penyanyiDibuka = null
        halamanPenyanyi = null
        playlistYt = null
        playlistDibuka = id
        tabPustaka = TabPustaka.PLAYLIST
        tab = Tab.PUSTAKA
    }

    fun tutupPlaylist() {
        playlistDibuka = null
    }

    fun buatPlaylist(nama: String, lagu: List<Lagu> = emptyList()): Playlist {
        val p = data.buatPlaylist(nama, lagu)
        kabar("Playlist “${p.nama}” dibuat")
        return p
    }

    /** Masukkan lagu ke playlist. Playlist offline: lagu baru langsung masuk antrean unduhan. */
    fun masukkanKePlaylist(id: String, lagu: List<Lagu>) {
        val p = data.playlistDengan(id) ?: return
        val masuk = lagu.filter { data.tambahKePlaylist(id, it) }
        when {
            masuk.isEmpty() -> kabar(if (lagu.size == 1) "Sudah ada di ${p.nama}" else "Semua lagu sudah ada di ${p.nama}")
            p.offline -> {
                val n = AntreanUnduh.tambah(masuk, p.nama)
                kabar("Ditambahkan ke ${p.nama}" + if (n > 0) " — sedang diunduh" else "")
            }
            else -> kabar(if (masuk.size == 1) "Ditambahkan ke ${p.nama}" else "${masuk.size} lagu ditambahkan ke ${p.nama}")
        }
    }

    /** Keluarkan lagu dari playlist; [hapusFile] = file unduhannya ikut dihapus. */
    fun keluarkanDariPlaylist(id: String, lagu: Lagu, hapusFile: Boolean) {
        data.hapusDariPlaylist(id, lagu.kunci)
        if (hapusFile && !lagu.lokal && Unduhan.hapusFile(lagu.id)) muatOffline()
    }

    /** Perlu bertanya "hapus juga file unduhannya?" saat lagu dikeluarkan dari playlist ini? */
    fun perluTanyaFile(id: String, lagu: Lagu): Boolean {
        val p = data.playlistDengan(id) ?: return false
        return p.offline && !lagu.lokal && Unduhan.sudahDiunduh(lagu.id) && !data.diPlaylistOffline(lagu.kunci, kecuali = id)
    }

    /** Mulai unduh playlist. [semua] = seluruh lagu -> playlist ditandai offline (lagu baru ikut diunduh). */
    fun mulaiUnduhPlaylist(id: String, daftar: List<Lagu>, semua: Boolean) {
        val p = data.playlistDengan(id) ?: return
        if (semua) data.aturOfflinePlaylist(id, true)
        val n = AntreanUnduh.tambah(daftar, p.nama)
        kabar(if (n > 0) "Mengunduh $n lagu dari ${p.nama}" else "Semua lagu itu sudah diunduh")
    }

    /** Hapus file unduhan lagu-lagu playlist ini (kecuali yang masih dipakai playlist offline lain). Playlist tetap ada. */
    fun hapusUnduhanPlaylist(id: String) {
        val p = data.playlistDengan(id) ?: return
        data.aturOfflinePlaylist(id, false)
        var n = 0
        p.lagu.filter { !it.lokal && Unduhan.sudahDiunduh(it.id) && !data.diPlaylistOffline(it.kunci, kecuali = id) }
            .forEach { if (Unduhan.hapusFile(it.id)) n++ }
        muatOffline()
        kabar("$n file unduhan dihapus. ${p.nama} kembali jadi playlist online.")
    }

    fun hapusPlaylist(id: String) {
        val p = data.playlistDengan(id) ?: return
        data.hapusPlaylist(id)
        if (playlistDibuka == id) playlistDibuka = null
        kabar("Playlist “${p.nama}” dihapus")
    }

    // ------------------------------------------------------------------ playlist YouTube
    var playlistYt by mutableStateOf<HalamanPlaylistYt?>(null)
        private set

    fun bukaPlaylistYouTube(url: String) {
        val h = HalamanPlaylistYt(url)
        penyanyiDibuka = null
        halamanPenyanyi = null
        playlistDibuka = null
        playlistYt = h
        h.memuat = true
        scope.launch {
            try {
                val (info, hasil) = YouTube.bukaPlaylist(url)
                h.info = info
                h.lagu = hasil.lagu
                h.berikut = hasil.berikut
                h.adaBerikut = hasil.berikut != null
            } catch (e: DitahanYouTube) {
                h.galat = "YouTube sedang menahan permintaan (dikira bot). Coba lagi beberapa saat lagi."
            } catch (e: Exception) {
                h.galat = "Playlist tidak bisa dibuka (mungkin pribadi atau sudah dihapus)."
            } finally {
                h.memuat = false
            }
        }
    }

    fun tutupPlaylistYt() {
        playlistYt = null
    }

    fun muatLagiPlaylistYt(h: HalamanPlaylistYt) {
        val hal = h.berikut ?: return
        if (h.memuat) return
        h.memuat = true
        scope.launch {
            try {
                val r = YouTube.lanjutPlaylist(h.url, hal)
                val ada = h.lagu.map { it.id }.toSet()
                h.lagu = h.lagu + r.lagu.filter { it.id !in ada }
                h.berikut = r.berikut
                h.adaBerikut = r.berikut != null
            } catch (e: Exception) {
                kabar(if (e is DitahanYouTube) "YouTube sedang menahan permintaan. Coba lagi nanti." else "Gagal memuat lagu berikutnya")
            } finally {
                h.memuat = false
            }
        }
    }

    /** Putar bertahap: yang sudah dimuat diputar, 100 berikutnya diambil saat antrean tinggal 5 lagu. */
    fun putarPlaylistYt(h: HalamanPlaylistYt, mulai: Int = 0, acak: Boolean = false) {
        if (h.lagu.isEmpty()) return
        if (acak) Pemutar.putarAcak(h.lagu) else Pemutar.putar(h.lagu, mulai)
        var hal = h.berikut
        if (hal != null && !acak) {
            Pemutar.ambilLanjutan = {
                val sekarang = hal
                if (sekarang == null) {
                    emptyList()
                } else {
                    val r = YouTube.lanjutPlaylist(h.url, sekarang)
                    hal = r.berikut
                    r.lagu
                }
            }
        }
    }

    /** Ambil semua halaman (maks. 1.000 lagu, dengan jeda), lalu simpan jadi playlist sendiri. */
    fun imporPlaylistYt(h: HalamanPlaylistYt) {
        if (h.impor >= 0) return
        val info = h.info ?: return
        h.impor = h.lagu.size
        scope.launch {
            val semua = h.lagu.toMutableList()
            var hal = h.berikut
            var terputus = false
            while (hal != null && semua.size < BATAS_IMPOR) {
                delay(1200)                                              // pelan-pelan, supaya tidak dikira bot
                try {
                    val r = YouTube.lanjutPlaylist(h.url, hal)
                    val ada = semua.map { it.id }.toSet()
                    semua += r.lagu.filter { it.id !in ada }
                    hal = r.berikut
                    h.impor = semua.size
                } catch (e: Exception) {
                    terputus = true
                    break
                }
            }
            val daftar = semua.take(BATAS_IMPOR)
            val p = data.buatPlaylist(info.nama, daftar, info.url)
            h.impor = -1
            kabar(
                "Disimpan: “${p.nama}” (${daftar.size} lagu)" + when {
                    terputus -> " — sebagian tidak terambil, coba impor lagi nanti"
                    hal != null -> " — dibatasi $BATAS_IMPOR lagu"
                    else -> ""
                },
            )
            bukaPlaylist(p.id)
        }
    }

    const val BATAS_IMPOR = 1000

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
