package dev.androidteacher.core.platform

/** Запись ответа с микрофона. */
interface AudioRecorder {
    /** Запрашивает доступ к микрофону и начинает запись. `false` — нет доступа или браузер не поддерживает запись. */
    suspend fun start(): Boolean

    /** Останавливает запись. `null`, если запись не шла. */
    suspend fun stop(): AudioRecording?
}

/** Готовая запись. Живёт в памяти до [release] или перезагрузки страницы. */
interface AudioRecording {
    val durationMillis: Long

    /** Текущая позиция воспроизведения, мс. */
    val positionMillis: Long

    val isPlaying: Boolean

    fun play()

    fun pause()

    fun release()
}

/** Заглушка для платформ без записи и для тестов. */
object NoAudioRecorder : AudioRecorder {
    override suspend fun start(): Boolean = false

    override suspend fun stop(): AudioRecording? = null
}
