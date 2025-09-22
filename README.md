# ArCoS - Artificial Cognitive System

ARCOS est un système cognitif autonome conçu pour simuler une personnalité numérique capable d'interagir, de se souvenir, de former des opinions et de prendre des initiatives. Ce projet s'appuie sur des technologies modernes en matière d'IA, notamment les grands modèles de langage (LLM) et les bases de données vectorielles, pour créer une expérience interactive et évolutive.
`/! Le projet est encore en développement actif, le lancer pour vous dans son état actuel risque d'être un enfer  !\`

## Architecture

Le projet est construit sur une architecture microservices-like, où chaque composant a une responsabilité claire et communique avec les autres via un bus d'événements.

### Composants principaux

- **Orchestrator**: Le cœur du système, qui coordonne les interactions entre les différents services.
- **LLM**: L'interface avec le LLM.
- **Memory**: Gère la mémoire à court et à long terme du système. La mémoire à long terme est implémentée à l'aide d'une base de données vectorielle Qdrant.
- **Personality**: Simule la personnalité du système, y compris ses valeurs, ses opinions et ses désirs.
- **EventBus**: Un bus d'événements simple qui permet aux différents composants de communiquer de manière asynchrone.
- **Producer**: Regroupe l'ensemble des producteurs d'évenements qui sont push dans l'EventBus
- **IO**: Gère les entrées/sorties, y compris la reconnaissance vocale (Speech-to-Text) et la synthèse vocale (Text-to-Speech).
- **Tools**: Regroupe les outils externes utilisés par l'IA.

### Gestion des erreurs

Chaque erreur de parsing ou d'appel doit permettre une logique de retry ou d'échec gracieux si un nombre maximum de retry est dépassé.

## Installation et configuration

### Prérequis

- Java 21
- Maven 3.8+
- Docker et Docker Compose

### Configuration

Dans un fichier .env dans le même dossier que me docker-compose.yml vous devez définir ces valeurs : 

1.  **Clé d'API Mistral AI**: Le projet utilise l'API de Mistral AI. Vous devez fournir votre clé d'API dans le fichier sous la forme :
`MISTRALAI_API_KEY=yourkey`

2.  **Clé d'API BraveSearch**: Le projet utilise l'API de Brave pour les recherches internet. Vous devez fournir votre clé d'api dans le fichier sous la forme :
 `BRAVE_SEARCH_API_KEY=yourkey`

3.  **Clé d'accès PicoVoice Porcupine**: Le projet utilise Porcupine pour détecter les "wake words" Vous devez fournir votre clé dans le fichier sous la forme :
 `PORCUPINE_ACCESS_KEY=yourkey`.
### Lancement

2.  **Lancer l'application**:
    ```bash
    docker compose up
    ```

## Utilisation

Une fois l'application lancée, elle écoutera les mots-clés (wake words) pour commencer à interagir. Vous pouvez parler à l'application et elle vous répondra en utilisant la synthèse vocale.

## Tests

Pour lancer les tests, utilisez la commande suivante à la racine du projet `ARCOS`:

```bash
mvn test
```
