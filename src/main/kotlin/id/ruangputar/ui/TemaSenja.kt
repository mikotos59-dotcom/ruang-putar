package id.ruangputar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tema "Senja Neon": senja pantai bergaya retro — langit ungu–pink–oranye, matahari bergaris,
 * siluet pohon kelapa, aksen pink neon. Gambar & nama asli buatan sendiri.
 */
internal val SenjaNeon = darkColorScheme(
    primary = Color(0xFFFF4FA3),
    onPrimary = Color(0xFF2A0518),
    secondary = Color(0xFFFF9E45),
    onSecondary = Color(0xFF2A1400),
    tertiary = Color(0xFF2DE2E6),
    background = Color(0xFF170A2B),
    onBackground = Color(0xFFFFEAF5),
    surface = Color(0xFF1F0F38),
    onSurface = Color(0xFFFFEAF5),
    surfaceVariant = Color(0xFF2E1650),
    onSurfaceVariant = Color(0xFFD9A8D6),
    surfaceContainer = Color(0xFF24113F),
    secondaryContainer = Color(0xFF4A1A5E),
    onSecondaryContainer = Color(0xFFFFD6EC),
    outline = Color(0xFF6B3A8A),
)

fun temaSenja(nama: String) = nama == "senja"

private val Langit = listOf(Color(0xFF2B0F4C), Color(0xFF6A1B78), Color(0xFFE8457F), Color(0xFFFF9E45))
private val Siluet = Color(0xFF14061F)

/** Kepala Beranda tema Senja Neon. */
@Composable
fun KepalaSenja(sapa: String) {
    Box(Modifier.fillMaxWidth().height(180.dp)) {
        Canvas(Modifier.fillMaxSize()) { lukisSenja() }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to MaterialTheme.colorScheme.background)))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
                "Selamat $sapa,",
                style = MaterialTheme.typography.headlineMedium.merge(
                    TextStyle(
                        fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
                        shadow = Shadow(Color(0xFFFF4FA3), Offset(0f, 0f), 18f),
                    ),
                ),
                color = Color.White,
            )
            Text("Musik untukmu hari ini 🌴", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 16.dp)) { TombolBagikanBeranda() }
    }
}

private fun DrawScope.lukisSenja() {
    val w = size.width
    val h = size.height
    val cakrawala = h * 0.70f

    // langit
    drawRect(Brush.verticalGradient(Langit, 0f, cakrawala), size = Size(w, cakrawala))

    // matahari bergaris (retro)
    val pusat = Offset(w * 0.70f, cakrawala)
    val jari = h * 0.46f
    drawCircle(
        Brush.verticalGradient(listOf(Color(0xFFFFE07A), Color(0xFFFF7A59), Color(0xFFFF4FA3)), pusat.y - jari, pusat.y),
        jari, pusat,
    )
    // garis-garis di bagian bawah matahari (hanya di dalam lingkarannya)
    val lingkaran = Path().apply { addOval(Rect(pusat, jari)) }
    clipPath(lingkaran) {
        for (i in 0 until 6) {
            val y = pusat.y - jari * (0.62f - i * 0.11f)
            val tebal = jari * (0.025f + i * 0.012f)
            drawRect(Color(0xFFE8457F), Offset(pusat.x - jari, y), Size(jari * 2, tebal))
        }
    }

    // laut + pantulan matahari
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF3A1257), Color(0xFF170A2B)), cakrawala, h),
        Offset(0f, cakrawala), Size(w, h - cakrawala),
    )
    for (i in 0 until 7) {
        val y = cakrawala + 4f + i * (h - cakrawala) / 7f
        val lebar = jari * (1.3f - i * 0.15f)
        drawRect(
            Color(0xFFFF7A9C).copy(alpha = 0.55f - i * 0.07f),
            Offset(pusat.x - lebar / 2, y), Size(lebar, 2.2f),
        )
    }

    // pohon kelapa
    pohonKelapa(w * 0.06f, h * 1.02f, h * 0.95f, 0.18f)
    pohonKelapa(w * 0.15f, h * 1.05f, h * 0.70f, -0.10f)
    pohonKelapa(w * 0.95f, h * 1.04f, h * 0.85f, -0.22f)
    pohonKelapa(w * 0.40f, h * 1.02f, h * 0.38f, 0.08f)
}

/** Siluet pohon kelapa: batang melengkung + pelepah daun. */
private fun DrawScope.pohonKelapa(dasarX: Float, dasarY: Float, tinggi: Float, condong: Float) {
    val atas = Offset(dasarX + condong * tinggi, dasarY - tinggi)
    val tengah = Offset(dasarX + condong * tinggi * 0.25f, dasarY - tinggi * 0.55f)
    val t = tinggi * 0.035f
    val batang = Path().apply {
        moveTo(dasarX - t, dasarY)
        quadraticTo(tengah.x - t * 0.6f, tengah.y, atas.x - t * 0.35f, atas.y)
        lineTo(atas.x + t * 0.35f, atas.y)
        quadraticTo(tengah.x + t * 0.9f, tengah.y, dasarX + t, dasarY)
        close()
    }
    drawPath(batang, Siluet)

    val panjang = tinggi * 0.42f
    for (derajat in listOf(-175f, -150f, -122f, -95f, -68f, -40f, -12f, 12f)) {
        val r = Math.toRadians(derajat.toDouble())
        val arah = Offset(cos(r).toFloat(), sin(r).toFloat())
        val ujung = atas + arah * panjang + Offset(0f, panjang * 0.38f)      // ujung pelepah terkulai
        val kendali = atas + arah * (panjang * 0.55f) - Offset(0f, panjang * 0.10f)
        val tegak = Offset(-arah.y, arah.x) * (panjang * 0.11f)
        val daun = Path().apply {
            moveTo(atas.x, atas.y)
            quadraticTo(kendali.x + tegak.x, kendali.y + tegak.y, ujung.x, ujung.y)
            quadraticTo(kendali.x - tegak.x * 0.3f, kendali.y - tegak.y * 0.3f, atas.x, atas.y)
            close()
        }
        drawPath(daun, Siluet)
    }
    // buah kelapa
    val buah = tinggi * 0.022f
    drawCircle(Siluet, buah, atas + Offset(-buah, buah * 1.4f))
    drawCircle(Siluet, buah, atas + Offset(buah * 1.1f, buah * 1.6f))
}
