package dev.androidteacher.core.data

import dev.androidteacher.core.domain.Achievement
import dev.androidteacher.core.domain.Progress
import dev.androidteacher.core.platform.InMemoryKeyValueStore
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressRepositoryTest {

    @Test
    fun updatesArePersistedAndRestored() {
        val store = InMemoryKeyValueStore()
        ProgressRepository(store).update {
            it.copy(xp = 40, passedTopics = mapOf("kotlin.a" to LocalDate(2026, 9, 1)), achievements = setOf(Achievement.FirstTopic))
        }
        val restored = ProgressRepository(store).progress.value
        assertEquals(40, restored.xp)
        assertEquals(LocalDate(2026, 9, 1), restored.passedTopics["kotlin.a"])
        assertEquals(setOf(Achievement.FirstTopic), restored.achievements)
    }

    @Test
    fun corruptedSnapshotIsBackedUpInsteadOfLost() {
        val store = InMemoryKeyValueStore(mapOf(ProgressRepository.KEY to "{broken"))
        val repository = ProgressRepository(store)
        assertEquals(Progress(), repository.progress.value)
        assertEquals("{broken", store.get(ProgressRepository.BACKUP_KEY))
    }

    @Test
    fun snapshotFromNewerAppIsNotOverwrittenSilently() {
        val newer = """{"schemaVersion":99,"xp":10}"""
        val store = InMemoryKeyValueStore(mapOf(ProgressRepository.KEY to newer))
        ProgressRepository(store)
        assertEquals(newer, store.get(ProgressRepository.BACKUP_KEY))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val store = InMemoryKeyValueStore(mapOf(ProgressRepository.KEY to """{"schemaVersion":1,"xp":7,"futureField":true}"""))
        assertEquals(7, ProgressRepository(store).progress.value.xp)
    }

    @Test
    fun exportImportRoundTrip() {
        val source = ProgressRepository(InMemoryKeyValueStore())
        source.update { it.copy(xp = 123) }
        val target = ProgressRepository(InMemoryKeyValueStore())
        assertTrue(target.import(source.export()).isSuccess)
        assertEquals(123, target.progress.value.xp)
        assertTrue(target.import("not json").isFailure)
        assertEquals(123, target.progress.value.xp)
    }

    @Test
    fun resetClearsProgress() {
        val store = InMemoryKeyValueStore()
        val repository = ProgressRepository(store)
        repository.update { it.copy(xp = 5) }
        repository.reset()
        assertEquals(0, repository.progress.value.xp)
        assertEquals(null, store.get(ProgressRepository.KEY))
    }
}
