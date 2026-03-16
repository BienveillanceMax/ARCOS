# Python Tool Redesign — Spec

## Objectif
Transformer le Python tool d'un outil inutilisé en capacité d'émergence pour Calcifer via :
1. Prompt engineering de la description tool (adoption LLM)
2. Sandboxing bubblewrap (sécurité)
3. Polish technique (fichier temp, limite stdout)

## Fichiers impactés
- `Tools/Actions/PythonActions.java` — renommage, nouvelle description
- `Tools/PythonTool/PythonExecutor.java` — bwrap, fichier temp, limite stdout
- `PlannedAction/PlannedActionExecutor.java` — renommer clé registry
- `LLM/Prompts/PromptBuilder.java` — renommer tool dans ReWOO prompt

## Design

### 1. Description tool LLM
- **Nom** : `Executer_du_code`
- **Description** : "Exécute du code Python3 dans un sandbox et retourne stdout. Cas d'usage : calculs/conversions, manipulation de dates, traitement de texte, génération de données structurées, résolution de problèmes logiques. Limites : pas d'accès réseau ni filesystem. Librairie standard uniquement."

### 2. Sandboxing bubblewrap
Commande :
```
bwrap --ro-bind /usr /usr --ro-bind /lib /lib --ro-bind /bin /bin \
      --symlink usr/lib64 /lib64 \
      --tmpfs /tmp --dev /dev --proc /proc \
      --unshare-net --unshare-pid --unshare-user \
      python3 /tmp/script.py
```
- Filesystem : `/usr`, `/lib`, `/bin` read-only, reste invisible
- `/tmp` tmpfs éphémère en écriture
- Réseau désactivé, PID isolé, user isolé
- Script bind-mounted en read-only dans `/tmp/script.py`

### 3. Input via fichier temporaire
- Java écrit le code dans un fichier temp (`Files.createTempFile`)
- Le fichier est bind-mounted read-only dans le sandbox
- Supprimé après exécution (finally block)
- Élimine les problèmes d'échappement shell de `python3 -c`

### 4. Limite stdout
- Tronquer la sortie à 4 Ko côté Java
- Ajouter "[sortie tronquée]" si dépassement

### 5. Fallback sécurisé
- Au démarrage ou premier appel : vérifier `which bwrap`
- Si absent : retourner ActionResult.failure("Sandbox indisponible : bubblewrap non installé")
- Ne jamais exécuter sans sandbox

## Hors scope
- Nouveaux packages Python (stdlib uniquement)
- Nouvelle dépendance Java
- Changements à ActionResult
- Tests d'intégration (bwrap pas dispo en CI)
