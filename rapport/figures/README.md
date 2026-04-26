# Captures d'écran à fournir

Ce dossier doit contenir 5 captures PNG. Le rapport LaTeX y fait référence
via `\includegraphics{figures/<nom>.png}`.

## Liste des fichiers attendus

| Fichier | Description | Largeur recommandée |
|---|---|---|
| `dashboard-overview.png` | Vue d'ensemble du dashboard (page entière) | 1600 px |
| `dashboard-flux.png` | Tableau de flux des transactions | 1600 px |
| `dashboard-modal.png` | Modale de détail d'une alerte | 1200 px |
| `dashboard-agents.png` | Panneau d'état des 6 agents JADE | 800 px |
| `logs-jade.png` | Console PowerShell avec les logs JADE | 1600 px |

## Procédure de capture

### 1. Démarrer la stack

```bash
cd ../..    # racine du projet
docker compose up -d
```

Attendre 30 secondes que les services soient healthy.

### 2. Ouvrir le dashboard

Naviguer vers <http://localhost:3000> dans Chrome/Firefox.

### 3. Captures Windows

Utilise l'outil **Capture d'écran** intégré à Windows (`Win + Shift + S`) :

- **dashboard-overview.png** : Capture toute la page du dashboard
- **dashboard-flux.png** : Zoom sur la section "Flux des transactions"
- **dashboard-modal.png** : Clique sur une transaction pour ouvrir la modale, puis capture
- **dashboard-agents.png** : Zoom sur le panneau "Agents"
- **logs-jade.png** : Dans PowerShell, exécuter `docker logs -f pfa-jade-backend`, puis capturer

Sauvegarde les fichiers avec les noms exacts ci-dessus dans ce dossier `figures/`.

### 4. Recompiler le rapport

```bash
cd ..
latexmk -pdf -bibtex rapport.tex
```

## Si tu ne peux pas faire de captures

Le rapport compile quand même : LaTeX affichera juste "fichier introuvable"
pour ces 5 figures, mais le reste s'imprime correctement. Tu peux aussi
remplacer temporairement les `\includegraphics` par des placeholders
texte dans `rapport.tex`.
