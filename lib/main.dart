import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import 'native/kyawgyi_bridge.dart';
import 'gemini/voice_assistant_screen.dart';

/// Minimal Flutter shell for the KyawGyi native bridge.
///
/// Demonstrates the full native loop: runtime mic permission -> foreground
/// service -> STT session -> PCM stream -> device controls. Not a finished
/// product UI — a verified starting point.
///
/// Compile status: NOT VERIFIED (no Flutter SDK in this environment).
void main() => runApp(const KyawGyiApp());

class KyawGyiApp extends StatelessWidget {
  const KyawGyiApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'KyawGyi Assistant',
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.amber),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  bool _serviceRunning = false;
  bool _listening = false;
  int _pcmChunks = 0;
  StreamSubscription<Uint8List>? _pcmSub;
  final List<String> _log = [];

  void _say(String s) {
    if (!mounted) return;
    setState(() => _log.insert(
        0, '${DateTime.now().toIso8601String().substring(11, 19)} $s'));
  }

  Future<void> _refresh() async {
    final running = await KyawGyiBridge.isVoiceServiceRunning();
    final listening = await KyawGyiBridge.isListening();
    if (!mounted) return;
    setState(() {
      _serviceRunning = running;
      _listening = listening;
    });
  }

  /// The native service fails closed without RECORD_AUDIO — request it first.
  /// Also requests POST_NOTIFICATIONS (API 33+) so the foreground service's
  /// persistent "listening" notification is actually visible to the user.
  Future<bool> _ensureMic() async {
    final mic = await Permission.microphone.request();
    _say(mic.isGranted ? 'Mic permission granted' : 'Mic permission DENIED');
    if (mic.isGranted) {
      // Non-fatal: the service runs without it, but its notification is hidden.
      final notif = await Permission.notification.request();
      if (!notif.isGranted) {
        _say('Note: notification permission denied — service runs silently');
      }
    }
    return mic.isGranted;
  }

  Future<void> _toggleService() async {
    final ok = _serviceRunning
        ? await KyawGyiBridge.stopVoiceService()
        : await KyawGyiBridge.startVoiceService();
    _say(ok
        ? 'Voice service ${_serviceRunning ? 'stopping' : 'starting'}'
        : 'Voice service call FAILED');
    await _refresh();
  }

  Future<void> _toggleStt() async {
    if (_listening) {
      await _pcmSub?.cancel();
      _pcmSub = null;
      await KyawGyiBridge.stopStt();
      _say('STT stopped');
    } else {
      if (!await _ensureMic()) return;
      final ok = await KyawGyiBridge.startStt();
      if (!ok) {
        _say('startStt FAILED');
        return;
      }
      _pcmChunks = 0;
      _pcmSub = KyawGyiBridge.pcmStream.listen(
        (_) {
          if (!mounted) return;
          setState(() => _pcmChunks++);
        },
        onError: (Object e) => _say('PCM error: $e'),
      );
      _say('STT started — streaming PCM (16kHz mono)');
    }
    await _refresh();
  }

  @override
  void dispose() {
    _pcmSub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('KyawGyi Assistant')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Service: ${_serviceRunning ? 'RUNNING' : 'stopped'}  •  '
            'Mic: ${_listening ? 'LISTENING' : 'idle'}  •  '
            'PCM chunks: $_pcmChunks',
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              ElevatedButton(
                  onPressed: _ensureMic,
                  child: const Text('Mic permission')),
              ElevatedButton(
                  onPressed: _toggleService,
                  child: Text(
                      _serviceRunning ? 'Stop service' : 'Start service')),
              ElevatedButton(
                  onPressed: _toggleStt,
                  child: Text(_listening ? 'Stop STT' : 'Start STT')),
              ElevatedButton(
                  onPressed: () async =>
                      _say('openApp(ဖုန်း): ${await KyawGyiBridge.openApp('ဖုန်း')}'),
                  child: const Text('Open ဖုန်း')),
              ElevatedButton(
                  onPressed: () async =>
                      _say('goHome: ${await KyawGyiBridge.goHome()}'),
                  child: const Text('Go home')),
              ElevatedButton(
                  onPressed: () async =>
                      _say('openRecents: ${await KyawGyiBridge.openRecents()}'),
                  child: const Text('Recents')),
              ElevatedButton(
                  onPressed: () async {
                    final t = await KyawGyiBridge.readScreen();
                    _say('readScreen: ${t.length} texts');
                  },
                  child: const Text('Read screen')),
              ElevatedButton(
                  onPressed: _refresh, child: const Text('Refresh state')),
              ElevatedButton(
                  onPressed: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => const VoiceAssistantScreen(),
                        ),
                      ),
                  child: const Text('🎙️ Gemini မြန်မာအသံ')),
            ],
          ),
          const Divider(height: 32),
          const Text('Log', style: TextStyle(fontWeight: FontWeight.bold)),
          ..._log.map((l) => Text(l,
              style:
                  const TextStyle(fontFamily: 'monospace', fontSize: 12))),
        ],
      ),
    );
  }
}
