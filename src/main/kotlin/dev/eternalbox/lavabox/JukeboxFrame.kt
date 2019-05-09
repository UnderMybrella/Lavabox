package dev.eternalbox.lavabox

import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import java.util.*

data class JukeboxFrame(val timecode: Long, val data: ByteArray) {
    constructor(frame: AudioFrame): this(frame.timecode, frame.data)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JukeboxFrame) return false

        if (timecode != other.timecode) return false
        if (!Arrays.equals(data, other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timecode.hashCode()
        result = 31 * result + Arrays.hashCode(data)
        return result
    }
}