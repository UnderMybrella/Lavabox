package org.abimon.lavabox

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.yield
import org.abimon.kjukebox.InfiniteJukeboxBeat
import org.abimon.kjukebox.InfiniteJukeboxTrack
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object LavaBox {
    var cacheFolder = File(".lavabox")

    val DATA_REGEX = "\\w+-\\w+-\\w+-\\w+-\\w+\\.dat".toRegex()

    @JvmStatic
    fun main(args: Array<String>) {

    }

    fun separateTrack(manager: AudioPlayerManager, player: AudioPlayer, audioPath: String, track: InfiniteJukeboxTrack): Collection<JukeboxFrame> {
        if (!cacheFolder.exists())
            cacheFolder.mkdir()

        val audioFrames = ConcurrentLinkedQueue<JukeboxFrame>()
        val cacheFile = File(cacheFolder, "${track.info.id}.zip")

        if (cacheFile.exists()) {
            try {
                val zip = ZipFile(cacheFile)

                zip.entries().iterator().forEach { entry ->
                    if (entry.name.matches(DATA_REGEX)) {
                        zip.getInputStream(entry).use { stream ->
                            val timecode = stream.readInt64LE()
                            val length = stream.readInt32LE()

                            val data = ByteArray(length)
                            stream.read(data)

                            audioFrames.add(JukeboxFrame(timecode, data))
                        }
                    }
                }
            } catch (io: IOException) {
                cacheFile.delete()
            }
        }

        if (audioFrames.isEmpty()) {
            val isFinished = AtomicBoolean(false)

            manager.loadItem(audioPath, FunctionalResultHandler({ lavaTrack ->
                launch {
                    while (isActive && lavaTrack.state != AudioTrackState.FINISHED) {
                        yield()
                        audioFrames.add(JukeboxFrame(player.provide() ?: continue))
                    }

                    isFinished.set(true)
                }

                player.startTrack(lavaTrack, false)
            }, null, null, null))

            while (!isFinished.get()) Thread.sleep(100)

            ZipOutputStream(FileOutputStream(cacheFile)).use { stream ->
                audioFrames.forEach { (timecode, data) ->
                    stream.putNextEntry(ZipEntry("${UUID.randomUUID()}.dat"))

                    stream.writeInt64LE(timecode)
                    stream.writeInt32LE(data.size)
                    stream.write(data)

                    stream.closeEntry()
                }
            }
        }

        return audioFrames
    }

    fun separateAndGroupTrack(manager: AudioPlayerManager, player: AudioPlayer, audioPath: String, track: InfiniteJukeboxTrack): Pair<Array<JukeboxFrame>, Map<InfiniteJukeboxBeat, Array<JukeboxFrame>>> {
        val frames = separateTrack(manager, player, audioPath, track).sortedBy { frame -> frame.timecode }

        val start: Array<JukeboxFrame> = track.analysis.beats[0].let { beat ->
            val range = 0 until (beat.start * 1000).toLong()
            return@let frames.filter { frame -> frame.timecode in range }.toTypedArray()
        }

        return start to track.analysis.beats.map { beat ->
            val range = (beat.start * 1000).toLong() until ((beat.start * 1000) + (beat.duration * 1000)).toLong()
            return@map beat to frames.filter { frame -> frame.timecode in range }.toTypedArray()
        }.toMap()
    }

    private fun InputStream.readInt64LE(): Long {
        val a = read().toLong()
        val b = read().toLong()
        val c = read().toLong()
        val d = read().toLong()
        val e = read().toLong()
        val f = read().toLong()
        val g = read().toLong()
        val h = read().toLong()

        return (h shl 56) or (g shl 48) or (f shl 40) or (e shl 32) or
                (d shl 24) or (c shl 16) or (b shl 8) or a
    }

    private fun OutputStream.writeInt64LE(num: Number) {
        val long = num.toLong()

        write(long.toInt() and 0xFF)
        write((long shr 8).toInt() and 0xFF)
        write((long shr 16).toInt() and 0xFF)
        write((long shr 24).toInt() and 0xFF)
        write((long shr 32).toInt() and 0xFF)
        write((long shr 40).toInt() and 0xFF)
        write((long shr 48).toInt() and 0xFF)
        write((long shr 56).toInt() and 0xFF)
    }

    private fun InputStream.readInt32LE(): Int {
        val a = read()
        val b = read()
        val c = read()
        val d = read()

        return (d shl 24) or (c shl 16) or (b shl 8) or a
    }

    private fun OutputStream.writeInt32LE(num: Number) {
        val long = num.toLong()

        write(long.toInt() and 0xFF)
        write((long shr 8).toInt() and 0xFF)
        write((long shr 16).toInt() and 0xFF)
        write((long shr 24).toInt() and 0xFF)
    }
}