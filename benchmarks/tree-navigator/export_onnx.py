#!/usr/bin/env python3
"""Export cross-encoder to ONNX format for Java inference."""
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer
from pathlib import Path

MODEL_DIR = Path(__file__).parent / "finetuned-navigator-deep"
OUTPUT_PATH = MODEL_DIR / "model.onnx"

model = AutoModelForSequenceClassification.from_pretrained(str(MODEL_DIR))
tokenizer = AutoTokenizer.from_pretrained(str(MODEL_DIR))
model.eval()

dummy = tokenizer("query", "description", return_tensors="pt", max_length=128, truncation=True, padding="max_length")
torch.onnx.export(
    model,
    (dummy["input_ids"], dummy["attention_mask"]),
    str(OUTPUT_PATH),
    input_names=["input_ids", "attention_mask"],
    output_names=["logits"],
    dynamic_axes={
        "input_ids": {0: "batch", 1: "seq"},
        "attention_mask": {0: "batch", 1: "seq"},
        "logits": {0: "batch"},
    },
    opset_version=14,
)
print(f"Exported to {OUTPUT_PATH} ({OUTPUT_PATH.stat().st_size / 1e6:.1f} MB)")
