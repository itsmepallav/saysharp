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

class ReflectionFlowTest {
    @get:Rule val compose = createComposeRule()
    private val store = object : ProgressRepository {
        val refs = mapOf(RecordingSlot.First to RecordingRef("first",35000),RecordingSlot.Retry to RecordingRef("retry",35000),RecordingSlot.Application to RecordingRef("transfer",35000))
        var value=PracticeSession(stage=PracticeStage.Apply,recordings=refs,firstAssessment=SelfAssessment.Unsure,listenedRecordingIds=setOf("first"))
        var fail=false
        override fun load()=value
        override fun save(session:SessionProgress){check(!fail);value=session}
    }
    private fun show() {
        val c=PracticeController(ReflectionAudio(),store)
        compose.setContent { SaySharpApp(c,c::startRecording) }
        click("Resume practice");click("Finish my practice")
    }
    private fun click(label:String) {
        val node=compose.onNodeWithText(label)
        if(!node.isDisplayed())node.performScrollTo()
        node.performClick()
    }
    @Test fun blankReflectionFinishesWithoutAnotherMandatoryStep() {
        show();click("Finish session")
        compose.runOnIdle { assertEquals(PracticeStage.Complete,store.value.stage);assertEquals("",store.value.realLifeWin);assertEquals(setOf(1),store.value.completedDays.keys) }
    }
    @Test fun typedReflectionSavesWithCompletedReviewAndFailedWriteCanRetry() {
        show()
        compose.onNodeWithText("Where could you use today's skill? (optional)").performScrollTo().performTextInput("Explaining my plan tomorrow")
        compose.runOnIdle { store.fail=true }
        click("Finish session")
        compose.onNodeWithText("Finish session").assertExists()
        compose.runOnIdle { assertEquals(PracticeStage.Complete,store.value.stage);assertEquals("",store.value.realLifeWin);store.fail=false }
        click("Finish session")
        compose.runOnIdle { assertEquals("Explaining my plan tomorrow",store.value.completedDays.getValue(1).realLifeWin);assertEquals(store.value.realLifeWin,store.value.completedDays.getValue(1).realLifeWin) }
    }
    @Test fun skipDoesNotUndoCompletionOrRequireSavingADraft() {
        show()
        compose.onNodeWithText("Where could you use today's skill? (optional)").performScrollTo().performTextInput("Uncommitted draft")
        click("Skip reflection")
        compose.runOnIdle { assertEquals(setOf(1),store.value.completedDays.keys);assertEquals("",store.value.realLifeWin) }
    }
}
private class ReflectionAudio:PracticeAudio {
    override fun start(limitMillis:Long,onLimit:()->Unit,onError:()->Unit){}
    override fun stop()=RecordingRef("unused",35000)
    override fun cancel(){}
    override fun exists(recording:RecordingRef)=true
    override fun delete(recording:RecordingRef){}
    override fun deleteAllRecordings(){}
    override fun monotonicMillis()=0L
    override fun play(recording:RecordingRef,openingOnly:Boolean,onFinished:()->Unit,onError:()->Unit){}
    override fun stopPlayback(){}
}
