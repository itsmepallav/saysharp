package com.saysharp.saysharp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import com.saysharp.shared.session.*
import com.saysharp.shared.ui.SaySharpApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PendingSaveFlowTest {
    @get:Rule val compose=createComposeRule()
    @Test fun failedRecoveryCanResumeFromTodayAndRetryMetadataWithoutRecordingAgain() {
        val first=RecordingRef("first",35000)
        val retry=RecordingRef("retry",35000)
        val store=object:ProgressRepository {
            var value=PracticeSession(stage=PracticeStage.Retry,recordingInProgress=true,
                recordings=mapOf(RecordingSlot.First to first),firstAssessment=SelfAssessment.Unsure)
            var fail=true
            override fun load()=value
            override fun save(session:SessionProgress){check(!fail);value=session}
        }
        val audio=object:PracticeAudio {
            var pending:RecordingRef?=retry
            override fun pendingRecording()=pending
            override fun acknowledgeRecording(recording:RecordingRef){if(pending==recording)pending=null}
            override fun exists(recording:RecordingRef)=true
            override fun start(limitMillis:Long,onLimit:()->Unit,onError:()->Unit)=error("No rerecord needed")
            override fun stop():RecordingRef=error("No rerecord needed")
            override fun cancel(){}
            override fun delete(recording:RecordingRef){}
            override fun deleteAllRecordings(){}
            override fun monotonicMillis()=0L
            override fun play(recording:RecordingRef,openingOnly:Boolean,onFinished:()->Unit,onError:()->Unit){}
            override fun stopPlayback(){}
        }
        val controller=PracticeController(audio,store)
        compose.setContent { SaySharpApp(controller,controller::startRecording) }
        compose.onNodeWithText("Resume practice").performClick()
        compose.onNodeWithText("Retry save").assertExists()
        compose.runOnIdle { store.fail=false }
        compose.onNodeWithText("Retry save").performClick()
        compose.onNodeWithText("Compare my attempts").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(first,store.value.recordings[RecordingSlot.First])
            assertEquals(retry,store.value.recordings[RecordingSlot.Retry])
            assertEquals(SelfAssessment.Unsure,store.value.firstAssessment)
            assertNull(audio.pending)
        }
    }
}
