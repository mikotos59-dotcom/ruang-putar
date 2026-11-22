package id.ruangputar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import id.ruangputar.Versi
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Repo GitHub versi HP (Android). */
const val REPO_HP = "mikotos59-dotcom/ruang-putar-musik"

val tautanPc get() = "https://github.com/${Versi.REPO_GITHUB}/releases/latest"
val tautanHp get() = "https://github.com/$REPO_HP/releases/latest"

private fun pesanBagikan() =
    "Coba Ruang Putar — pemutar musik ringan buatan @SuS_X_x.\n" +
        "• PC (Windows): $tautanPc\n" +
        "• HP (Android): $tautanHp\n" +
        "Discord Bengkel SuS: $TAUTAN_DISCORD"

private fun salin(teks: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(teks), null) }
}

@Composable
fun BagianBagikan() {
    var tampilQr by remember { mutableStateOf("hp") }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                "Salin pesan lalu tempel di WhatsApp, Telegram, Discord, dan lainnya. Teman tinggal klik tautannya " +
                    "lalu unduh file di bagian Assets (.exe untuk PC, .apk untuk HP).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { salin(pesanBagikan()); Status.kabar("Pesan bagikan disalin — tinggal tempel (Ctrl+V)") }) {
                    Text("Salin pesan bagikan")
                }
                OutlinedButton(onClick = { salin(tautanPc); Status.kabar("Tautan versi PC disalin") }) { Text("Tautan PC") }
                OutlinedButton(onClick = { salin(tautanHp); Status.kabar("Tautan versi HP disalin") }) { Text("Tautan HP") }
            }
            Text(
                "Atau pindai kode QR di samping pakai kamera HP untuk langsung membuka halaman unduhan.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pilihan("QR versi HP", tampilQr == "hp") { tampilQr = "hp" }
                Pilihan("QR versi PC", tampilQr == "pc") { tampilQr = "pc" }
            }
        }
        KodeQr(if (tampilQr == "hp") tautanHp else tautanPc)
    }
}

@Composable
private fun KodeQr(teks: String) {
    val matriks: BitMatrix? = remember(teks) {
        runCatching {
            QRCodeWriter().encode(teks, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        }.getOrNull()
    }
    Box(
        Modifier.size(168.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (matriks == null) {
            Text("QR gagal", color = Color.Black)
            return@Box
        }
        Canvas(Modifier.size(144.dp)) {
            val n = matriks.width
            val sel = size.minDimension / n
            for (y in 0 until n) for (x in 0 until n) {
                if (matriks.get(x, y)) drawRect(Color.Black, Offset(x * sel, y * sel), Size(sel + 0.5f, sel + 0.5f))
            }
        }
    }
}

/** Tombol kecil di pojok Beranda: membuka jendela Bagikan (pesan, tautan, QR). */
@Composable
fun TombolBagikanBeranda() {
    var buka by remember { mutableStateOf(false) }
    TextButton(onClick = { buka = true }) {
        Icon(Icons.Filled.Share, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Bagikan", Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (buka) {
        AlertDialog(
            onDismissRequest = { buka = false },
            title = { Text("Bagikan Ruang Putar") },
            text = { Box(Modifier.widthIn(max = 640.dp)) { BagianBagikan() } },
            confirmButton = { TextButton(onClick = { buka = false }) { Text("Tutup") } },
        )
    }
}
