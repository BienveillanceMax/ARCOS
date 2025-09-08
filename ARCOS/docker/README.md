# Deploying ArCoS with Docker

This guide provides instructions to deploy the ArCoS application and its dependencies using Docker and Docker Compose. This method simplifies the setup by containerizing the application and its environment.

## Prerequisites

1.  **Raspberry Pi 5**: With a Debian-based OS like Raspberry Pi OS (64-bit) installed.
2.  **Docker**: Docker must be installed on your Raspberry Pi. You can install it and the compose plugin with:
    ```bash
    sudo apt-get update && sudo apt-get install -y docker.io docker-compose-plugin
    ```
3.  **Hardware**: Your "Newpie Newline" Bluetooth speaker/microphone must be paired and connected to the Raspberry Pi using the `bluetoothctl` tool as described in the main project documentation.
4.  **API Keys and Credentials**: You must have the following secrets ready:
    *   Mistral AI API Key
    *   BraveSearch API Key
    *   Google Calendar `client_secrets.json` file
    *   Piper TTS voice model files (`.onnx` and `.onnx.json`)

## Deployment Steps

All commands should be run from the `ARCOS` directory of the project.

### Step 1: Configure Secrets

Before building the container, you need to place your secrets in the correct locations.

1.  **Create `.env` file**:
    Create a file named `.env` in the `ARCOS` directory. This file will hold your API keys. Its content should be:
    ```
    MISTRALAI_API_KEY=YOUR_MISTRAL_API_KEY_HERE
    BRAVE_SEARCH_API_KEY=YOUR_BRAVE_SEARCH_API_KEY_HERE
    ```
    Replace the placeholder values with your actual keys.

2.  **Place Google Credentials**:
    Place your `client_secrets.json` file inside the `src/main/resources/` directory.

3.  **Place TTS Voice Models**:
    *   Create a new directory: `src/main/resources/upmc-model/`
    *   Place your `fr_FR-upmc-medium.onnx` and `fr_FR-upmc-medium.onnx.json` files inside this new directory.

### Step 2: Build and Run the Application

With the secrets in place, you can now build and run the entire application stack with a single command.

```bash
sudo docker compose up --build
```

**What this command does:**
*   `--build`: This flag tells Docker Compose to first build the `arcos-app` image using the `Dockerfile`. It will download the base Java image, install Piper, copy your application code, and compile it with Maven. This only needs to be done the first time or when you change the application code.
*   `up`: This command starts all the services defined in the `docker-compose.yml` file (`arcos-app` and `qdrant`).

The application should now be running. You can view the logs for all services in your terminal. To stop the application, press `Ctrl+C`. To run it in the background in the future, you can use `sudo docker compose up -d`.

This completes the containerized deployment process.
