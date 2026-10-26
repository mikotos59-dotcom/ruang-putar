package id.ruangputar

import org.json.JSONObject
import java.io.File
import java.net.URLClassLoader

/**
 * Titik awal aplikasi. Kalau ada mesin YouTube (NewPipeExtractor) yang lebih baru di
 * %APPDATA%\RuangPutar\mesin, aplikasi dijalankan dengan file mesin baru itu di depan classpath.
 * Kalau percobaan sebelumnya gagal (aplikasi tidak sempat terbuka), kembali ke mesin bawaan.
 */
fun main(args: Array<String>) {
    val folder = File(folderDataAwal(), "mesin")
    val aktif = File(folder, "aktif.json")
    val mencoba = File(folder, "mencoba")
    try {
        if (aktif.isFile) {
            if (mencoba.exists()) {
                // pembukaan sebelumnya dengan mesin baru tidak selesai -> nonaktifkan
                mencoba.delete()
                val o = runCatching { JSONObject(aktif.readText()) }.getOrNull()
                aktif.renameTo(File(folder, "gagal.json"))
                System.setProperty("ruangputar.mesin.gagal", o?.optString("versi") ?: "?")
            } else {
                val o = JSONObject(aktif.readText())
                val versi = o.getString("versi")
                if (bandingkanVersi(versi, Versi.NEWPIPE) <= 0) {
                    // aplikasi sudah membawa mesin yang sama/lebih baru
                    aktif.delete()
                } else {
                    val dir = File(folder, o.getString("folder"))
                    val baru = (0 until o.getJSONArray("jar").length()).map { File(dir, o.getJSONArray("jar").getString(it)) }
                    if (baru.isNotEmpty() && baru.all { it.isFile }) {
                        val buang = (0 until o.getJSONArray("buang").length()).map { o.getJSONArray("buang").getString(it).removeSuffix(".jar") }
                        val lama = System.getProperty("java.class.path").split(File.pathSeparator)
                            .filter { it.isNotBlank() }
                            .filter { p -> val n = File(p).name; buang.none { b -> n.startsWith(b) } }
                            .map { File(it) }
                        val url = (baru + lama).map { it.toURI().toURL() }.toTypedArray()
                        val loader = URLClassLoader("mesin-$versi", url, ClassLoader.getPlatformClassLoader())
                        System.setProperty("ruangputar.mesin", versi)
                        mencoba.writeText(versi)
                        Thread.currentThread().contextClassLoader = loader
                        loader.loadClass("id.ruangputar.MainKt")
                            .getMethod("mulaiAplikasi", Array<String>::class.java)
                            .invoke(null, args as Any)
                        return
                    }
                }
            }
        }
    } catch (e: Throwable) {
        System.err.println("Mesin baru tidak bisa dipakai: $e")
        mencoba.delete()
        aktif.renameTo(File(folder, "gagal.json"))
        System.clearProperty("ruangputar.mesin")
        System.setProperty("ruangputar.mesin.gagal", "?")
        Thread.currentThread().contextClassLoader = ClassLoader.getSystemClassLoader()
    }
    mulaiAplikasi(args)
}

/** Dipanggil aplikasi setelah jendela tampil normal: mesin baru dianggap berhasil. */
fun mesinBerhasilDibuka() {
    File(File(folderDataAwal(), "mesin"), "mencoba").delete()
}

/** Sama dengan App.folderData, tanpa memuat kelas aplikasi lain. */
internal fun folderDataAwal(): File = if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
    File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "RuangPutar")
} else {
    File(System.getenv("XDG_CONFIG_HOME") ?: "${System.getProperty("user.home")}/.config", "ruangputar")
}

internal fun bandingkanVersi(a: String, b: String): Int {
    fun angka(v: String) = v.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
    val x = angka(a)
    val y = angka(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}
