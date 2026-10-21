package com.example.audio

import com.kyawgyi.voice.AudioBufferPacket
import com.kyawgyi.voice.AudioCaptureModule
import com.kyawgyi.voice.AudioCaptureModuleState

/**
 * Type aliases bridging [com.example.audio] and [com.kyawgyi.voice] packages
 * for [AudioCaptureModule] streaming audio buffers.
 */
typealias KyawGyiAudioCaptureModule = AudioCaptureModule
typealias KyawGyiAudioBufferPacket = AudioBufferPacket
typealias KyawGyiAudioCaptureState = AudioCaptureModuleState
