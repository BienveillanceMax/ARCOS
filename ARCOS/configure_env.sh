#!/bin/bash

# This script configures the environment variables required to run the ArCoS application.
#
# IMPORTANT:
# 1. Edit the placeholder values below with your actual API keys.
# 2. Before running the application, source this script in your terminal:
#    source configure_env.sh
#
# 3. Remember to also replace the placeholder content in 'src/main/resources/client_secrets.json'
#    with your actual Google Calendar API credentials.

# --- EDIT THE VALUES BELOW ---

# 1. Mistral AI API Key
# Get your key from: https://mistral.ai/
export MISTRALAI_API_KEY="YOUR_MISTRAL_API_KEY_HERE"

# 2. Brave Search API Key
# Get your key from: https://brave.com/search/api/
export BRAVE_SEARCH_API_KEY="YOUR_BRAVE_SEARCH_API_KEY_HERE"

# --- DO NOT EDIT BELOW THIS LINE ---

echo "Environment variables set."
echo "MISTRALAI_API_KEY is set to: ${MISTRALAI_API_KEY}"
echo "BRAVE_SEARCH_API_KEY is set to: ${BRAVE_SEARCH_API_KEY}"
echo "--------------------------------------------------------"
echo "Next steps:"
echo "1. Ensure 'src/main/resources/client_secrets.json' is configured."
echo "2. Build the application with: mvn clean package"
echo "3. Run the application with: java -jar target/ARCOS-0.0.1-SNAPSHOT.jar"
