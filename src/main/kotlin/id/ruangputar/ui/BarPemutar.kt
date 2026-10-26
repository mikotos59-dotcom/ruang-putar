package id.ruangputar.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.ruangputar.inti.Pemutar
import id.ruangputar.inti.Unduhan
import id.ruangputar.inti.formatWaktu

/** Bar pemutar di bawah jendela: lagu, kendali, geser waktu, volume, antrean. */
@Composable
fun BarPemutar() {
    val lagu = Pemutar.laguKini
    val favorit by Status.data.favorit.collectAsState()
    val unduhan by Status.data.unduhan.collectAsState()
    val proses by Unduhan.proses.collectAsState()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            // ----------------------------------------------------------- kiri: lagu
            Row(Modifier.weight(0.3f), verticalAlignment = Alignment.CenterVertically) {
                if (lagu != null) {
                    Sampul(lagu.sampul, 60.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(lagu.judul, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(
                            lagu.artis, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (lagu.channel.isNotEmpty()) Modifier.clickable { Status.bukaPenyanyi(lagu.artis, lagu.channel) } else Modifier,
                        )
                    }
                    val fav = favorit.any { it.kunci == lagu.kunci }
                    IconButton(onClick = { Status.alihFavorit(lagu) }) {
                        Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorit",
                            tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(
                        Pemutar.galatMesin?.let { "⚠ Mesin pemutar tidak siap" } ?: "Pilih lagu untuk diputar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // ----------------------------------------------------------- tengah: kendali + waktu
            Column(Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { Pemutar.alihAcak() }) {
                        Icon(Ikon.Acak, "Acak", tint = if (Pemutar.acak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { Pemutar.sebelumnya() }) { Icon(Ikon.Sebelum, "Sebelumnya") }
                    Surface(
                        onClick = { Pemutar.putarJeda() },
                        shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                Pemutar.memuat -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Pemutar.main -> Icon(Ikon.Jeda, "Jeda", tint = MaterialTheme.colorScheme.onPrimary)
                                else -> Icon(Icons.Filled.PlayArrow, "Putar", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    IconButton(onClick = { Pemutar.berikutnya() }) { Icon(Ikon.Berikut, "Berikutnya") }
                    IconButton(onClick = { Pemutar.alihUlang() }) {
                        Icon(
                            if (Pemutar.ulang == 2) Ikon.UlangSatu else Ikon.Ulang, "Ulang",
                            tint = if (Pemutar.ulang > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                GeserWaktu()
            }
            // ----------------------------------------------------------- kanan: unduh, timer, antrean, volume
            Row(Modifier.weight(0.3f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (lagu != null && !lagu.lokal) {
                    val p = proses[lagu.id]
                    when {
                        p != null -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { p / 100f }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        }
                        unduhan.containsKey(lagu.id) -> IconButton(onClick = { Status.kabar("Sudah ada di Offline") }) {
                            Icon(Ikon.Terunduh, "Sudah diunduh", tint = MaterialTheme.colorScheme.primary)
                        }
                        else -> IconButton(onClick = { Status.unduh(lagu) }) { Icon(Ikon.Unduh, "Unduh ke Offline") }
                    }
                }
                TombolTimer()
                IconButton(onClick = { Status.panelAntrean = !Status.panelAntrean }) {
                    Icon(Ikon.Antrean, "Antrean", tint = if (Status.panelAntrean) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (Pemutar.volume == 0) "🔇" else "🔊", Modifier.padding(start = 4.dp))
                Slider(
                    value = Pemutar.volume / 100f,
                    onValueChange = { Pemutar.aturVolume((it * 100).toInt()) },
                    modifier = Modifier.width(110.dp),
                    colors = SliderDefaults.colors(),
                )
            }
        }
    }
}

@Composable
private fun GeserWaktu() {
    var digeser by remember { mutableStateOf(false) }
    var nilai by remember { mutableFloatStateOf(0f) }
    val durasi = Pemutar.durasi.coerceAtLeast(0.0)
    val pos = if (digeser) nilai.toDouble() else Pemutar.posisi.coerceIn(0.0, maxOf(durasi, 0.0))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatWaktu(pos.toLong()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp))
        Slider(
            value = pos.toFloat(),
            valueRange = 0f..maxOf(durasi.toFloat(), 1f),
            onValueChange = { digeser = true; nilai = it },
            onValueChangeFinished = { Pemutar.geserKe(nilai.toDouble()); digeser = false },
            enabled = Pemutar.laguKini != null && durasi > 0,
            modifier = Modifier.weight(1f).height(24.dp),
        )
        Text(formatWaktu(durasi.toLong()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp).padding(start = 6.dp))
    }
}

@Composable
private fun TombolTimer() {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(Ikon.Timer, "Timer tidur", tint = if (Pemutar.timerSampai > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (Pemutar.timerSampai > 0) {
                val sisa = ((Pemutar.timerSampai - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                Text("Berhenti dalam ± $sisa mnt", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
            listOf(15, 30, 45, 60, 90).forEach { m ->
                DropdownMenuItem(text = { Text("$m menit") }, onClick = { menu = false; Pemutar.setelTimer(m) })
            }
            if (Pemutar.timerSampai > 0) {
                DropdownMenuItem(text = { Text("Matikan timer") }, onClick = { menu = false; Pemutar.setelTimer(0) })
            }
        }
    }
}

/** Panel antrean di kanan. */
@Composable
fun PanelAntrean() {
    val state = rememberLazyListState()
    LaunchedEffect(Status.panelAntrean) { if (Pemutar.indeks > 0) state.scrollToItem(Pemutar.indeks) }
    Column(Modifier.width(340.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Antrean", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { Pemutar.kosongkanAntrean() }, enabled = Pemutar.antrean.size > 1) { Text("Kosongkan") }
            IconButton(onClick = { Status.panelAntrean = false }) { Icon(Icons.Filled.Clear, "Tutup") }
        }
        HorizontalDivider()
        if (Pemutar.antrean.isEmpty()) {
            Kosong("Antrean kosong.")
            return@Column
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 8.dp), state = state) {
                itemsIndexed(Pemutar.antrean) { i, l ->
                    val aktif = i == Pemutar.indeks
                    var menu by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (aktif) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable { Pemutar.putarDiAntrean(i) }
                            .padding(start = 12.dp, end = 0.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sampul(l.sampul, 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(l.judul, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                                color = if (aktif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(l.artis, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { Pemutar.geserDiAntrean(i, i - 1) }, enabled = i > 0, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Naikkan")
                        }
                        IconButton(onClick = { Pemutar.geserDiAntrean(i, i + 1) }, enabled = i < Pemutar.antrean.size - 1, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Turunkan")
                        }
                        IconButton(onClick = { Pemutar.hapusDiAntrean(i) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Clear, "Hapus dari antrean", Modifier.size(18.dp))
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}
