# Rapport PFA — Compilation LaTeX

## Fichiers

- `rapport.tex` — le rapport principal (~70 pages, 6 chapitres)
- `references.bib` — bibliographie au format BibLaTeX
- `figures/` — captures d'écran à fournir (voir `figures/README.md`)
- `figures/capture-screenshots.ps1` — script PowerShell de capture auto

## Contenu

Le rapport est structuré en 6 chapitres :

1. Contexte général et état de l'art
2. Concepts théoriques (SMA, FIPA-ACL, JADE, ML)
3. **Frameworks, langages et outils utilisés** (nouveau)
4. Analyse et conception du système
5. Implémentation
6. Tests, validation et résultats expérimentaux (avec graphes pgfplots)

Le chapitre 6 contient **9 graphes** générés à la compilation :
distribution des classes, distribution post-SMOTE, comparaison ROC-AUC,
matrice de confusion, répartition des décisions, fréquence des règles,
histogramme de latence, décomposition du temps par étape.

Plus **5 captures d'écran** du dashboard (à fournir dans `figures/`).

## Compilation

### Avec un compilateur local (TeX Live, MiKTeX)

```bash
# Compilation complète (à faire 2 fois pour résoudre les références croisées)
pdflatex rapport.tex
biber rapport
pdflatex rapport.tex
pdflatex rapport.tex
```

Cela génère `rapport.pdf`.

### Avec latexmk (recommandé)

```bash
latexmk -pdf -bibtex rapport.tex
```

### Avec Overleaf (sans installation locale)

1. Aller sur https://www.overleaf.com
2. New Project → Upload Project → uploader le dossier `rapport/`
3. S'assurer que le compilateur est **pdfLaTeX** et `biber` (Menu → Settings)
4. Cliquer sur "Recompile"

### Avec VS Code (extension LaTeX Workshop)

1. Installer l'extension "LaTeX Workshop"
2. Ouvrir `rapport.tex`
3. Build automatique au save

## Personnalisation à faire avant impression

Dans `rapport.tex`, remplacer ces placeholders :

- `[Nom de l'Universite]` (page de garde)
- `[Nom de l'encadrant]` (page de garde + remerciements)
- `Etudiant 1`, `Etudiant 2`, etc. (page de garde) — mettre les vrais noms
- La date de soutenance (page de garde)

## Packages requis

Si la compilation échoue, installer les packages manquants :

```bash
# TeX Live
sudo apt-get install texlive-full   # Linux
# ou
sudo tlmgr install <package>

# MiKTeX (Windows)
# Les packages s'installent automatiquement à la compilation
```

Packages utilisés : `babel`, `lmodern`, `geometry`, `graphicx`, `xcolor`,
`hyperref`, `amsmath`, `booktabs`, `tabularx`, `longtable`, `enumitem`,
`fancyhdr`, `titlesec`, `listings`, `caption`, `subcaption`, `tikz`,
`biblatex` (avec `biber`).
