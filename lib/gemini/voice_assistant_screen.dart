/// Gemini-powered Myanmar voice assistant screen.
///
/// Full voice loop, all in Burmese UI:
///   key entry (+ "စစ်ဆေးရန်" verify) → mic → Gemini STT (my) →
///   Gemini chat reply (my) → Gemini TTS → playback.
///
/// Mic capture reuses the existing native PCM stream
/// (16-bit mono @ 16 kHz via KyawGyiBridge.pcmStream) — no new native code.

import 'dart:async';
import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import '../native/kyawgyi_bridge.dart';
import 'api_key_manager.dart';
import 'gemini_service.dart';

class _Msg {
  final bool isUser;
  final String text;
  _Msg(this.isUser, this.text);
}

class VoiceAssistantScreen extends StatefulWidget {
  const VoiceAssistantScreen({super.key});

  @override
  State<VoiceAssistantScreen> createState() => _VoiceAssistantScreenState();
}

class _VoiceAssistantScreenState extends State<VoiceAssistantScreen> {
  final ApiKeyManager _keys = ApiKeyManager();
  final GeminiService _gemini = GeminiService();
  final AudioPlayer _player = AudioPlayer();
  final TextEditingController _keyInput = TextEditingController();

  bool _loading = true;
  bool _hasKey = false;
  String _maskedKey = '';
  bool _verifying = false;
  String _verifyMsg = '';
  bool _verifiedOk = false;

  bool _recording = false;
  bool _busy = false;
  String _status = '';
  final List<_Msg> _msgs = [];
  StreamSubscription<Uint8List>? _pcmSub;
  final List<int> _pcmBytes = [];
  Timer? _recTimer;

  @override
  void initState() {
    super.initState();
    _initKey();
  }

  Future<void> _initKey() async {
    final has = await _keys.hasKey();
    String masked = '';
    if (has) {
      final k = await _keys.getKey();
      if (k != null) masked = ApiKeyManager.maskKey(k);
    }
    if (!mounted) return;
    setState(() {
      _hasKey = has;
      _maskedKey = masked;
      _loading = false;
    });
  }

  @override
  void dispose() {
    _pcmSub?.cancel();
    _recTimer?.cancel();
    _keyInput.dispose();
    _player.dispose();
    _gemini.dispose();
    super.dispose();
  }

  // ------------------------------------------------------------ key entry

  Future<void> _verifyKey() async {
    final input = _keyInput.text.trim();
    if (input.isEmpty) {
      setState(() => _verifyMsg = 'API key အရင်ထည့်သွင်းပါ');
      return;
    }
    setState(() {
      _verifying = true;
      _verifyMsg = 'စစ်ဆေးနေသည်...';
      _verifiedOk = false;
    });
    try {
      final ok = await _gemini.validateKey(input);
      if (!mounted) return;
      setState(() {
        _verifyMsg =
            ok ? '✓ API Key မှန်ကန်ပါသည်' : '✗ API Key မမှန်ကန်ပါ';
        _verifiedOk = ok;
      });
    } on GeminiApiException catch (e) {
      if (!mounted) return;
      setState(() => _verifyMsg = e.message);
    } finally {
      if (mounted) setState(() => _verifying = false);
    }
  }

  Future<void> _saveKey() async {
    if (!_verifiedOk) {
      setState(() => _verifyMsg = 'အရင် "စစ်ဆေးရန်" နှိပ်ပြီး မှန်ကန်ကြောင်း အတည်ပြုပါ');
      return;
    }
    try {
      await _keys.saveKey(_keyInput.text);
      _keyInput.clear();
      await _initKey();
      if (!mounted) return;
      setState(() {
        _verifyMsg = '';
        _verifiedOk = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('API key သိမ်းဆည်းပြီးပါပြီ')),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _verifyMsg = 'သိမ်းဆည်း၍မရပါ: $e');
    }
  }

  Future<void> _clearKey() async {
    await _keys.clearKey();
    await _initKey();
  }

  // ------------------------------------------------------------ voice loop

  Future<void> _toggleRecord() async {
    if (_busy) return;
    if (_recording) {
      await _stopAndProcess();
    } else {
      await _startRecord();
    }
  }

  Future<void> _startRecord() async {
    final mic = await Permission.microphone.request();
    if (!mic.isGranted) {
      setState(() => _status = 'မိုက်ခရိုဖုန်း ခွင့်ပြုချက် မရပါ');
      return;
    }
    final ok = await KyawGyiBridge.startStt();
    if (!ok) {
      setState(() => _status = 'အသံဖမ်းစက် စတင်၍မရပါ');
      return;
    }
    _pcmBytes.clear();
    _pcmSub = KyawGyiBridge.pcmStream.listen(
      (chunk) {
        _pcmBytes.addAll(chunk);
        // 60s cap @16kHz/16-bit mono ≈ 1.9MB — well under the API limit.
        if (_pcmBytes.length > 16000 * 2 * 60) {
          _stopAndProcess();
        }
      },
      onError: (Object e) {
        if (mounted) setState(() => _status = 'အသံဖမ်းယူမှု အမှား: $e');
      },
    );
    // Safety auto-stop at 60s.
    _recTimer?.cancel();
    _recTimer = Timer(const Duration(seconds: 60), () {
      if (_recording) _stopAndProcess();
    });
    setState(() {
      _recording = true;
      _status = '🎙️ နားထောင်နေသည်... ပြောပြီးရင် ထပ်နှိပ်ပါ';
    });
  }

  Future<void> _stopAndProcess() async {
    if (!_recording) return;
    _recTimer?.cancel();
    await _pcmSub?.cancel();
    _pcmSub = null;
    await KyawGyiBridge.stopStt();
    setState(() {
      _recording = false;
      _busy = true;
      _status = '⏳ စဉ်းစားနေသည်...';
    });

    try {
      final apiKey = await _keys.getKey();
      if (apiKey == null || apiKey.isEmpty) {
        throw const GeminiApiException('API key မရှိပါ');
      }
      if (_pcmBytes.isEmpty) {
        throw const GeminiApiException('အသံ တိုလွန်းပါသည်');
      }

      // 1. STT: PCM → WAV → Gemini → Myanmar text.
      final wav = GeminiService.pcmToWav(
        Uint8List.fromList(_pcmBytes),
        sampleRate: 16000,
      );
      final userText = await _gemini.transcribeWav(apiKey, wav);
      if (!mounted) return;
      setState(() {
        _msgs.add(_Msg(true, userText));
        _status = '💭 ဖြေကြားချက် ထုတ်နေသည်...';
      });

      // 2. Chat reply in Myanmar.
      final reply = await _gemini.chatReply(apiKey, userText);
      if (!mounted) return;
      setState(() {
        _msgs.add(_Msg(false, reply));
        _status = '🔊 အသံထွက်နေသည်...';
      });

      // 3. TTS → play.
      final audioWav = await _gemini.speak(apiKey, reply);
      await _player.play(BytesSource(audioWav));
      if (!mounted) return;
      setState(() => _status = '✓ ပြီးပါပြီ — ထပ်ပြောချင်ရင် မိုက်နှိပ်ပါ');
    } on GeminiApiException catch (e) {
      if (!mounted) return;
      setState(() => _status = '⚠️ ${e.message}');
    } catch (e) {
      if (!mounted) return;
      setState(() => _status = '⚠️ အမှား: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  // ------------------------------------------------------------------ UI

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('🎙️ Gemini မြန်မာအသံ')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _hasKey
              ? _buildVoiceUi()
              : _buildKeyUi(),
    );
  }

  Widget _buildKeyUi() {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const Text(
          'Gemini API key ထည့်သွင်းပါ',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        const Text(
          'Google AI Studio (aistudio.google.com) → Get API Key မှ ရယူပါ။\n'
          'Key ကို ဖုန်းထဲမှာသာ လုံခြုံစွာ သိမ်းထားပါတယ်။',
          style: TextStyle(fontSize: 13, color: Colors.grey),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _keyInput,
          obscureText: true,
          decoration: const InputDecoration(
            labelText: 'Gemini API Key',
            border: OutlineInputBorder(),
            hintText: 'AIza...',
          ),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: ElevatedButton(
                onPressed: _verifying ? null : _verifyKey,
                child: _verifying
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('စစ်ဆေးရန်'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: ElevatedButton(
                onPressed: _verifiedOk ? _saveKey : null,
                child: const Text('သိမ်းရန်'),
              ),
            ),
          ],
        ),
        if (_verifyMsg.isNotEmpty) ...[
          const SizedBox(height: 12),
          Text(
            _verifyMsg,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: _verifiedOk ? Colors.green : Colors.red,
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildVoiceUi() {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  'Key: $_maskedKey',
                  style: const TextStyle(fontSize: 12, color: Colors.grey),
                ),
              ),
              TextButton(
                onPressed: _busy || _recording ? null : _clearKey,
                child: const Text('Key ဖျက်မယ်'),
              ),
            ],
          ),
        ),
        Expanded(
          child: _msgs.isEmpty
              ? const Center(
                  child: Text(
                    'မိုက်ခရိုဖုန်း နှိပ်ပြီး မြန်မာလို ပြောပါ 🗣️',
                    style: TextStyle(fontSize: 16, color: Colors.grey),
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: _msgs.length,
                  itemBuilder: (ctx, i) {
                    final m = _msgs[i];
                    return Align(
                      alignment:
                          m.isUser ? Alignment.centerRight : Alignment.centerLeft,
                      child: Container(
                        margin: const EdgeInsets.symmetric(vertical: 4),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: m.isUser
                              ? Colors.amber.shade200
                              : Colors.grey.shade200,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          m.text,
                          style: const TextStyle(fontSize: 15),
                        ),
                      ),
                    );
                  },
                ),
        ),
        if (_status.isNotEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Text(_status, style: const TextStyle(fontSize: 13)),
          ),
        Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              FloatingActionButton.large(
                onPressed: _busy ? null : _toggleRecord,
                backgroundColor: _recording ? Colors.red : Colors.amber,
                child: _busy
                    ? const CircularProgressIndicator(color: Colors.white)
                    : Icon(_recording ? Icons.stop : Icons.mic,
                        size: 36, color: Colors.white),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
