@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.androidteacher.core.platform

import kotlinx.coroutines.await
import kotlin.js.Promise

/** Запись через `MediaRecorder`. Работает только на `https` или `localhost`. */
class BrowserAudioRecorder : AudioRecorder {
    private var startedAt = 0.0

    override suspend fun start(): Boolean {
        val started = runCatching { jsStartRecording().await<JsBoolean>().toBoolean() }.getOrDefault(false)
        if (started) startedAt = jsNow()
        return started
    }

    override suspend fun stop(): AudioRecording? {
        val url = runCatching { jsStopRecording().await<JsString?>() }.getOrNull()?.toString() ?: return null
        return BrowserAudioRecording(url, durationMillis = (jsNow() - startedAt).toLong())
    }
}

private class BrowserAudioRecording(
    private val url: String,
    override val durationMillis: Long,
) : AudioRecording {
    private val audio: JsAny = jsCreateAudio(url)

    override val positionMillis: Long get() = (jsCurrentTime(audio) * 1000).toLong()

    override val isPlaying: Boolean get() = jsIsPlaying(audio)

    override fun play() = jsPlay(audio)

    override fun pause() = jsPause(audio)

    override fun release() {
        jsPause(audio)
        jsRevokeUrl(url)
    }
}

private fun jsNow(): Double = js("Date.now()")

private fun jsStartRecording(): Promise<JsBoolean> = js(
    """(async () => {
        try {
            if (!navigator.mediaDevices || typeof MediaRecorder === 'undefined') return false;
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const chunks = [];
            const recorder = new MediaRecorder(stream);
            recorder.ondataavailable = (e) => { if (e.data && e.data.size > 0) chunks.push(e.data); };
            recorder.start();
            globalThis.__atRecorder = { recorder, stream, chunks };
            return true;
        } catch (e) {
            return false;
        }
    })()""",
)

private fun jsStopRecording(): Promise<JsString?> = js(
    """new Promise((resolve) => {
        const s = globalThis.__atRecorder;
        if (!s) { resolve(null); return; }
        s.recorder.onstop = () => {
            s.stream.getTracks().forEach((t) => t.stop());
            const blob = new Blob(s.chunks, { type: s.recorder.mimeType || 'audio/webm' });
            globalThis.__atRecorder = null;
            resolve(URL.createObjectURL(blob));
        };
        s.recorder.stop();
    })""",
)

private fun jsCreateAudio(url: String): JsAny = js("new Audio(url)")

private fun jsCurrentTime(audio: JsAny): Double = js("audio.currentTime")

private fun jsIsPlaying(audio: JsAny): Boolean = js("!audio.paused && !audio.ended")

private fun jsPlay(audio: JsAny): Unit = js("{ audio.play(); }")

private fun jsPause(audio: JsAny): Unit = js("{ audio.pause(); }")

private fun jsRevokeUrl(url: String): Unit = js("{ URL.revokeObjectURL(url); }")
