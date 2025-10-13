# Use a base image with Python and necessary build tools
FROM python:3.11-slim-bookworm

# Set the working directory
WORKDIR /app

# Install dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

# Clone the repository
RUN git clone https://github.com/joshuaboniface/remote-faster-whisper.git .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy the configuration file
COPY config.yaml .

# Expose the port
EXPOSE 9876

# Run the application
CMD ["python", "remote_faster_whisper.py"]
