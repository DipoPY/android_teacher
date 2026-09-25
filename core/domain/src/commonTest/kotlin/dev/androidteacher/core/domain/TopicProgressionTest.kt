package dev.androidteacher.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopicProgressionTest {

    private val progression = TopicProgression(testContent)

    @Test
    fun firstTopicOfEverySectionIsOpen() {
        val sections = progression.sections(Progress())
        assertEquals(listOf(TopicStatus.Available, TopicStatus.Locked, TopicStatus.Locked), sections[0].topics.map { it.status })
        assertEquals(TopicStatus.Available, sections[1].topics[0].status)
    }

    @Test
    fun passingTopicUnlocksTheNextOne() {
        val progress = Progress(passedTopics = mapOf("kotlin.a" to DAY0))
        val kotlin = progression.section("kotlin", progress)!!
        assertEquals(listOf(TopicStatus.Passed, TopicStatus.Available, TopicStatus.Locked), kotlin.topics.map { it.status })
        assertEquals(33, kotlin.percent)
        assertEquals("kotlin.b", kotlin.nextTopic?.id)
    }

    @Test
    fun sectionIsCompleteWhenAllTopicsPassed() {
        val progress = Progress(passedTopics = listOf("kotlin.a", "kotlin.b", "kotlin.c").associateWith { DAY0 })
        val kotlin = progression.section("kotlin", progress)!!
        assertTrue(kotlin.isComplete)
        assertEquals(100, kotlin.percent)
        assertNull(kotlin.nextTopic)
        assertEquals(75, progression.overallPercent(progress))
        assertFalse(progression.allPassed(progress))
    }

    @Test
    fun nextTopicAfterLastIsNull() {
        assertEquals("kotlin.b", progression.nextTopicAfter("kotlin.a")?.id)
        assertNull(progression.nextTopicAfter("kotlin.c"))
    }
}
