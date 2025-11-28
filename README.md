# ArCoS - Artificial Cognitive System

ARCOS est un système cognitif autonome conçu pour simuler une personnalité numérique capable d'interagir, de se souvenir, de former des opinions et de prendre des initiatives. Ce projet s'appuie sur des technologies modernes en matière d'IA, notamment les grands modèles de langage (LLM) et les bases de données vectorielles, pour créer une expérience interactive et évolutive.

`/! Le projet est encore en développement actif, le lancer pour vous dans son état actuel risque d'être un enfer  !\`

## Table of Contents

- [ArCoS - Artificial Cognitive System](#arcos---artificial-cognitive-system)
  - [Table of Contents](#table-of-contents)
  - [Architecture](#architecture)
    - [Core Components](#core-components)
    - [Technology Stack](#technology-stack)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Installation and Configuration](#installation-and-configuration)
    - [Running the Application](#running-the-application)
  - [Usage](#usage)
  - [Development](#development)
    - [Running Tests](#running-tests)
  - [Error Handling](#error-handling)

## Architecture

Le projet est construit sur une architecture modulaire où chaque composant a une responsabilité claire et communique avec les autres de manière asynchrone via un bus d'événements.

### Core Components

- **Orchestrator**: Le cœur du système. Il écoute les événements provenant de l'EventBus et coordonne les actions des autres services. C'est le chef d'orchestre qui s'assure que chaque stimulus (un mot-clé détecté, un message utilisateur) est traité correctement.
- **EventBus**: Une file d'attente en mémoire qui desacouple les producteurs d'événements (ex: détection de mot-clé) des consommateurs (l'Orchestrator).
- **Producers**: Cette catégorie de composants est responsable de la génération d'événements. Le plus notable est le `WakeWordProducer`, qui utilise la bibliothèque Porcupine pour écouter en permanence un mot-clé et déclencher le début d'une interaction.
- **IO (Input/Output)**: Gère les interactions avec l'utilisateur.
  - **Speech-to-Text**: Capture l'audio, le transmet au service `faster-whisper` pour la transcription.
  - **Text-to-Speech**: Synthétise le texte généré par l'IA en audio pour une réponse vocale.
- **LLM (Large Language Model)**: Interface avec l'API de Mistral AI. Il est utilisé pour la compréhension du langage, la génération de réponses et l'exécution de tâches complexes via des appels de fonctions (Tools).
- **Memory**: Simule la mémoire humaine avec deux composantes :
  - **Short-Term Memory**: Contexte de la conversation actuelle.
  - **Long-Term Memory**: Une base de données vectorielle Qdrant qui stocke les souvenirs, opinions et désirs de l'IA, permettant une persistance et une recherche sémantique sur le long terme.
- **Personality**: Le "cerveau" de l'IA. C'est ici que les souvenirs sont analysés pour former des opinions et générer des "désirs", qui sont des actions proactives que l'IA peut décider de prendre.
- **Tools**: Des capacités externes que l'IA peut utiliser pour interagir avec le monde extérieur. Exemples :
  - `BraveSearch`: Pour effectuer des recherches sur Internet.
  - `Google Calendar`: Pour gérer l'agenda de l'utilisateur.

### Technology Stack

- **Langage**: Java 21
- **Framework**: Spring Boot 3.5.3
- **IA**: Spring AI 1.0.0
- **LLM**: Mistral AI
- **Base de données vectorielle**: Qdrant
- **Détection de mot-clé**: PicoVoice Porcupine
- **Speech-to-Text**: faster-whisper
- **Containerisation**: Docker, Docker Compose
- **Build**: Maven
- **APIs externes**: Google Calendar, Brave Search

## Getting Started

### Prerequisites

- Java 21
- Maven 3.8+
- Docker et Docker Compose
- Les `user id` et `group id` de votre utilisateur. Vous pouvez les obtenir avec les commandes `id -u` et `id -g`.
- Les `group id` des groupes `audio` et `bluetooth` de votre système. Vous pouvez les obtenir avec `getent group audio bluetooth`.

### Installation and Configuration

L'application est conçue pour être lancée avec Docker Compose, qui orchestre les différents services nécessaires.

#### Docker Compose Services

Le fichier `docker-compose.yml` définit les services suivants :
- **qdrant**: La base de données vectorielle pour la mémoire à long terme.
- **faster-whisper**: Le service de transcription audio en texte.
- **arcos-app**: L'application principale ARCOS.

#### Configuration Steps

1.  **Créer un fichier `.env`**: À la racine du projet `ARCOS`, créez un fichier `.env` pour stocker vos secrets et configurations.

2.  **Configurer les variables d'environnement**: Ajoutez les variables suivantes à votre fichier `.env` :
    ```bash
    # Vos identifiants système
    UID=<your_user_id>
    GID=<your_group_id>
    BLUETOOTH_GID=<your_bluetooth_group_id>

    # Clés d'API
    MISTRALAI_API_KEY=<your_mistral_api_key>
    BRAVE_SEARCH_API_KEY=<your_brave_search_api_key>
    PORCUPINE_ACCESS_KEY=<your_porcupine_access_key>
    ```

3.  **Ajouter les fichiers requis**: Placez les fichiers suivants dans le dossier `ARCOS/src/main/resources/` :
    - `client_secrets.json`: Vos identifiants Google API pour l'intégration avec Google Calendar.
    - **Modèle de Wake Word**: Un fichier `.ppn` de Porcupine. Deux modèles sont fournis par défaut :
        - `Calcifer.ppn` (pour architecture ARM)
        - `Mon-ami_fr_linux_v3_0_0.ppn` (pour architecture x86)
      Le nom du fichier doit correspondre à celui attendu par l'application.

### Running the Application

Une fois la configuration terminée, vous pouvez lancer l'ensemble des services avec la commande suivante à la racine du projet `ARCOS`:

```bash
docker compose up --build
```

L'option `--build` est recommandée au premier lancement pour construire les images Docker.

## Usage

Une fois l'application lancée, elle écoutera les mots-clés (wake words) pour commencer à interagir. Vous pouvez parler à l'application et elle vous répondra en utilisant la synthèse vocale.

## Development

Cette section s'adresse aux développeurs souhaitant contribuer au projet.

### Running Locally

Il est possible de lancer l'application localement sans Docker, à condition que les services `qdrant` et `faster-whisper` soient accessibles.

1.  **Lancez les services externes**: Assurez-vous que Qdrant et faster-whisper tournent et sont accessibles depuis votre machine locale. Vous pouvez les lancer via Docker:
    ```bash
    docker compose up qdrant faster-whisper
    ```
2.  **Configurez l'application**: Assurez-vous que votre fichier `application.properties` est correctement configuré pour se connecter à ces services.
3.  **Lancez l'application**: Exécutez la classe principale `ARCOSApplication` depuis votre IDE.

### Running Tests

Pour lancer la suite de tests unitaires, utilisez la commande suivante à la racine du projet `ARCOS`:

```bash
mvn test
```

Pour lancer un test spécifique :

```bash
mvn test -Dtest=NomDeLaClasseDeTest
```

### Coding Style and Conventions

- **Nommage**: Suivre les conventions de nommage standard de Java.
- **Gestion des dépendances**: Utiliser le `dependencyManagement` de Maven pour maintenir les versions des dépendances cohérentes.
- **Modularité**: Le code est organisé en modules basés sur les fonctionnalités (e.g., `Memory`, `Personality`, `IO`). Visez à maintenir cette séparation des préoccupations.

## Error Handling

Chaque erreur de parsing ou d'appel doit permettre une logique de retry ou d'échec gracieux si un nombre maximum de retry est dépassé.
