package id.ruangputar.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.ruangputar.inti.Lagu
import id.ruangputar.inti.Pemutar
import id.ruangputar.inti.Penyanyi
import id.ruangputar.inti.Rak
import id.ruangputar.inti.Unduhan
import id.ruangputar.inti.YouTube
import kotlinx.coroutines.launch
import java.time.LocalTime

// ====================================================================== bantuan tata letak
/** Kolom bergulir dengan scrollbar di kanan. */
@Composable
fun GulirKolom(state: LazyListState = rememberLazyListState(), isi: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = state, contentPadding = PaddingValues(bottom = 24.dp), content = isi)
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
fun JudulHalaman(teks: String, ekor: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(teks, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ekor?.invoke()
    }
}

// ====================================================================== Beranda
@Composable
fun LayarBeranda() {
    val data = Status.data
    val rak by data.rak.collectAsState()
    val riwayat by data.riwayat.collectAsState()
    val pengaturan by data.pengaturan.collectAsState()
    val ditahan by YouTube.ditahanSampai.collectAsState()
    val sering = remember(riwayat) { data.seringDiputar() }
    var dialogRak by remember { mutableStateOf<Rak?>(null) }
    var tambahRak by remember { mutableStateOf(false) }

    GulirKolom {
        item { KepalaBeranda(pengaturan.tema == "batik") }
        if (ditahan > System.currentTimeMillis()) item { BannerDitahan() }
        if (sering.isNotEmpty()) {
            item {
                JudulRak("Sering kamu putar", null, null)
                BarisKartu(sering)
            }
        }
        items(rak, key = { it.id }) { r ->
            LaunchedEffect(r.id) { Status.muatRak(r) }
            RakBeranda(r, onUbah = { dialogRak = r })
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                TextButton(onClick = { tambahRak = true }) { Text("+ Tambah rak") }
                TextButton(onClick = { data.kembalikanRakBawaan() }) { Text("Kembalikan rak bawaan") }
            }
        }
    }

    if (tambahRak) {
        DialogRak(null, onBatal = { tambahRak = false }) { nama, kueri, maks, min ->
            data.tambahRak(nama, kueri, maks, minDetik = min)
            tambahRak = false
        }
    }
    dialogRak?.let { r ->
        DialogRak(r, onBatal = { dialogRak = null }) { nama, kueri, maks, min ->
            val baru = r.copy(nama = nama, kueri = kueri, maksMenit = maks, minDetik = min)
            data.ubahRak(baru)
            Status.muatRak(baru, paksa = true)
            dialogRak = null
        }
    }
}

@Composable
private fun KepalaBeranda(batik: Boolean) {
    val jam = LocalTime.now().hour
    val sapa = when (jam) {
        in 4..10 -> "pagi"
        in 11..14 -> "siang"
        in 15..17 -> "sore"
        else -> "malam"
    }
    Box(Modifier.fillMaxWidth().height(if (batik) 150.dp else 110.dp)) {
        if (batik) {
            MotifKawung(Modifier.fillMaxSize(), MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text("Selamat $sapa", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Musik untukmu hari ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BannerDitahan() {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.secondary)
            Text(
                "YouTube sementara menahan permintaan dari internetmu (dikira bot). Lagu di cache & Offline tetap bisa " +
                    "diputar. Biasanya pulih sendiri dalam 30 menit; memutus-sambung modem/router bisa memberi IP baru.",
                Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun JudulRak(judul: String, onKlik: (() -> Unit)?, ekor: (@Composable () -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            judul, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).let { if (onKlik != null) it.clickable(onClick = onKlik) else it },
        )
        ekor?.invoke()
    }
}

/** Deretan kartu mendatar dengan tombol ◀ ▶ dan scrollbar (geser dengan Shift + roda mouse). */
@Composable
private fun BarisKartu(daftar: List<Lagu>) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box {
        Column {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(daftar) { i, l -> KartuLagu(l) { Pemutar.putar(daftar, i) } }
            }
            HorizontalScrollbar(rememberScrollbarAdapter(state), Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp))
        }
        if (state.canScrollBackward) {
            TombolGeser(Icons.Filled.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) {
                scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - 4).coerceAtLeast(0)) }
            }
        }
        if (state.canScrollForward) {
            TombolGeser(Icons.Filled.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) {
                scope.launch { state.animateScrollToItem(state.firstVisibleItemIndex + 4) }
            }
        }
    }
}

@Composable
private fun TombolGeser(ikon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, aksi: () -> Unit) {
    Surface(modifier.padding(4.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shadowElevation = 2.dp) {
        IconButton(onClick = aksi) { Icon(ikon, null) }
    }
}

@Composable
private fun RakBeranda(r: Rak, onUbah: () -> Unit) {
    val isi = Status.isiRak[r.id]
    val pesan = Status.pesanRak[r.id]
    var menu by remember { mutableStateOf(false) }
    val pengaturan by Status.data.pengaturan.collectAsState()
    JudulRak(r.nama + "  ›", { Status.lihatSemuaRak(r) }) {
        val label = labelDurasi(r.minBerlaku(pengaturan), r.maksMenit)
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { Status.muatRak(r, paksa = true) }) { Icon(Icons.Filled.Refresh, "Muat ulang") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu rak") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Putar semua") }, onClick = { menu = false; isi?.let { Pemutar.putar(it) } })
                DropdownMenuItem(text = { Text("Ubah…") }, onClick = { menu = false; onUbah() })
                DropdownMenuItem(text = { Text("Naikkan") }, onClick = { menu = false; Status.data.geserRak(r.id, -1) })
                DropdownMenuItem(text = { Text("Turunkan") }, onClick = { menu = false; Status.data.geserRak(r.id, 1) })
                DropdownMenuItem(text = { Text("Hapus rak") }, onClick = { menu = false; Status.data.hapusRak(r.id) })
            }
        }
    }
    when {
        !isi.isNullOrEmpty() -> BarisKartu(isi)
        pesan != null -> Text(pesan, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Text("Tidak ada lagu.", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DialogRak(lama: Rak?, onBatal: () -> Unit, onSimpan: (String, String, Int, Int) -> Unit) {
    var nama by remember { mutableStateOf(lama?.nama ?: "") }
    var kueri by remember { mutableStateOf(lama?.kueri ?: "") }
    var maks by remember { mutableIntStateOf(lama?.maksMenit ?: 5) }
    var min by remember { mutableIntStateOf(lama?.minDetik ?: -1) }
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text(if (lama == null) "Tambah rak" else "Ubah rak") },
        text = {
            Column(Modifier.widthIn(max = 520.dp)) {
                OutlinedTextField(nama, { nama = it }, label = { Text("Nama rak") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(kueri, { kueri = it }, label = { Text("Kata kunci pencarian") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Durasi minimal", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PILIHAN_MIN_RAK.forEach { (d, t) -> Pilihan(t, min == d) { min = d } }
                }
                Text("Durasi maksimal", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Semua", 5 to "< 5 mnt", 10 to "< 10 mnt", 20 to "< 20 mnt", 60 to "< 1 jam").forEach { (m, t) ->
                        Pilihan(t, maks == m) { maks = m }
                    }
                }
                Text(
                    "“< 5 mnt” = lagu satuan, tanpa kompilasi / full album.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = kueri.isNotBlank(), onClick = { onSimpan(nama.ifBlank { kueri.take(30) }, kueri.trim(), maks, min) }) {
                Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal") } },
    )
}

// ====================================================================== Cari
@Composable
fun LayarCari() {
    val pengaturan by Status.data.pengaturan.collectAsState()
    val h = Status.hasilCari
    val daftarState = rememberLazyListState()
    val fokus = remember { FocusRequester() }
    LaunchedEffect(h, h?.indeks) { daftarState.scrollToItem(0) }
    LaunchedEffect(Status.mintaFokusCari) { runCatching { fokus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = Status.kueri,
            onValueChange = { Status.kueri = it },
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp).focusRequester(fokus),
            placeholder = { Text("Judul lagu, penyanyi, atau link YouTube — tekan Enter") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (Status.kueri.isNotEmpty()) IconButton(onClick = { Status.kueri = "" }) { Icon(Icons.Filled.Clear, "Hapus") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { Status.cari(Status.kueri) }, onDone = { Status.cari(Status.kueri) }),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pilihan("Lagu", pengaturan.filterCari == "lagu") { Status.ubahFilterCari("lagu") }
            Pilihan("Semua video", pengaturan.filterCari == "video") { Status.ubahFilterCari("video") }
            Spacer(Modifier.padding(horizontal = 8.dp))
            Text("Min.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PILIHAN_MIN.forEach { (d, t) -> Pilihan(t, pengaturan.minDetik == d) { Status.ubahMinDurasi(d) } }
        }
        if (h == null) {
            Kosong("Ketik judul lagu atau nama penyanyi, lalu tekan Enter. Bisa juga tempel link YouTube.")
            return@Column
        }
        Text(
            Status.judulCari, Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        h.galat?.let { Text(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.secondary) }
        if (h.memuat && h.kini.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        val daftar = h.kini
        GulirKolom(daftarState) {
            if (daftar.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { Pemutar.putar(daftar) }) { Text("Putar semua") }
                        OutlinedButton(onClick = { daftar.forEach { Pemutar.antre(it) } }) { Text("Tambah ke antrean") }
                    }
                }
            } else if (!h.memuat && h.galat == null) {
                item { Kosong("Tidak ada hasil.") }
            }
            itemsIndexed(daftar, key = { _, l -> l.id }) { i, l ->
                Box(Modifier.padding(horizontal = 12.dp)) { BarisLagu(l, onKlik = { Pemutar.putar(daftar, i) }, nomor = i + 1) }
            }
            item { TombolHalaman(h) }
        }
    }
}

// ====================================================================== Pustaka
@Composable
fun LayarPustaka() {
    Column(Modifier.fillMaxSize()) {
        JudulHalaman("Pustaka")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabPustaka.entries.forEach { t -> Pilihan(t.judul, Status.tabPustaka == t) { Status.tabPustaka = t } }
        }
        Spacer(Modifier.height(8.dp))
        when (Status.tabPustaka) {
            TabPustaka.FAVORIT -> {
                val favorit by Status.data.favorit.collectAsState()
                DaftarLagu(favorit, "Belum ada favorit. Klik kanan sebuah lagu, lalu ♥ Favoritkan.")
            }
            TabPustaka.RIWAYAT -> {
                val riwayat by Status.data.riwayat.collectAsState()
                DaftarLagu(riwayat, "Riwayat kosong.", ekstra = {
                    TextButton(onClick = { Status.data.hapusRiwayat() }) { Text("Hapus riwayat") }
                })
            }
            TabPustaka.OFFLINE -> HalamanOffline()
            TabPustaka.PENYANYI -> HalamanDaftarPenyanyi()
        }
    }
}

@Composable
private fun DaftarLagu(daftar: List<Lagu>, kosong: String, ekstra: (@Composable () -> Unit)? = null) {
    GulirKolom {
        if (daftar.isEmpty()) {
            item { Kosong(kosong) }
        } else {
            item {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { Pemutar.putar(daftar) }) { Text("Putar semua") }
                    OutlinedButton(onClick = { Pemutar.putarAcak(daftar) }) { Text("Acak") }
                    Text("${daftar.size} lagu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ekstra?.invoke()
                }
            }
            itemsIndexed(daftar, key = { _, l -> l.kunci }) { i, l ->
                Box(Modifier.padding(horizontal = 12.dp)) { BarisLagu(l, onKlik = { Pemutar.putar(daftar, i) }, nomor = i + 1) }
            }
        }
    }
}

@Composable
private fun HalamanOffline() {
    val unduhan by Status.data.unduhan.collectAsState()
    val pengaturan by Status.data.pengaturan.collectAsState()
    LaunchedEffect(unduhan.size, pengaturan.folderMusik) { Status.muatOffline() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Lagu di folder ${pengaturan.folderMusik} — diputar tanpa internet.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { runCatching { java.awt.Desktop.getDesktop().open(Unduhan.folder().apply { mkdirs() }) } }) {
                Text("Buka folder")
            }
            TextButton(onClick = { Status.muatOffline() }) { Text("Muat ulang") }
        }
        if (Status.memuatOffline && Status.offline.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            DaftarLagu(Status.offline, "Belum ada lagu offline. Klik kanan sebuah lagu, lalu Unduh ke Offline.")
        }
    }
}

@Composable
private fun HalamanDaftarPenyanyi() {
    val penyanyi by Status.data.penyanyi.collectAsState()
    GulirKolom {
        if (penyanyi.isEmpty()) {
            item { Kosong("Belum mengikuti penyanyi. Klik kanan sebuah lagu, lalu Ikuti.") }
        }
        items(penyanyi, key = { it.url }) { p ->
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { Status.bukaPenyanyi(p.nama, p.url) }.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { KepalaPenyanyi(p.nama) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Berhenti mengikuti") }, onClick = { menu = false; Status.alihIkuti(p.nama, p.url) })
                    }
                }
            }
        }
    }
}

// ====================================================================== Penyanyi
@Composable
fun LayarPenyanyi(p: Penyanyi, h: Halaman) {
    val diikuti by Status.data.penyanyi.collectAsState()
    val semuaRak by Status.data.rak.collectAsState()
    val ikut = diikuti.any { it.url == p.url }
    val adaRak = semuaRak.any { it.channel == p.url }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Status.tutupPenyanyi() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Box(Modifier.weight(1f)) { KepalaPenyanyi(p.nama.ifEmpty { "Penyanyi" }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ikut) {
                    OutlinedButton(onClick = { Status.alihIkuti(p.nama, p.url) }) { Text("✓ Diikuti") }
                } else {
                    Button(onClick = { Status.alihIkuti(p.nama, p.url) }) { Text("Ikuti") }
                }
                OutlinedButton(onClick = { Status.alihRakPenyanyi(p) }) { Text(if (adaRak) "✓ Rak di Beranda" else "+ Rak Beranda") }
            }
        }
        h.galat?.let { Text(it, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.secondary) }
        if (h.memuat && h.kini.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        val daftar = h.kini
        GulirKolom {
            if (daftar.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { Pemutar.putar(daftar) }) { Text("Putar semua") }
                        OutlinedButton(onClick = { Pemutar.putarAcak(daftar) }) { Text("Acak") }
                    }
                }
            }
            itemsIndexed(daftar, key = { _, l -> l.id }) { i, l ->
                Box(Modifier.padding(horizontal = 12.dp)) { BarisLagu(l, onKlik = { Pemutar.putar(daftar, i) }, nomor = i + 1) }
            }
            item { TombolHalaman(h) }
        }
    }
}

// ---------------------------------------------------------------------- durasi minimal
val PILIHAN_MIN = listOf(0 to "Semua", 30 to "30 dtk", 60 to "1 mnt", 90 to "1,5 mnt", 120 to "2 mnt")
private val PILIHAN_MIN_RAK = listOf(-1 to "Ikut pengaturan") + PILIHAN_MIN

/** "1–5 mnt", "≥ 1 mnt", "≤ 5 mnt", atau kosong. */
fun labelDurasi(minDetik: Int, maksMenit: Int): String {
    val min = when {
        minDetik <= 0 -> ""
        minDetik % 60 == 0 -> "${minDetik / 60}"
        minDetik < 60 -> "$minDetik dtk"
        else -> "%.1f".format(minDetik / 60.0).replace('.', ',')
    }
    return when {
        min.isNotEmpty() && maksMenit > 0 -> "$min–$maksMenit mnt"
        min.isNotEmpty() -> if (min.endsWith("dtk")) "≥ $min" else "≥ $min mnt"
        maksMenit > 0 -> "≤ $maksMenit mnt"
        else -> ""
    }
}
