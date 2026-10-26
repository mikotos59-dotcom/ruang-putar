package id.ruangputar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.ruangputar.inti.Jaringan
import id.ruangputar.inti.Lagu
import id.ruangputar.inti.Pemutar
import id.ruangputar.inti.Unduhan
import id.ruangputar.inti.formatWaktu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Pemuat gambar kecil: unduh, ubah ke ukuran tampil, simpan di memori. */
object PemuatGambar {
    private val simpanan = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 300
    }

    private val ytimg = Regex("""^https?://i\d?\.ytimg\.com/vi(?:_webp)?/([\w-]{11})/""")
    private val ukuranGoogle = Regex("""=(w\d+-h\d+|s\d+)[^/]*$""")

    /** Versi gambar yang pas: besar = 1280×720 / 544×544, kecil = 320×180 / 226×226. */
    private fun calon(url: String, besar: Boolean): List<String> {
        ytimg.find(url)?.let { m ->
            val id = m.groupValues[1]
            val pilih = if (besar) "https://i.ytimg.com/vi/$id/hq720.jpg" else "https://i.ytimg.com/vi/$id/mqdefault.jpg"
            return listOf(pilih, url).distinct()
        }
        if ("googleusercontent.com" in url || "ggpht.com" in url) {
            val ukuran = if (besar) "=w544-h544-l90-rj" else "=w226-h226-l90-rj"
            return listOf(ukuranGoogle.replace(url, ukuran), url).distinct()
        }
        return listOf(url)
    }

    private fun unduh(url: String): ByteArray? {
        if (url.startsWith("file:")) return runCatching { File(java.net.URI(url)).readBytes() }.getOrNull()
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", Jaringan.AGEN).build()
        return Jaringan.api.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    }

    suspend fun ambil(url: String, targetPx: Int): ImageBitmap? {
        if (url.isEmpty()) return null
        val besar = targetPx > 300
        val kunci = "$url|${if (besar) "b" else "k"}"
        synchronized(simpanan) { simpanan[kunci] }?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = calon(url, besar).firstNotNullOfOrNull { runCatching { unduh(it) }.getOrNull() } ?: return@withContext null
                val img = org.jetbrains.skia.Image.makeFromEncoded(bytes)
                val bmp = img.toComposeImageBitmap()
                synchronized(simpanan) { simpanan[kunci] = bmp }
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Composable
fun Sampul(url: String, ukuran: Dp, bentuk: Shape = RoundedCornerShape(8.dp), modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { ukuran.roundToPx() }
    val gambar by produceState<ImageBitmap?>(null, url, px > 300) { value = PemuatGambar.ambil(url, px) }
    Box(
        modifier.size(ukuran).clip(bentuk).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val g = gambar
        if (g != null) {
            Image(g, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
        } else {
            Icon(Ikon.Nada, null, Modifier.size(ukuran / 3), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Menu lagu (⋮ atau klik kanan). */
@Composable
fun MenuLagu(lagu: Lagu, terbuka: Boolean, tutup: () -> Unit, tambahan: List<Pair<String, () -> Unit>> = emptyList()) {
    val favorit by Status.data.favorit.collectAsState()
    val unduhan by Status.data.unduhan.collectAsState()
    DropdownMenu(expanded = terbuka, onDismissRequest = tutup) {
        @Composable
        fun item(teks: String, aksi: () -> Unit) = DropdownMenuItem(text = { Text(teks) }, onClick = { tutup(); aksi() })
        item("Putar setelah ini") { Pemutar.putarBerikutnya(lagu) }
        item("Tambah ke antrean") { Pemutar.antre(lagu) }
        item(if (favorit.any { it.kunci == lagu.kunci }) "Hapus dari Favorit" else "♥ Favoritkan") { Status.alihFavorit(lagu) }
        if (!lagu.lokal) {
            if (!unduhan.containsKey(lagu.id)) item("Unduh ke Offline") { Status.unduh(lagu) }
            if (lagu.channel.isNotEmpty()) {
                item("Lagu lain dari ${lagu.artis.ifEmpty { "penyanyi ini" }}") { Status.bukaPenyanyi(lagu.artis, lagu.channel) }
                item(if (Status.data.mengikuti(lagu.channel)) "Berhenti mengikuti" else "Ikuti ${lagu.artis}") {
                    Status.alihIkuti(lagu.artis, lagu.channel)
                }
            }
            item("Salin link YouTube") {
                java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(java.awt.datatransfer.StringSelection(lagu.urlHalaman), null)
                Status.kabar("Link disalin")
            }
        } else {
            item("Buka folder") {
                runCatching { java.awt.Desktop.getDesktop().open(File(lagu.uri).parentFile) }
            }
        }
        tambahan.forEach { (t, a) -> item(t, a) }
    }
}

/** Satu baris lagu: nomor, sampul, judul, artis, durasi, tombol menu. Klik kanan = menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BarisLagu(
    lagu: Lagu,
    onKlik: () -> Unit,
    nomor: Int? = null,
    menuTambahan: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val proses by Unduhan.proses.collectAsState()
    val unduhan by Status.data.unduhan.collectAsState()
    val favorit by Status.data.favorit.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val aktif = Pemutar.laguKini?.kunci == lagu.kunci
    val warnaAktif = MaterialTheme.colorScheme.primary
    val hover = remember { MutableInteractionSource() }
    val disorot by hover.collectIsHoveredAsState()
    Box {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (disorot) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent)
                .hoverable(hover)
                .onClick(matcher = androidx.compose.foundation.PointerMatcher.mouse(PointerButton.Secondary)) { menu = true }
                .clickable(onClick = onKlik)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (nomor != null) {
                Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                    if (disorot) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    } else {
                        Text(
                            "$nomor", style = MaterialTheme.typography.labelMedium,
                            color = if (aktif) warnaAktif else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Sampul(lagu.sampul, 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    lagu.judul, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold, color = if (aktif) warnaAktif else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    lagu.artis.ifEmpty { if (lagu.lokal) "File di PC" else "" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                proses[lagu.id]?.takeIf { lagu.id.isNotEmpty() }?.let { p ->
                    LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            if (!lagu.lokal && unduhan.containsKey(lagu.id)) {
                Icon(Ikon.Terunduh, "Offline", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            if (favorit.any { it.kunci == lagu.kunci }) {
                Icon(Icons.Filled.Favorite, null, Modifier.size(16.dp), tint = warnaAktif)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (lagu.durasi > 0) formatWaktu(lagu.durasi) else "",
                Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
            }
        }
        Box(Modifier.align(Alignment.TopEnd)) { MenuLagu(lagu, menu, { menu = false }, menuTambahan) }
    }
}

/** Kartu di rak Beranda. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KartuLagu(lagu: Lagu, lebar: Dp = 168.dp, onKlik: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val disorot by hover.collectIsHoveredAsState()
    Box {
        Column(
            Modifier.width(lebar)
                .hoverable(hover)
                .onClick(matcher = androidx.compose.foundation.PointerMatcher.mouse(PointerButton.Secondary)) { menu = true }
                .clickable(onClick = onKlik),
        ) {
            Box {
                Sampul(lagu.sampul, lebar, RoundedCornerShape(10.dp))
                if (disorot) {
                    Surface(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp),
                        shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, "Putar", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
            Text(
                lagu.judul, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                lagu.artis, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MenuLagu(lagu, menu, { menu = false })
    }
}

@Composable
fun Pilihan(teks: String, dipilih: Boolean, onKlik: () -> Unit) {
    Surface(
        onClick = onKlik,
        shape = RoundedCornerShape(50),
        color = if (dipilih) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (dipilih) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(teks, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Tombol ◀ Sebelumnya · Halaman n · Berikutnya ▶. */
@Composable
fun TombolHalaman(h: Halaman) {
    if (h.daftar.isEmpty() && !h.memuat) return
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { h.mundur() }, enabled = h.bisaMundur) { Text("◀ Sebelumnya") }
        Text(
            if (h.memuat) "Memuat…" else "Halaman ${h.indeks + 1}",
            Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelMedium,
        )
        OutlinedButton(onClick = { h.maju() }, enabled = h.bisaMaju) { Text("Berikutnya ▶") }
    }
}

@Composable
fun Kosong(teks: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(teks, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun KepalaPenyanyi(nama: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Person, null, Modifier.size(32.dp)) }
        Spacer(Modifier.width(16.dp))
        Text(nama, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}
