package id.ruangputar

import id.ruangputar.inti.Jaringan
import id.ruangputar.inti.Penyimpanan
import id.ruangputar.inti.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Hal-hal yang dipakai seluruh aplikasi: folder, data, dan scope coroutine. */
object App {
    val windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** %APPDATA%\RuangPutar (sama dengan Ruang Putar lama) atau ~/.config/ruangputar. */
    val folderData: File = folderDataAwal()

    /** Cache lagu (bisa dihapus kapan saja): %LOCALAPPDATA%\RuangPutar\musik atau ~/.cache/ruangputar/musik. */
    val folderCache: File = if (windows) {
        File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "RuangPutar${File.separator}musik")
    } else {
        File(System.getenv("XDG_CACHE_HOME") ?: "${System.getProperty("user.home")}/.cache", "ruangputar/musik")
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var data: Penyimpanan
        private set

    fun mulai() {
        folderData.mkdirs()
        folderCache.mkdirs()
        YouTube.mulai()
        data = Penyimpanan(File(folderData, "musik.json"), scope)
        scope.launch {
            data.pengaturan.collect { Jaringan.pembatas.batas = it.batasByte }
        }
    }

}
