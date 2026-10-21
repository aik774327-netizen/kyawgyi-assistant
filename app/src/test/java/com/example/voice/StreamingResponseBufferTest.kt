package com.example.voice

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamingResponseBufferTest {

    private lateinit var buffer: StreamingResponseBuffer

    @Before
    fun setup() {
        buffer = StreamingResponseBuffer()
    }

    @Test
    fun testSentenceBoundarySegmentation() = runBlocking {
        val collectedSentences = mutableListOf<String>()

        val collectJob = launch {
            buffer.sentenceFlow.collect { sentence ->
                collectedSentences.add(sentence)
            }
        }

        // Stream delta chunks from simulated LLM
        buffer.appendTextChunk("မင်္ဂ")
        buffer.appendTextChunk("လာပါခင်ဗျာ။") // Myanmar sentence delimiter '။'
        buffer.appendTextChunk("ကျော်ကြီးကူညီ")
        buffer.appendTextChunk("ပေးပါရစေ။")

        buffer.completeStream()
        collectJob.join()

        assertEquals(2, collectedSentences.size)
        assertEquals("မင်္ဂလာပါခင်ဗျာ။", collectedSentences[0])
        assertEquals("ကျော်ကြီးကူညီပေးပါရစေ။", collectedSentences[1])
    }

    @Test
    fun testFlushOnCompleteStream() = runBlocking {
        val collectedSentences = mutableListOf<String>()

        val collectJob = launch {
            buffer.sentenceFlow.collect { sentence ->
                collectedSentences.add(sentence)
            }
        }

        // Stream text without ending punctuation
        buffer.appendTextChunk("အားလုံး အဆင်ပြေပါတယ်")
        buffer.completeStream()
        collectJob.join()

        assertEquals(1, collectedSentences.size)
        assertEquals("အားလုံး အဆင်ပြေပါတယ်", collectedSentences[0])
        assertTrue(buffer.isCompleted())
    }
}
