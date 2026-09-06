// Host-side compatibility check: runs a 16 kHz f32le PCM fixture through the
// same dictation transcription path as Android. Not a performance benchmark.
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

#include "transcribe.hpp"
#include "whisper.h"

int main(int argc, char ** argv) {
    if (argc != 4 && argc != 5) {
        std::cerr << "usage: transcribe_fixture_host <model.bin> <pcm-f32le> <threads> [language]\n";
        return 1;
    }
    whisper_context_params contextParams = whisper_context_default_params();
    struct whisper_context * context = whisper_init_from_file_with_params(argv[1], contextParams);
    if (context == nullptr) {
        std::cerr << "failed to load model\n";
        return 1;
    }
    std::ifstream input(argv[2], std::ios::binary);
    if (!input) {
        std::cerr << "failed to open PCM fixture\n";
        return 1;
    }
    std::vector<float> samples;
    char buffer[sizeof(float)];
    while (input.read(buffer, sizeof(buffer))) {
        std::uint32_t bits;
        __builtin_memcpy(&bits, buffer, sizeof(bits));
        float value;
        __builtin_memcpy(&value, &bits, sizeof(value));
        samples.push_back(value);
    }
    const char * language = argc == 5 ? argv[4] : "en";
    const std::string text = transcribeDictation(
        context,
        samples.data(),
        samples.size(),
        std::stoi(argv[3]),
        language);
    std::cout << text << "\n";
    whisper_free(context);
    return 0;
}
