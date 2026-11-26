package id.ruangputar.inti

import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import id.ruangputar.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tombol media keyboard (Putar/Jeda, Lagu berikutnya, Sebelumnya, Berhenti) — termasuk yang dikirim
 * NumDeck atau tombol Fn + media — untuk mengendalikan Ruang Putar.
 *
 * Mode (Pengaturan → tombolMedia):
 *  • otomatis : dipakai Ruang Putar saat sedang memutar, atau dijeda kurang dari 10 menit lalu.
 *               Selain itu tombol dibiarkan untuk aplikasi lain (mis. YouTube di browser).
 *  • selalu   : selalu dipakai Ruang Putar (selama ada lagu).
 *  • mati     : tidak dipakai.
 * Saat dipakai Ruang Putar, tombolnya ditahan supaya browser tidak ikut putar/jeda.
 */
object TombolMedia {
    private const val VK_BERIKUTNYA = 0xB0
    private const val VK_SEBELUMNYA = 0xB1
    private const val VK_BERHENTI = 0xB2
    private const val VK_PUTAR_JEDA = 0xB3
    private const val BATAS_JEDA_MS = 10 * 60_000L

    @Volatile private var kait: WinUser.HHOOK? = null
    private var proc: WinUser.LowLevelKeyboardProc? = null   // disimpan supaya tidak dibuang GC
    private val ditangani = HashSet<Int>()

    /** Kapan terakhir Ruang Putar sedang memutar (diisi Pemutar). */
    @Volatile var terakhirMain = 0L

    fun mulai() {
        if (!Platform.isWindows() || kait != null) return
        Thread({
            try {
                val p = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
                    val blok = nCode >= 0 && runCatching { olah(wParam.toInt(), info.vkCode) }.getOrDefault(false)
                    if (blok) {
                        WinDef.LRESULT(1)
                    } else {
                        User32.INSTANCE.CallNextHookEx(kait, nCode, wParam, WinDef.LPARAM(Pointer.nativeValue(info.pointer)))
                    }
                }
                proc = p
                kait = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, p, Kernel32.INSTANCE.GetModuleHandle(null), 0)
                if (kait == null) return@Thread
                val pesan = WinUser.MSG()
                while (User32.INSTANCE.GetMessage(pesan, null, 0, 0) > 0) {
                    // hanya untuk menghidupkan hook
                }
            } catch (_: Throwable) {
                // tanpa tombol media pun aplikasi tetap jalan
            } finally {
                kait?.let { User32.INSTANCE.UnhookWindowsHookEx(it) }
                kait = null
            }
        }, "RuangPutar-tombol-media").apply {
            isDaemon = true
            start()
        }
    }

    /** true = tombol ditahan (dipakai Ruang Putar). Dipanggil dari thread hook. */
    private fun olah(pesan: Int, vk: Int): Boolean {
        if (vk !in VK_BERIKUTNYA..VK_PUTAR_JEDA) return false
        val turun = pesan == WinUser.WM_KEYDOWN || pesan == WinUser.WM_SYSKEYDOWN
        if (!turun) return ditangani.remove(vk)
        if (vk in ditangani) return true                 // ditahan (ulang otomatis)
        if (!mauDipakai(vk)) return false
        ditangani.add(vk)
        App.scope.launch(Dispatchers.Main) {
            when (vk) {
                VK_PUTAR_JEDA -> Pemutar.putarJeda()
                VK_BERIKUTNYA -> Pemutar.berikutnya()
                VK_SEBELUMNYA -> Pemutar.sebelumnya()
                VK_BERHENTI -> if (Pemutar.main) Pemutar.putarJeda()
            }
        }
        return true
    }

    private fun mauDipakai(vk: Int): Boolean {
        val mode = App.data.pengaturan.value.tombolMedia
        if (mode == "mati" || Pemutar.laguKini == null) return false
        if (mode == "selalu" || Pemutar.main) return true
        if (vk == VK_BERHENTI) return false
        // dijeda belum lama -> masih dianggap "pemutar yang sedang dipakai"
        return System.currentTimeMillis() - terakhirMain < BATAS_JEDA_MS
    }
}
