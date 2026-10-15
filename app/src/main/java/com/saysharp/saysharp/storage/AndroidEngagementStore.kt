package com.saysharp.saysharp.storage

import android.util.AtomicFile
import com.saysharp.shared.engagement.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

class AndroidEngagementStore(directory: File) : EngagementStore {
    private val file = AtomicFile(File(directory.apply { mkdirs() }, "engagement.json"))
    override fun load(): EngagementState {
        val bytes = try { file.readFully() } catch (error: FileNotFoundException) {
            if (!file.baseFile.exists()) return EngagementState() else throw error
        }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        check(json.getInt("version") == 1)
        val acknowledgements = json.getJSONArray("acknowledged")
        val acknowledged = (0 until acknowledgements.length()).map { acknowledgements.getInt(it) }.toSet()
        require(acknowledged.all { value -> Milestones.any { it.sessions == value } })
        return EngagementState(json.getBoolean("welcomed"), SpeakingGoal.valueOf(json.getString("goal")), acknowledged)
    }

    override fun save(state: EngagementState) {
        val bytes = JSONObject().put("version", 1).put("welcomed", state.welcomed)
            .put("goal", state.goal.name).put("acknowledged", JSONArray(state.acknowledged.sorted()))
            .toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
