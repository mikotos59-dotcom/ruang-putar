package id.ruangputar.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.ruangputar.App
import id.ruangputar.Versi
import id.ruangputar.keluarAplikasi
import id.ruangputar.inti.Impor
import id.ruangputar.inti.Mesin
import id.ruangputar.inti.Pembaruan
import id.ruangputar.inti.Proksi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import javax.swing.JFileChooser

const val TAUTAN_DISCORD = "https://discord.gg/VRqw9EGUpb"

@Composable
fun LayarPengaturan() {
    val p by Status.data.pengaturan.collectAsState()
    val scope = rememberCoroutineScope()
    val gulir = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(gulir).padding(start = 24.dp, end = 32.dp, bottom = 32.dp)) {
            Column(Modifier.widthIn(max = 820.dp)) {
                Text("Pengaturan", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                Bagian("Tampilan")
                BarisPilihan("Tema", listOf("batik" to "Batik", "batik-terang" to "Batik terang", "gelap" to "Gelap", "terang" to "Terang", "otomatis" to "Ikut sistem"), p.tema) { v ->
                    Status.data.ubahPengaturan { it.copy(tema = v) }
                }

                Bagian("Pemutaran")
                BarisPilihan("Kualitas suara", listOf("tinggi" to "Tinggi", "hemat" to "Hemat kuota"), p.kualitas,
                    "Hemat kuota: ± 50–70 kbps. Berlaku untuk lagu yang belum ada di cache.") { v ->
                    Status.data.ubahPengaturan { it.copy(kualitas = v) }
                }
                BarisSaklar("Sembunyikan ke tray saat ditutup", p.tutupKeTray,
                    "Tombol X menyembunyikan jendela; musik tetap jalan. Keluar lewat ikon Ruang Putar di tray.") { v ->
                    Status.data.ubahPengaturan { it.copy(tutupKeTray = v) }
                }
                BarisSaklar("Kabar lagu berganti", p.notifikasi, "Muncul di pojok layar saat jendela sedang disembunyikan.") { v ->
                    Status.data.ubahPengaturan { it.copy(notifikasi = v) }
                }

                Bagian("Pencarian & Beranda")
                BarisPilihan("Durasi minimal lagu", PILIHAN_MIN.map { (d, t) -> d.toString() to t }, p.minDetik.toString(),
                    "Lagu yang lebih pendek disembunyikan dari hasil cari & rak (mis. potongan / klip pendek). Tiap rak bisa diatur sendiri.") { v ->
                    Status.ubahMinDurasi(v.toInt())
                }

                Bagian("Unduhan & cache")
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("Folder Offline", fontWeight = FontWeight.SemiBold)
                    Text(p.folderMusik, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val pilih = JFileChooser(p.folderMusik).apply {
                                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                dialogTitle = "Pilih folder Offline"
                            }
                            if (pilih.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                Status.data.ubahPengaturan { it.copy(folderMusik = pilih.selectedFile.absolutePath) }
                            }
                        }) { Text("Ganti folder…") }
                        OutlinedButton(onClick = { runCatching { Desktop.getDesktop().open(java.io.File(p.folderMusik).apply { mkdirs() }) } }) {
                            Text("Buka folder")
                        }
                    }
                }
                BarisPilihan(
                    "Kecepatan unduh", listOf("rendah" to "Rendah ± 1 MB/s", "normal" to "Normal ± 3 MB/s", "tanpa" to "Tanpa batas"), p.kecepatan,
                    "Berlaku untuk semua unduhan sekaligus (putar & Offline). Rendah = tidak mengganggu game.",
                ) { v -> Status.data.ubahPengaturan { it.copy(kecepatan = v) } }
                val ukuran = remember { mutableLongStateOf(0L) }
                LaunchedEffect(Unit) { ukuran.longValue = withContext(Dispatchers.IO) { Proksi.ukuranCache() } }
                BarisPilihan(
                    "Batas cache", listOf("512" to "512 MB", "1024" to "1 GB", "2048" to "2 GB", "4096" to "4 GB"), p.cacheMb.toString(),
                    "Lagu yang pernah diputar disimpan di sini dan bisa diulang tanpa internet. Terpakai sekarang: ${"%.0f".format(ukuran.longValue / 1048576.0)} MB.",
                ) { v ->
                    Status.data.ubahPengaturan { it.copy(cacheMb = v.toInt()) }
                    scope.launch(Dispatchers.IO) { Proksi.rapikan(v.toLong() * 1024 * 1024); ukuran.longValue = Proksi.ukuranCache() }
                }
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        Proksi.kosongkan()
                        ukuran.longValue = Proksi.ukuranCache()
                        Status.kabar("Cache dikosongkan")
                    }
                }) { Text("Kosongkan cache") }

                Bagian("Beranda")
                OutlinedButton(onClick = { Status.data.kembalikanRakBawaan(); Status.kabar("Rak Beranda dikembalikan ke bawaan") }) {
                    Text("Kembalikan rak bawaan")
                }

                Bagian("Pembaruan")
                BagianPembaruan()
                Spacer(Modifier.height(12.dp))
                BagianMesin()

                if (Impor.ada()) {
                    Bagian("Data Ruang Putar lama")
                    Text(
                        "Favorit, riwayat, rak, dan penyanyi dari Ruang Putar versi sebelumnya (mesin yt-dlp) sudah diimpor otomatis " +
                            "saat pertama dibuka. Bisa diimpor lagi — data yang sudah ada tidak terhapus dan tidak dobel.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val hasil = runCatching { Impor.jalankan() }.getOrElse { "Gagal impor: ${it.message}" }
                            Status.kabar(hasil ?: "Data lama tidak ditemukan")
                        }
                    }, Modifier.padding(top = 6.dp)) { Text("Impor lagi") }
                }

                Bagian("Tentang")
                Text("Ruang Putar v${Versi.NAMA} (build ${Versi.KODE})", fontWeight = FontWeight.SemiBold)
                Text("Dibuat oleh @SuS_X_x", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { buka(TAUTAN_DISCORD) }) { Text("Gabung Discord") }
                    OutlinedButton(onClick = { buka("https://github.com/${Versi.REPO_GITHUB}") }) { Text("GitHub") }
                    OutlinedButton(onClick = { runCatching { Desktop.getDesktop().open(App.folderData) } }) { Text("Folder data") }
                }
                Text(
                    "Kirim masukan & lapor bug di server Discord Bengkel SuS (sertakan versi di atas + screenshot).\n" +
                        "Untuk pemakaian pribadi. Mesin pengambil lagu: NewPipeExtractor (GPL-3.0). Pemutar suara: mpv (LGPL).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(gulir), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

fun buka(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

@Composable
private fun Bagian(judul: String) {
    Spacer(Modifier.height(24.dp))
    Text(judul.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 8.dp))
}

@Composable
private fun BarisPilihan(judul: String, pilihan: List<Pair<String, String>>, nilai: String, ket: String = "", ubah: (String) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(judul, fontWeight = FontWeight.SemiBold)
        if (ket.isNotEmpty()) {
            Text(ket, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pilihan.forEach { (v, t) -> Pilihan(t, v == nilai) { ubah(v) } }
        }
    }
}

@Composable
private fun BarisSaklar(judul: String, nilai: Boolean, ket: String, ubah: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(judul, fontWeight = FontWeight.SemiBold)
            Text(ket, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(nilai, ubah)
    }
}

// ====================================================================== pembaruan aplikasi
@Composable
private fun BagianPembaruan() {
    val s by Pembaruan.status.collectAsState()
    Text("Aplikasi: v${Versi.NAMA} (build ${Versi.KODE})", fontWeight = FontWeight.SemiBold)
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        when (val st = s) {
            is Pembaruan.Status.Tersedia, is Pembaruan.Status.Siap -> Button(onClick = { Pembaruan.perbarui() }) { Text("Perbarui sekarang") }
            is Pembaruan.Status.Mengunduh -> OutlinedButton(onClick = { Pembaruan.tawarkan.value = true }) { Text("Mengunduh ${st.persen}%") }
            else -> OutlinedButton(onClick = { Pembaruan.cek(manual = true) }, enabled = st !is Pembaruan.Status.Mengecek) { Text("Cek pembaruan") }
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        val ket = when (val st = s) {
            Pembaruan.Status.Mengecek -> "Mengecek…"
            Pembaruan.Status.Terbaru -> "Sudah versi terbaru ✓"
            is Pembaruan.Status.Tersedia -> st.nama
            is Pembaruan.Status.Siap -> "Sudah diunduh"
            is Pembaruan.Status.Galat -> st.pesan
            else -> ""
        }
        Text(ket, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        "Dicek otomatis sekali sehari. Pembaruan dipasang otomatis, lalu Ruang Putar dibuka lagi.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ====================================================================== pembaruan mesin YouTube
@Composable
private fun BagianMesin() {
    val s by Mesin.status.collectAsState()
    Text(
        "Mesin YouTube: NewPipeExtractor ${Mesin.versiAktif}" + if (Mesin.diperbarui) " (diperbarui, bawaan ${Versi.NEWPIPE})" else "",
        fontWeight = FontWeight.SemiBold,
    )
    Mesin.gagalSebelumnya?.let {
        Text(
            "⚠ Mesin $it gagal dipakai, jadi kembali ke mesin bawaan.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
        )
    }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val st = s) {
            is Mesin.Status.Tersedia ->
                if (st.lewatAplikasi) {
                    Button(onClick = { Pembaruan.perbarui() }) { Text("Perbarui aplikasi (mesin ${st.versi})") }
                } else {
                    Button(onClick = { Mesin.pasang(st.versi) }) { Text("Pasang mesin ${st.versi}") }
                }
            is Mesin.Status.PerluMulaiUlang -> Button(onClick = { mulaiUlang() }) { Text("Buka ulang Ruang Putar") }
            else -> OutlinedButton(
                onClick = { Mesin.cek(manual = true) },
                enabled = st !is Mesin.Status.Mengecek && st !is Mesin.Status.Memasang,
            ) { Text("Cek mesin terbaru") }
        }
        if (Mesin.diperbarui && s !is Mesin.Status.PerluMulaiUlang) {
            TextButton(onClick = { Mesin.kembaliKeBawaan() }) { Text("Kembali ke mesin bawaan") }
        }
    }
    val ket = when (val st = s) {
        Mesin.Status.Mengecek -> "Mengecek versi terbaru…"
        Mesin.Status.Terbaru -> "Mesin sudah versi terbaru ✓"
        is Mesin.Status.Tersedia -> if (st.lewatAplikasi) "Versi aplikasi baru sudah membawa mesin ${st.versi} (paling aman)."
        else "Mesin ${st.versi} tersedia — dipasang tanpa memasang ulang aplikasi (± 2 MB)."
        is Mesin.Status.Memasang -> st.keterangan
        is Mesin.Status.PerluMulaiUlang -> "Mesin ${st.versi} siap. Buka ulang Ruang Putar untuk memakainya."
        is Mesin.Status.Galat -> st.pesan
        else -> "Kalau lagu tiba-tiba gagal dicari/diputar semua, biasanya YouTube berubah: cek mesin terbaru di sini."
    }
    if (s is Mesin.Status.Memasang) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
    Text(ket, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

/** Jalankan ulang aplikasi (exe yang sama), lalu keluar. */
fun mulaiUlang() {
    val exe = System.getProperty("jpackage.app-path") ?: ProcessHandle.current().info().command().orElse(null)
    App.data.simpanSekarang()
    if (exe != null && !exe.endsWith("java", ignoreCase = true) && !exe.endsWith("java.exe", ignoreCase = true)) {
        runCatching { ProcessBuilder(exe, "--mulai-ulang").start() }
    } else {
        Status.kabar("Tutup lalu buka lagi Ruang Putar secara manual.")
        return
    }
    keluarAplikasi()
}

// ====================================================================== jendela tawaran pembaruan
@Composable
fun DialogPembaruan() {
    val tampil by Pembaruan.tawarkan.collectAsState()
    val s by Pembaruan.status.collectAsState()
    val masalah by Pembaruan.karenaMasalah.collectAsState()
    if (!tampil) return
    when (val st = s) {
        is Pembaruan.Status.Tersedia -> AlertDialog(
            onDismissRequest = { Pembaruan.nanti() },
            title = { Text("Pembaruan tersedia") },
            text = {
                Column(Modifier.widthIn(max = 480.dp)) {
                    if (masalah) {
                        Text("Lagu sering gagal diputar atau dicari. Versi baru ini mungkin sudah memperbaikinya.",
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(st.nama + if (st.ukuran > 0) " · %.0f MB".format(st.ukuran / 1048576.0) else "")
                    Text("Terpasang: v${Versi.NAMA} (build ${Versi.KODE})", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (st.catatan.isNotBlank()) Text(st.catatan, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Text("Ruang Putar akan tertutup sebentar selama pemasangan, lalu terbuka lagi. Data tetap aman.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { Button(onClick = { Pembaruan.perbarui() }) { Text("Perbarui") } },
            dismissButton = { TextButton(onClick = { Pembaruan.nanti() }) { Text("Nanti") } },
        )
        is Pembaruan.Status.Mengunduh -> AlertDialog(
            onDismissRequest = { },
            title = { Text("Mengunduh pembaruan…") },
            text = {
                Column(Modifier.widthIn(min = 320.dp)) {
                    LinearProgressIndicator(progress = { st.persen / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${st.persen}%", Modifier.padding(top = 8.dp))
                    Text("Musik tetap bisa diputar selama mengunduh.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { Pembaruan.tawarkan.value = false }) { Text("Sembunyikan") } },
        )
        is Pembaruan.Status.Galat -> AlertDialog(
            onDismissRequest = { Pembaruan.tawarkan.value = false },
            title = { Text("Pembaruan gagal") },
            text = { Text(st.pesan) },
            confirmButton = { TextButton(onClick = { Pembaruan.tawarkan.value = false }) { Text("Tutup") } },
        )
        else -> Unit
    }
}
