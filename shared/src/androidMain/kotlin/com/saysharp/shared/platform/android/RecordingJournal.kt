package com.saysharp.shared.platform.android

import com.saysharp.shared.platform.RecordingRef
import java.io.*

/** One finalized-but-uncommitted recording. Same-directory rename publishes a fully synced marker. */
class RecordingJournal(directory: File) {
    private val file = File(directory, "pending-recording.bin")
    private val temporary = File(directory, "pending-recording.tmp")
    fun read(): RecordingRef? = if (!file.exists()) null else DataInputStream(FileInputStream(file)).use {
        check(it.readInt() == 1)
        RecordingRef(it.readUTF(), it.readLong()).also { ref ->
            require(ref.id.matches(Regex("[a-fA-F0-9-]{36}\\.m4a")) && ref.durationMillis >= 0)
        }
    }
    fun write(ref: RecordingRef) {
        file.parentFile!!.mkdirs()
        FileOutputStream(temporary).use { stream ->
            val data = DataOutputStream(stream)
            data.writeInt(1); data.writeUTF(ref.id); data.writeLong(ref.durationMillis)
            data.flush(); stream.fd.sync()
        }
        check(temporary.renameTo(file)) { "Could not commit recording journal" }
    }
    fun acknowledge(ref: RecordingRef) {
        if (read() == ref) check(file.delete()) { "Could not acknowledge recording journal" }
    }
}
