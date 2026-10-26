package id.ruangputar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import id.ruangputar.inti.Catatan
import id.ruangputar.inti.Impor
import id.ruangputar.inti.Mesin
import id.ruangputar.inti.Pembaruan
import id.ruangputar.inti.Pemutar
import id.ruangputar.inti.Proksi
import id.ruangputar.ui.BarPemutar
import id.ruangputar.ui.DialogPembaruan
import id.ruangputar.ui.Ikon
import id.ruangputar.ui.LayarBeranda
import id.ruangputar.ui.LayarCari
import id.ruangputar.ui.LayarPengaturan
import id.ruangputar.ui.LayarPenyanyi
import id.ruangputar.ui.LayarPustaka
import id.ruangputar.ui.PanelAntrean
import id.ruangputar.ui.Status
import id.ruangputar.ui.Tab
import id.ruangputar.ui.TemaRuangPutar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.system.exitProcess

/** Satu Ruang Putar saja: pembukaan kedua mengirim pesan ke yang sudah jalan lalu keluar. */
private object SatuJendela {
    private const val PORT = 47392
    private var server: ServerSocket? = null
    var saatPesan: (String) -> Unit = {}

    fun ambil(tunggu: Boolean): Boolean {
        val batas = System.currentTimeMillis() + if (tunggu) 15_000 else 0
        while (true) {
            try {
                server = ServerSocket(PORT, 4, InetAddress.getLoopbackAddress())
                Thread({ dengar() }, "satu-jendela").apply { isDaemon = true }.start()
                return true
            } catch (e: Exception) {
                if (System.currentTimeMillis() > batas) return false
                Thread.sleep(300)
            }
        }
    }

    fun kirim(pesan: String) {
        runCatching {
            Socket(InetAddress.getLoopbackAddress(), PORT).use { s ->
                s.getOutputStream().write((pesan + "\n").toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun dengar() {
        val s = server ?: return
        while (true) {
            try {
                s.accept().use { k ->
                    val baris = k.getInputStream().bufferedReader(Charsets.UTF_8).readLine() ?: return@use
                    Status.scope.launch { saatPesan(baris) }
                }
            } catch (e: Exception) {
                if (s.isClosed) return
            }
        }
    }

    fun lepas() = runCatching { server?.close() }
}

/** Keluar dari aplikasi dengan rapi. */
fun keluarAplikasi() {
    runCatching { App.data.simpanSekarang() }
    runCatching { Pemutar.tutup() }
    mesinBerhasilDibuka()
    SatuJendela.lepas()
    exitProcess(0)
}

/** Argumen: link YouTube atau file musik ("Buka dengan Ruang Putar"). */
private fun bukaArgumen(arg: List<String>) {
    val isi = arg.filterNot { it.startsWith("--") }
    isi.firstOrNull { it.startsWith("http") }?.let { Status.bukaTautan(it) }
    val file = isi.map { File(it) }.filter { it.isFile }
    if (file.isNotEmpty()) {
        Pemutar.putar(file.map { f ->
            val (artis, judul) = f.nameWithoutExtension.split(" - ", limit = 2).let { if (it.size == 2) it[0] to it[1] else "" to f.nameWithoutExtension }
            id.ruangputar.inti.Lagu(id = "", judul = judul, artis = artis, uri = f.absolutePath)
        })
    }
}

fun mulaiAplikasi(args: Array<String>) {
    val mulaiUlang = args.contains("--mulai-ulang")
    if (!SatuJendela.ambil(tunggu = mulaiUlang)) {
        SatuJendela.kirim(if (args.isEmpty()) "tampil" else "buka\t" + args.joinToString("\t"))
        exitProcess(0)
    }
    App.mulai()
    Proksi.mulai()
    Pemutar.mulai()

    application(exitProcessOnExit = false) {
        val pengaturan by App.data.pengaturan.collectAsState()
        var terlihat by remember { mutableStateOf(true) }
        val keadaan = rememberWindowState(size = DpSize(1240.dp, 800.dp))
        val tray = rememberTrayState()
        val ikon = remember { BitmapPainter(useResource("ikon.png", ::loadImageBitmap)) }
        val snack = remember { SnackbarHostState() }

        fun tampilkan() {
            terlihat = true
            keadaan.isMinimized = false
        }

        LaunchedEffect(Unit) {
            SatuJendela.saatPesan = { p ->
                tampilkan()
                if (p.startsWith("buka\t")) bukaArgumen(p.removePrefix("buka\t").split('\t'))
            }
            Pemutar.kabar = { Status.kabar(it) }
            bukaArgumen(args.toList())
            // data Ruang Putar lama (yt-dlp) -> impor sekali
            if (!App.data.pengaturan.value.sudahImpor && Impor.ada()) {
                launch(Dispatchers.IO) {
                    val hasil = runCatching { Impor.jalankan() }.getOrElse { "Impor data lama gagal: ${it.message}" }
                    Status.isiRak.clear()
                    hasil?.let { Status.kabar(it) }
                }
            } else if (!App.data.pengaturan.value.sudahImpor) {
                App.data.ubahPengaturan { it.copy(sudahImpor = true) }
            }
            delay(3000)
            Pembaruan.cekOtomatis()
            if (Mesin.gagalSebelumnya != null) Status.kabar("Mesin YouTube baru gagal dipakai — kembali ke mesin bawaan.")
            delay(5000)
            mesinBerhasilDibuka()
        }
        DisposableEffect(terlihat, pengaturan.notifikasi) {
            Pemutar.saatGanti = { lagu ->
                if (!terlihat && pengaturan.notifikasi) {
                    tray.sendNotification(Notification(lagu.judul, lagu.artis.ifEmpty { "Ruang Putar" }, Notification.Type.None))
                }
            }
            onDispose { }
        }
        LaunchedEffect(Unit) {
            Status.pesan.collect { snack.showSnackbar(it, duration = SnackbarDuration.Short) }
        }

        Tray(
            icon = ikon,
            state = tray,
            tooltip = Pemutar.laguKini?.let { "${it.judul} — ${it.artis}" } ?: "Ruang Putar",
            onAction = { tampilkan() },
            menu = {
                Item("Tampilkan Ruang Putar", onClick = { tampilkan() })
                Separator()
                Item(if (Pemutar.main) "Jeda" else "Putar", onClick = { Pemutar.putarJeda() })
                Item("Berikutnya", onClick = { Pemutar.berikutnya() })
                Item("Sebelumnya", onClick = { Pemutar.sebelumnya() })
                Separator()
                Item("Keluar", onClick = { keluarAplikasi() })
            },
        )

        Window(
            onCloseRequest = {
                if (pengaturan.tutupKeTray) {
                    terlihat = false
                    if (Catatan.ambil("tray_dijelaskan") != "1") {
                        Catatan.simpan("tray_dijelaskan", "1")
                        tray.sendNotification(Notification("Ruang Putar masih jalan", "Musik tetap diputar. Klik ikon di tray untuk membuka lagi.", Notification.Type.Info))
                    }
                } else {
                    keluarAplikasi()
                }
            },
            visible = terlihat,
            state = keadaan,
            title = Pemutar.laguKini?.let { "${it.judul} — Ruang Putar" } ?: "Ruang Putar",
            icon = ikon,
            onKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) return@Window false
                when {
                    e.isCtrlPressed && e.key == Key.F -> { Status.buka(Tab.CARI); true }
                    e.key == Key.Spacebar -> { Pemutar.putarJeda(); true }
                    e.key == Key.DirectionLeft -> { Pemutar.geser(-5.0); true }
                    e.key == Key.DirectionRight -> { Pemutar.geser(5.0); true }
                    e.key == Key.DirectionUp -> { Pemutar.aturVolume(Pemutar.volume + 5); true }
                    e.key == Key.DirectionDown -> { Pemutar.aturVolume(Pemutar.volume - 5); true }
                    e.key == Key.MediaPlayPause -> { Pemutar.putarJeda(); true }
                    e.key == Key.MediaNext -> { Pemutar.berikutnya(); true }
                    e.key == Key.MediaPrevious -> { Pemutar.sebelumnya(); true }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(980, 620) }
            LaunchedEffect(terlihat) { if (terlihat) window.toFront() }
            TemaRuangPutar(pengaturan.tema) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        BilahSamping()
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            val p = Status.penyanyiDibuka
                            val h = Status.halamanPenyanyi
                            if (p != null && h != null) {
                                LayarPenyanyi(p, h)
                            } else {
                                when (Status.tab) {
                                    Tab.BERANDA -> LayarBeranda()
                                    Tab.CARI -> LayarCari()
                                    Tab.PUSTAKA -> LayarPustaka()
                                    Tab.PENGATURAN -> LayarPengaturan()
                                }
                            }
                            Pemutar.galatMesin?.let { g ->
                                Text(
                                    "⚠ Mesin pemutar (mpv) tidak bisa dimuat: $g",
                                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
                        }
                        if (Status.panelAntrean) PanelAntrean()
                    }
                    BarPemutar()
                }
                DialogPembaruan()
            }
        }
    }
}

@Composable
private fun BilahSamping() {
    val penyanyi by App.data.penyanyi.collectAsState()
    Column(
        Modifier.width(232.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ikon.Nada, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Ruang Putar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        val terpilih = if (Status.penyanyiDibuka != null) null else Status.tab
        val warna = NavigationDrawerItemDefaults.colors(unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer)
        NavigationDrawerItem(label = { Text("Beranda") }, selected = terpilih == Tab.BERANDA, onClick = { Status.buka(Tab.BERANDA) },
            icon = { Icon(Icons.Filled.Home, null) }, colors = warna)
        NavigationDrawerItem(label = { Text("Cari") }, selected = terpilih == Tab.CARI, onClick = { Status.buka(Tab.CARI) },
            icon = { Icon(Icons.Filled.Search, null) }, colors = warna)
        NavigationDrawerItem(label = { Text("Pustaka") }, selected = terpilih == Tab.PUSTAKA, onClick = { Status.buka(Tab.PUSTAKA) },
            icon = { Icon(Ikon.Pustaka, null) }, colors = warna)
        NavigationDrawerItem(label = { Text("Pengaturan") }, selected = terpilih == Tab.PENGATURAN, onClick = { Status.buka(Tab.PENGATURAN) },
            icon = { Icon(Icons.Filled.Settings, null) }, colors = warna)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("PENYANYI", Modifier.padding(start = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (penyanyi.isEmpty()) {
                Text("Klik kanan lagu → Ikuti", Modifier.padding(start = 12.dp, top = 4.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            penyanyi.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { Status.bukaPenyanyi(p.nama, p.url) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Person, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(p.nama, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (Status.penyanyiDibuka?.url == p.url) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("v${Versi.NAMA} · @SuS_X_x", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

