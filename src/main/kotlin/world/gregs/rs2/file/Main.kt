package world.gregs.rs2.file

import com.displee.cache.CacheLibrary
import com.github.michaelbull.logging.InlineLogger
import java.awt.GraphicsEnvironment
import java.io.File
import java.math.BigInteger
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream
import javax.swing.JOptionPane
import kotlin.concurrent.thread

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val logger = InlineLogger()
        val start = System.currentTimeMillis()
        logger.info { "Start up..." }
        val file = File("./file-server.properties")
        if (!file.exists()) {
            logger.error { "Unable to find server properties file." }
            return
        }

        var revision = 0
        var port = 0
        var threads = 0
        lateinit var cachePath: String
        lateinit var modulus: BigInteger
        lateinit var exponent: BigInteger
        var prefetchKeys: IntArray = intArrayOf()
        file.forEachLine { line ->
            val (key, value) = line.split("=")
            when (key) {
                "revision" -> revision = value.toInt()
                "port" -> port = value.toInt()
                "threads" -> threads = value.toInt()
                "cachePath" -> cachePath = value
                "rsaModulus" -> modulus = BigInteger(value, 16)
                "rsaPrivate" -> exponent = BigInteger(value, 16)
                "prefetchKeys" -> prefetchKeys = value.split(",").map { it.toInt() }.toIntArray()
            }
        }
        logger.info { "Settings loaded." }

        // Verify the cache files are present before proceeding
        val cacheDirectory = Paths.get(cachePath)
        if (!verifyCacheFiles(cacheDirectory)) {
            val userResponse =
                if (isDesktopAvailable()) {
                    promptUserWithDialog()
                } else {
                    println("No desktop environment detected. Defaulting to yes for cache download.")
                    "yes"
                }

            if (userResponse.equals("yes", ignoreCase = true)) {
                downloadAndInstallCache(cacheDirectory)
            } else {
                println("Cache download declined. Exiting.")
                return
            }
        }

        val cache = CacheLibrary(cachePath)
        val versionTable = cache.generateNewUkeys(exponent, modulus)
        logger.debug { "Version table generated: ${versionTable.contentToString()}" }

        if (prefetchKeys.isEmpty()) {
            prefetchKeys = generatePrefetchKeys(cache)
            logger.debug { "Prefetch keys generated: ${prefetchKeys.contentToString()}" }
        }
        logger.info { "Cache loaded." }

        val fileServer = FileServer(DataProvider(cache), versionTable)
        val network = Network(fileServer, prefetchKeys, revision)
        logger.info { "Loading complete [${System.currentTimeMillis() - start}ms]" }
        val runtime = Runtime.getRuntime()
        runtime.addShutdownHook(thread(start = false) { network.stop() })
        network.start(port, threads)
    }

    private fun isDesktopAvailable(): Boolean = !GraphicsEnvironment.isHeadless()

    private fun promptUserWithDialog(): String {
        val options = arrayOf("Yes", "No")
        val response =
            JOptionPane.showOptionDialog(
                null,
                "The cache path directory is missing required files. Do you want to download and install the cache?",
                "Cache Download",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0],
            )
        return if (response == JOptionPane.YES_OPTION) "yes" else "no"
    }

    private fun downloadAndInstallCache(cachePath: Path) {
        val cacheMirrors =
            listOf(
                URL("https://2011scape.com/downloads/cache.zip"),
                URL("https://archive.openrs2.org/caches/runescape/278/disk.zip"),
            )
        val zipFile = cachePath.resolve("cache.zip")

        for (cacheUrl in cacheMirrors) {
            try {
                Files.createDirectories(cachePath) // Ensure the cache directory exists
                println("Downloading cache from $cacheUrl.")

                // Download with progress
                downloadFileWithProgress(cacheUrl, zipFile)

                println("Download complete. Unzipping cache...")
                unzip(zipFile, cachePath.parent) // Extract to parent directory
                Files.delete(zipFile)
                println("Unzip complete.")
                return // Exit the loop once download and extraction are successful
            } catch (e: Exception) {
                println("Failed to download from $cacheUrl. Trying next URL if available.")
                e.printStackTrace()
            }
        }

        println("All cache download attempts failed.")
    }

    private fun downloadFileWithProgress(
        url: URL,
        destination: Path,
    ) {
        url.openStream().use { input ->
            Files.newOutputStream(destination, StandardOpenOption.CREATE).use { output ->
                val buffer = ByteArray(1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                val fileSize = url.openConnection().contentLengthLong

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    printProgress(totalBytesRead, fileSize)
                }
                println() // Move to the next line after download completes
            }
        }
    }

    private fun printProgress(
        bytesRead: Long,
        totalBytes: Long,
    ) {
        val progress = (bytesRead * 100) / totalBytes
        print("\rDownloading: $progress%")
    }

    private fun unzip(
        zipFilePath: Path,
        destDir: Path,
    ) {
        ZipInputStream(Files.newInputStream(zipFilePath)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name
                val normalizedPath = Paths.get(entryName).normalize()
                val filePath = destDir.resolve(normalizedPath)

                if (!entry.isDirectory) {
                    Files.createDirectories(filePath.parent)
                    Files.copy(zip, filePath, StandardCopyOption.REPLACE_EXISTING)
                    println("Extracted file: $filePath")
                } else {
                    Files.createDirectories(filePath)
                    println("Created directory: $filePath")
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun verifyCacheFiles(filestore: Path): Boolean {
        val requiredFiles =
            listOf(
                "main_file_cache.dat2",
                "main_file_cache.idx0",
                "main_file_cache.idx1",
                "main_file_cache.idx10",
                "main_file_cache.idx11",
                "main_file_cache.idx12",
                "main_file_cache.idx13",
                "main_file_cache.idx14",
                "main_file_cache.idx15",
                "main_file_cache.idx16",
                "main_file_cache.idx17",
                "main_file_cache.idx18",
                "main_file_cache.idx19",
                "main_file_cache.idx2",
                "main_file_cache.idx20",
                "main_file_cache.idx21",
                "main_file_cache.idx22",
                "main_file_cache.idx23",
                "main_file_cache.idx24",
                "main_file_cache.idx25",
                "main_file_cache.idx255",
                "main_file_cache.idx26",
                "main_file_cache.idx27",
                "main_file_cache.idx28",
                "main_file_cache.idx29",
                "main_file_cache.idx3",
                "main_file_cache.idx30",
                "main_file_cache.idx31",
                "main_file_cache.idx32",
                "main_file_cache.idx33",
                "main_file_cache.idx34",
                "main_file_cache.idx35",
                "main_file_cache.idx36",
                "main_file_cache.idx4",
                "main_file_cache.idx5",
                "main_file_cache.idx6",
                "main_file_cache.idx7",
                "main_file_cache.idx8",
                "main_file_cache.idx9",
            )

        for (file in requiredFiles) {
            val filePath = filestore.resolve(file)
            if (Files.notExists(filePath)) {
                return false
            }
        }
        return true
    }
}
