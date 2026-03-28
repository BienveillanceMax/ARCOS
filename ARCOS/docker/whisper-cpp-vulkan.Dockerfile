FROM ghcr.io/ggml-org/whisper.cpp:main-vulkan

ARG WHISPER_MODEL=ggml-large-v3-turbo.bin

RUN mkdir -p /models && wget -q --show-progress -O /models/${WHISPER_MODEL} \
    https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${WHISPER_MODEL}

EXPOSE 8080

ENTRYPOINT ["whisper-server"]
CMD ["--host", "0.0.0.0", "--port", "8080", "--model", "/models/ggml-large-v3-turbo.bin", "--language", "fr", "--no-timestamps"]
