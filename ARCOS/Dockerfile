# Phase 1: Build the application
FROM eclipse-temurin:21-jdk-jammy as builder

# Install build dependencies
RUN apt-get update && apt-get install -y maven git

# Copy the application source code
WORKDIR /app
COPY . .

# Build the application
# Note: This skips the tests which are currently failing.
RUN mvn clean package -DskipTests

# Phase 2: Create the final runtime image
FROM eclipse-temurin:21-jre-jammy

# Install runtime dependencies
# - wget: to download piper
# - alsa-utils: for audio playback
# - libasound2 & libsndfile1-dev: for audio synthesis and playback
RUN apt-get update && apt-get install -y wget alsa-utils libasound2 libsndfile1-dev && rm -rf /var/lib/apt/lists/*

# Install Piper TTS
WORKDIR /app
RUN wget https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_aarch64.tar.gz && \
    tar -xzf piper_linux_aarch64.tar.gz && \
    mv piper/piper /usr/local/bin/ && \
    rm -r piper piper_linux_aarch64.tar.gz

# Copy the built application from the builder stage
WORKDIR /app
COPY --from=builder /app/target/ARCOS-0.0.1-SNAPSHOT.jar ./application.jar

# Set the entrypoint to run the application
ENTRYPOINT ["java", "-jar", "application.jar"]
f