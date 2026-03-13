#!/usr/bin/env bash
# Voice calibration script — generates WAV samples for each mood state.
# Usage: ./scripts/calibrate-voice.sh [output_dir]
#
# Requires: Piper installed at ~/.piper-tts/piper/piper
#           GLaDOS model at ~/.piper-tts/models/fr_FR-glados-medium.onnx

set -euo pipefail

PIPER="${HOME}/.piper-tts/piper/piper"
MODEL="${HOME}/.piper-tts/models/fr_FR-glados-medium.onnx"
CONFIG="${HOME}/.piper-tts/models/fr_FR-glados-medium.onnx.json"
OUTPUT_DIR="${1:-./calibration-samples}"
SENTENCE="Franchement, je trouve que cette idée manque cruellement d'originalité, mais bon, je vais quand même t'aider."

mkdir -p "$OUTPUT_DIR"

if [ ! -x "$PIPER" ]; then
    echo "Error: Piper not found at $PIPER"
    exit 1
fi

declare -A STATES
# Format: "lengthScale noiseScale noiseW"
STATES[01_NEUTRAL]="1.05 0.60 0.80"
STATES[02_JOYFUL]="0.88 0.36 0.71"
STATES[03_ANGRY]="0.74 0.75 0.59"
STATES[04_BORED]="1.33 0.66 0.89"
STATES[05_ANXIOUS]="0.84 0.78 1.04"

for state in $(echo "${!STATES[@]}" | tr ' ' '\n' | sort); do
    read -r length noise noisew <<< "${STATES[$state]}"
    outfile="${OUTPUT_DIR}/${state}.wav"
    echo "Generating ${state}: lengthScale=${length} noiseScale=${noise} noiseW=${noisew}"
    echo "$SENTENCE" | "$PIPER" \
        --speaker 1 \
        --model "$MODEL" \
        --config "$CONFIG" \
        --output_file "$outfile" \
        --length_scale "$length" \
        --noise_scale "$noise" \
        --noise_w "$noisew"
    echo "  -> $outfile"
done

echo ""
echo "Done. Listen to files in ${OUTPUT_DIR}/ and provide feedback."
echo "Adjust constants in MoodVoiceMapper.java, then re-run."
