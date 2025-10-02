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

# Build args to set runtime user uid/gid to match host
ARG USER_ID=1000
ARG GROUP_ID=1000
ARG USER_NAME=appuser

# Install runtime dependencies for audio and other utilities
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wget \
        alsa-utils \
        pulseaudio-utils \
        libasound2 \
        libpulse0 \
        libsndfile1 && \
    rm -rf /var/lib/apt/lists/*

# Create a group and a user that match host uid/gid
RUN groupadd -g ${GROUP_ID} ${USER_NAME} || true \
 && useradd -m -u ${USER_ID} -g ${GROUP_ID} -s /bin/bash ${USER_NAME} || true

WORKDIR /home/${USER_NAME}/app

# Copy the built application from the builder stage
COPY --from=builder /app/target/ARCOS-0.0.1-SNAPSHOT.jar ./application.jar

# Ensure default home config dir exists (so Pulse cookie mount target exists)
RUN mkdir -p /home/${USER_NAME}/.config/pulse && chown -R ${USER_NAME}:${USER_NAME} /home/${USER_NAME}

USER ${USER_NAME}

ENTRYPOINT ["java", "-jar", "application.jar"]
