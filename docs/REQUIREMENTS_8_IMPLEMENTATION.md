# KyawGyi — Requirements 1–8 implementation map

## 1. Sherpa wrapper
`SherpaOfflineSpeechRecognizer.kt` is a streaming transducer adapter. The files named encoder/decoder/joiner/tokens are a streaming RNN-T/Zipformer layout; the exact Sherpa AAR API must match its pinned release.

## 2. One microphone
`CentralAudioRecorder.kt` owns the only `AudioRecord`. `DirectPcmStreamFeed` fans out its DSP PCM frames to an STT engine. Do not call `AudioRecord` from a second engine.

## 3. Endpointing
`AdaptiveEndpointing.kt` uses RMS voice activity plus 1.2–1.5 s silence bounds. In production, replace RMS with Silero VAD probabilities and calibrate on Burmese/noisy-device test audio.

## 4. Phonetic dictionary
`MyanmarTextNaturalizer.kt` contains the requested technology/social aliases and separates intent normalization from TTS normalization.

## 5. Prosody
`TtsProsodyPreprocessor` marks Myanmar comma/full-stop pauses as 250/500 ms. A PCM TTS renderer must turn these markers into silence frames; Android TextToSpeech cannot guarantee exact millisecond pauses.

## 6–7. Audio focus/failover
Existing `F5MyanmarTtsClient` owns AudioTrack focus. `TtsFallbackChain.kt` adds default/Google → Samsung SMT → chime fallback. In production, use one initialized TTS instance per engine and call AudioFocus abandon in every done/error/cancel path.

## 8. User preferences
`VoicePreferences.kt` persists rate/pitch in SharedPreferences. A Compose Slider can collect `speechRate` and `pitch` and call the setters.

## Integration order

```kotlin
val recorder = CentralAudioRecorder(applicationContext, serviceScope)
val sherpa = SherpaOfflineSpeechRecognizer(
    modelDir = modelDir,
    recorder = recorder,
    scope = serviceScope,
    onPartial = { text -> /* UI */ },
    onFinal = { raw ->
        val normalized = naturalizer.normalizeSpokenDigits(
            naturalizer.normalizeForIntent(raw)
        )
        // send normalized to command handler
    },
)
sherpa.prepare()
recorder.start()
sherpa.start()
```

`SpeechRecognizerRouter.isRecorderRunning()` must be backed by a real `StateFlow` in the app before production; the sample deliberately leaves that platform-specific flag visible rather than silently creating a second microphone.
