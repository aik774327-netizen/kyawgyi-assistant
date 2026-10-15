package com.example.voice

import android.content.Context
import com.kyawgyi.ir.repository.IrViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

typealias VoiceIntentProcessor = com.kyawgyi.ir.voice.VoiceIntentProcessor
typealias VoiceState = com.kyawgyi.ir.voice.VoiceState

fun createVoiceIntentProcessor(
  context: Context,
  viewModel: IrViewModel,
  scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
): com.kyawgyi.ir.voice.VoiceIntentProcessor {
  return com.kyawgyi.ir.voice.VoiceIntentProcessor(context, viewModel, scope)
}
