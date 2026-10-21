# KyawGyi အတွက် Sherpa-ONNX Streaming Myanmar ASR Model Guide

## အဓိကဆုံးဖြတ်ချက်

လက်ရှိ စစ်ဆေးထားသော official Sherpa-ONNX model catalogue တွင် **Myanmar/Burmese အတွက် ready-made online streaming Zipformer package** ကို အတည်ပြု၍ မတွေ့ရသေးပါ။ Sherpa-ONNX catalogue တွင် Bengali, English, Chinese နှင့် အခြားဘာသာများအတွက် `encoder.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt` ပါသော package များရှိသော်လည်း Myanmar package မရှိပါ။ ထို့ကြောင့် အောက်ပါ public Burmese checkpoints များကို အမည်ပြောင်း၍ Sherpa-ONNX သို့ မထည့်နိုင်ပါ။

| ရင်းမြစ် | မော်ဒယ်အမျိုးအစား | Streaming Zipformer အဖြစ် တိုက်ရိုက်သုံးနိုင်မှု | အကြံပြုချက် |
|---|---|---:|---|
| `myatsu/mms-1b-burmese-v1` | MMS/wav2vec2 CTC, 1B parameters | မရ | Burmese baseline အဖြစ်သုံးပြီး Burmese streaming RNN-T ကို ပြန်လည် train/fine-tune လုပ်ပါ |
| `Hein-HtetSan/myanmar-asr` | Whisper, Dolphin, SeamlessM4T | မရ | Server-side/large-model baseline; Android Sherpa streaming target မဟုတ် |
| OpenSLR-80 Burmese corpus | Speech/transcript data | မော်ဒယ်မဟုတ် | Icefall Zipformer training data အဖြစ် သုံးနိုင် |
| Icefall/Sherpa model catalogue | Stateless RNN-T/Zipformer ONNX | Burmese မဟုတ်သေး | Export format နှင့် deployment reference အဖြစ်သုံးပါ |

## ထုတ်လုပ်မည့် target architecture

Myanmar audio နှင့် Unicode-normalized transcript များကို အသုံးပြု၍ icefall ၏ streaming Zipformer transducer recipe တစ်ခုကို train/fine-tune လုပ်ပါ။ Export ပြီးနောက် ထွက်ရမည့် deployment directory မှာ—

```text
myanmar-zipformer/
├── encoder.onnx
├── decoder.onnx
├── joiner.onnx
├── tokens.txt
└── SHA256SUMS
```

Streaming RNN-T model တွင် `tokens.txt` သည် training အချိန်က vocabulary/blank IDs နှင့် တိတိကျကျ တူရမည်။ Burmese Unicode normalization, punctuation policy နှင့် blank token ID များကို export ပြီးနောက် ပြောင်းလဲမထားပါနှင့်။

## References

[1]: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html "Sherpa-ONNX online Zipformer model catalogue and file layout"
[2]: https://k2-fsa.github.io/icefall/model-export/export-onnx.html "Icefall export to ONNX documentation"
[3]: https://github.com/k2-fsa/icefall/blob/master/egs/librispeech/ASR/pruned_transducer_stateless7_streaming/export.py "Icefall streaming Zipformer export implementation"
[4]: https://huggingface.co/myatsu/mms-1b-burmese-v1 "MMS-1B Burmese ASR model card"
[5]: https://github.com/Hein-HtetSan/myanmar-asr "Myanmar ASR community repository"
[6]: https://github.com/k2-fsa/sherpa-onnx "Sherpa-ONNX deployment repository"
