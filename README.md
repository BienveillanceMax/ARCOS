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

1.  **Clé d'API Mistral AI**: Le projet utilise l'API de Mistral AI. Vous devez fournir votre clé d'API dans le fichier `src/main/resources/application.properties`:

    ```properties
    spring.ai.mistralai.api-key=VOTRE_CLÉ_API_MISTRAL
    ```

2.  **Qdrant**: La base de données vectorielle Qdrant est utilisée pour la mémoire à long terme. Le projet est configuré pour se connecter à une instance Qdrant locale via Docker Compose.

3.  **Clé d'API BraveSearch**: Le projet utilise l'API de Brave pour les recherches internet. Vous devez fournir votre clé d'api en variable d'environement. `BRAVE_SEARCH_API_KEY=yourkey` 
### Lancement

1.  **Lancer Qdrant**:
    ```bash
    docker-compose up -d
    ```
    Cette commande se trouve dans le dossier `src/main/java/Docker`.

2.  **Lancer l'application**:
    ```bash
    mvn spring-boot:run
    ```

## Utilisation

Une fois l'application lancée, elle écoutera les mots-clés (wake words) pour commencer à interagir. Vous pouvez parler à l'application et elle vous répondra en utilisant la synthèse vocale.

## Tests

Pour lancer les tests, utilisez la commande suivante à la racine du projet `ARCOS`:

```bash
mvn test
```
