No handwriting model is bundled in this build.

A future legally licensed offline model may be placed here as:

  english.tflite
  nepali.tflite

Legacy Phase 32 names are also accepted by HandwritingModelSpec:

  handwriting_english.tflite
  handwriting_devanagari.tflite

Expected format: quantized TensorFlow Lite. The dual framework rasterizes to 224×224;
the older preprocessor still produces 32×32 for compatibility tests.
English decoding is CTC over printable ASCII. Nepali decoding is CTC over
Devanagari consonants, vowels, matras, halant, and marks. Missing files stay
UNAVAILABLE and never invent text.
The IME never downloads a model at runtime and never uses a network recognizer.
