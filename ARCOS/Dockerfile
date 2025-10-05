# Phase 1: Build the application
FROM eclipse-temurin:21-jdk-jammy as builder

RUN apt-get update && apt-get install -y maven git

WORKDIR /app
COPY . .

RUN mvn clean package -DskipTests

# Phase 2: Create the final runtime image
FROM eclipse-temurin:21-jre-jammy

ARG USER_ID=1000
ARG GROUP_ID=1000
ARG USER_NAME=appuser

# Install runtime dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wget \
        alsa-utils \
        pulseaudio-utils \
        libasound2 \
        libpulse0 \
        libsndfile1 && \
    rm -rf /var/lib/apt/lists/*

# Create user matching host
RUN groupadd -g ${GROUP_ID} ${USER_NAME} || true \
 && useradd -m -u ${USER_ID} -g ${GROUP_ID} -s /bin/bash ${USER_NAME} || true

# Add user to audio group for ALSA access
RUN usermod -aG audio ${USER_NAME}

WORKDIR /home/${USER_NAME}/app

COPY --from=builder /app/target/ARCOS-0.0.1-SNAPSHOT.jar ./application.jar

# Create necessary directories and files
RUN mkdir -p /home/${USER_NAME}/.config/pulse && \
    touch /home/${USER_NAME}/.config/pulse/cookie && \
    chown -R ${USER_NAME}:${USER_NAME} /home/${USER_NAME}

# Create PulseAudio client config to prevent autospawn
RUN echo "autospawn = no" > /home/${USER_NAME}/.config/pulse/client.conf && \
    echo "daemon-binary = /bin/true" >> /home/${USER_NAME}/.config/pulse/client.conf && \
    chown ${USER_NAME}:${USER_NAME} /home/${USER_NAME}/.config/pulse/client.conf

USER ${USER_NAME}

ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "application.jar"]
