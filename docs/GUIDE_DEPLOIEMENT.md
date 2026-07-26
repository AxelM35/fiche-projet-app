# Guide de déploiement — Fiche Projet numérique

### À l'attention du responsable informatique du Collège Saint-Helier

Ce document explique comment déployer, configurer et faire vivre au quotidien
l'application **Fiche Projet numérique** (gestion des projets de voyages
scolaires). Il est écrit pour une personne en charge de l'infrastructure, pas
nécessairement développeuse : chaque étape est décrite de façon concrète et
autonome.

Deux autres documents complètent celui-ci si besoin :
- [`README.md`](../README.md) : présentation technique rapide, pour un profil développeur.
- [`docs/CAHIER_DES_CHARGES.md`](CAHIER_DES_CHARGES.md) : détail de chaque fonctionnalité, décisions prises, état d'avancement.
- [`docs/SAUVEGARDE.md`](SAUVEGARDE.md) : procédure complète de sauvegarde/restauration (référencée en détail au §7 ci-dessous).

---

## Sommaire

1. [Vue d'ensemble](#1-vue-densemble)
2. [Prérequis](#2-prérequis)
3. [Étape 1 — Préparer Google Cloud (authentification)](#3-étape-1--préparer-google-cloud-authentification)
4. [Étape 2 — Préparer le serveur](#4-étape-2--préparer-le-serveur)
5. [Étape 3 — HTTPS (obligatoire)](#5-étape-3--https-obligatoire)
6. [Étape 4 — Fichier de configuration (`.env`)](#6-étape-4--fichier-de-configuration-env)
7. [Étape 5 — Premier démarrage](#7-étape-5--premier-démarrage)
8. [Sauvegardes](#8-sauvegardes)
9. [Mettre à jour l'application](#9-mettre-à-jour-lapplication)
10. [Supervision au quotidien](#10-supervision-au-quotidien)
11. [Checklist avant l'ouverture aux utilisateurs](#11-checklist-avant-louverture-aux-utilisateurs)
12. [Dépannage courant](#12-dépannage-courant)
13. [Qui contacter / où trouver quoi](#13-qui-contacter--où-trouver-quoi)

---

## 1. Vue d'ensemble

L'application tourne dans **3 conteneurs Docker**, démarrés ensemble par
Docker Compose :

| Conteneur | Rôle |
|---|---|
| `app` | L'application elle-même (Java/Spring Boot), écoute sur le port 8080 |
| `db` | Base de données PostgreSQL, où sont stockées toutes les données |
| `db-backup` | Sauvegarde automatique quotidienne de `db` |

Les utilisateurs se connectent avec leur compte **Google du collège**
(Google Workspace) — il n'y a pas de mot de passe propre à l'application.
Les rôles (Comptabilité, Vie Scolaire, Direction, Admin) sont attribués par
adresse email, soit dans le fichier de configuration, soit ensuite
directement depuis l'application par un administrateur.

**Ce que vous n'avez pas à gérer vous-même** : le code, les mises à jour de
fonctionnalités, la structure de la base de données (elle se met à jour
seule au démarrage via des migrations). **Ce qui reste de votre
responsabilité** : le serveur qui héberge les conteneurs, le nom de domaine
et le certificat HTTPS, les identifiants Google/SMTP, et la copie des
sauvegardes hors du serveur.

---

## 2. Prérequis

- Un serveur (physique, VM, ou VPS chez un hébergeur) avec :
  - **Docker** et **Docker Compose** installés (voir [docs.docker.com/engine/install](https://docs.docker.com/engine/install/)).
  - Au moins 2 Go de RAM et 2 vCPU sont largement suffisants pour le volume
    attendu (une cinquantaine de dossiers par an) ; quelques Go d'espace
    disque libre pour la base de données et les sauvegardes.
  - Un port ouvert vers l'extérieur (443 pour HTTPS, voir §5).
- Un **nom de domaine ou sous-domaine** pointant vers ce serveur (ex.
  `fiche-projet.college-sthelier.fr`), avec un enregistrement DNS déjà en
  place.
- Un accès **administrateur Google Workspace** du collège, pour créer les
  identifiants OAuth2 (§3) et, si vous activez l'intégration Drive, un
  compte de service.
- Une adresse email et des identifiants SMTP pour l'envoi des
  notifications (voir §6 — un compte Gmail avec un "mot de passe
  d'application" convient, ou tout autre serveur SMTP dont vous disposez).
- `git` installé sur le serveur, pour récupérer le code de l'application.

---

## 3. Étape 1 — Préparer Google Cloud (authentification)

L'application authentifie les utilisateurs via **Google OAuth2/OIDC**. Il
faut créer des identifiants dans un projet Google Cloud.

1. Rendez-vous sur [console.cloud.google.com](https://console.cloud.google.com/),
   créez (ou choisissez) un projet associé au domaine Google Workspace du
   collège.
2. **Écran de consentement OAuth** (menu *APIs & Services > Écran de
   consentement OAuth*) :
   - Type : *Interne* si possible (restreint aux comptes du domaine
     Google Workspace), sinon *Externe* avec le paramètre "domaine
     autorisé" du côté application (voir `ALLOWED_EMAIL_DOMAIN` au §6) qui
     filtrera de toute façon les connexions.
   - Scopes demandés par l'application : `openid`, `profile`, `email`
     (non sensibles, ne nécessitent normalement pas de revue Google).
   - Une fois les tests validés, repassez l'écran de consentement en statut
     **Production** (sinon seuls les comptes ajoutés comme "testeurs"
     pourront se connecter).
3. **Identifiants** (menu *APIs & Services > Identifiants*) : créez un
   identifiant *ID client OAuth 2.0*, type *Application Web*.
   - **URI de redirection autorisée** à renseigner impérativement :
     ```
     https://<votre-domaine>/login/oauth2/code/google
     ```
     (remplacez `<votre-domaine>` par le domaine réel choisi, ex.
     `fiche-projet.college-sthelier.fr`). Sans cette URI exacte, la
     connexion échouera avec une erreur `redirect_uri_mismatch`.
   - Notez le **Client ID** et le **Client Secret** générés : ils vont dans
     `.env` (§6, `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`).

### Intégration Google Drive (facultative)

Si vous souhaitez que l'application crée automatiquement un dossier Drive
par voyage (pièces jointes), il faut en plus :

1. Activer l'**API Google Drive** sur le même projet Google Cloud.
2. Créer un **compte de service** (menu *IAM & Admin > Comptes de
   service*), puis générer une clé JSON pour ce compte.
3. Créer un **Drive partagé** dédié dans Google Workspace (ex. "Voyages
   scolaires"), et y ajouter l'adresse email du compte de service comme
   membre, avec le rôle **Gestionnaire de contenu**.
4. Encoder la clé JSON en base64 (`base64 -w0 cle.json` sous Linux/macOS)
   et reporter le résultat dans `GOOGLE_DRIVE_CREDENTIALS_JSON_BASE64`
   (§6).

Cette intégration est **désactivée par défaut** : sans configuration, rien
ne casse, le lien Drive se saisit simplement à la main sur chaque fiche.

---

## 4. Étape 2 — Préparer le serveur

Sur le serveur, en tant qu'utilisateur ayant les droits Docker :

```bash
git clone https://github.com/AxelM35/fiche-projet-app.git
cd fiche-projet-app
```

> Une mise à jour ultérieure se fera avec `git pull` dans ce même dossier
> (voir §9), inutile de re-cloner à chaque fois.

---

## 5. Étape 3 — HTTPS (obligatoire)

**L'authentification Google OAuth2 exige HTTPS** (Google refuse de
rediriger vers une URL `http://` en production), et la confidentialité des
données (organisateurs, budgets, échanges) l'impose de toute façon. Le
conteneur `app` ne fait que du HTTP en clair sur le port 8080 : **c'est à
vous de placer un reverse proxy devant**, qui gère le certificat TLS et
transmet le trafic vers `app:8080`.

L'option la plus simple à opérer est **Caddy**, qui obtient et renouvelle
automatiquement un certificat Let's Encrypt. Exemple minimal
(`Caddyfile`, en dehors du dépôt de l'application, sur le serveur) :

```
fiche-projet.college-sthelier.fr {
    reverse_proxy localhost:8080
}
```

Puis lancer Caddy en conteneur à côté (ou l'installer nativement sur le
serveur — voir [caddyserver.com/docs](https://caddyserver.com/docs/)).

Avec **nginx**, l'équivalent est un `server` bloc classique avec
`proxy_pass http://127.0.0.1:8080;` et un certificat obtenu via
`certbot`. Le choix de l'outil (Caddy, nginx, Traefik...) importe peu :
seul compte le résultat — le port 443 en HTTPS devant le port 8080 de
`app`.

Une fois le reverse proxy en place, mettez à jour dans `.env` :
```
APP_BASE_URL=https://fiche-projet.college-sthelier.fr
```
(cette URL sert à construire les liens cliquables dans les emails de
notification).

> **Limite connue à signaler au développeur avant l'ouverture** : la
> protection anti-brute-force sur la page de connexion (20 tentatives par
> minute) identifie actuellement chaque visiteur par
> `request.getRemoteAddr()`. Derrière un reverse proxy, cette adresse
> devient celle du proxy pour **tout le monde** : la limite s'applique
> alors globalement à l'ensemble des utilisateurs plutôt qu'individuellement
> par utilisateur. Ce n'est pas bloquant pour ouvrir l'application, mais
> à corriger côté code (lecture de l'en-tête `X-Forwarded-For` avec une
> liste de proxies de confiance) avant une montée en charge sérieuse.

---

## 6. Étape 4 — Fichier de configuration (`.env`)

```bash
cp .env.example .env
```

Puis éditez `.env` avec un éditeur de texte. Toutes les variables sont déjà
listées et commentées dans le fichier ; voici leur signification :

| Variable | Rôle |
|---|---|
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Identifiants de la base PostgreSQL. Choisissez un mot de passe robuste, différent de la valeur d'exemple. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Obtenus à l'étape 1 (§3). |
| `ALLOWED_EMAIL_DOMAIN` | Domaine email autorisé à se connecter (ex. `college-sthelier.fr`). Toute autre adresse est rejetée à la connexion. |
| `ROLES_ADMIN`, `ROLES_COMPTA`, `ROLES_VIESCO`, `ROLES_DIRECTION` | Adresses email (séparées par des virgules) recevant chaque rôle métier. Peuvent être complétées plus tard depuis l'application (`/admin/roles`) sans toucher à `.env` — mais gardez au moins une adresse ici pour `ROLES_ADMIN` : c'est le filet de sécurité qui évite de se retrouver sans aucun administrateur. |
| `ROLES_LECTURE_SEULE` | Facultatif : adresses en simple consultation (ex. secrétariat), sans droit de créer ni valider. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Serveur SMTP pour l'envoi des notifications par email. Avec Gmail : `smtp.gmail.com`, port `587`, et un ["mot de passe d'application"](https://support.google.com/accounts/answer/185833) généré pour le compte `MAIL_USERNAME` (un mot de passe Google classique ne fonctionnera pas). |
| `APP_BASE_URL` | URL publique HTTPS de l'application (voir §5) — utilisée uniquement pour construire les liens dans les emails. |
| `GOOGLE_DRIVE_*` | Intégration Drive facultative (voir §3). Laissez `GOOGLE_DRIVE_ENABLED=false` si vous ne l'utilisez pas. |
| `RELANCES_SEUIL_JOURS`, `RELANCES_PERIODE_JOURS` | Un email de rappel est envoyé automatiquement (tous les jours à 8h) au valideur en attente quand un dossier est bloqué depuis plus de `RELANCES_SEUIL_JOURS` jours (7 par défaut), puis répété tous les `RELANCES_PERIODE_JOURS`. |
| `BACKUP_SCHEDULE`, `BACKUP_KEEP_DAYS/WEEKS/MONTHS` | Fréquence et rétention des sauvegardes automatiques (voir §8). |

**Ne versionnez jamais `.env`** (il est déjà exclu par `.gitignore`) : il
contient des secrets réels, contrairement à `.env.example` qui ne sert que
de modèle.

---

## 7. Étape 5 — Premier démarrage

```bash
docker compose up --build -d
```

Le premier démarrage télécharge les images, construit l'application et
initialise la base de données (les tables sont créées automatiquement par
les migrations internes de l'application — aucune action manuelle sur la
base n'est nécessaire).

Vérifiez que tout est en ligne :

```bash
docker compose ps
```

Les 3 services doivent apparaître `healthy` (ou `running` pour
`db-backup`, qui n'a pas de sonde de santé). Pour vérifier directement
l'application :

```bash
curl http://localhost:8080/actuator/health
# doit repondre : {"status":"UP"}
```

Une fois le reverse proxy HTTPS en place (§5), ouvrez
`https://<votre-domaine>` dans un navigateur : vous devez arriver sur la
page de connexion Google. Connectez-vous avec une adresse listée dans
`ROLES_ADMIN` : vous devez arriver sur le tableau de bord avec le menu
"Administration" visible dans la barre de navigation.

**Rappel important** : après tout changement de rôle (dans `.env` ou
depuis `/admin/roles`), l'utilisateur concerné doit **se déconnecter puis
se reconnecter** pour que le nouveau rôle soit pris en compte (les droits
sont chargés une fois, à la connexion).

---

## 8. Sauvegardes

Un service dédié (`db-backup`) sauvegarde automatiquement la base tous les
jours dans le dossier `./backups/` du serveur, avec une rétention
glissante (jours/semaines/mois, configurable dans `.env`).

**Ce dossier `./backups/` n'est PAS une sauvegarde hors site.** Une panne
disque ou la perte du serveur emporterait aussi les sauvegardes locales
avec lui. Mettez en place une copie régulière de ce dossier vers un autre
emplacement (autre machine, stockage cloud, NAS...) — un simple `rsync`
ou `cron` suffit.

La procédure complète (déclencher une sauvegarde immédiate, **tester une
restauration** — indispensable avant de faire confiance à une sauvegarde
—, et restaurer en cas d'incident réel) est détaillée pas à pas dans
[`docs/SAUVEGARDE.md`](SAUVEGARDE.md). Faites ce test de restauration une
première fois avant l'ouverture aux utilisateurs, puis périodiquement.

---

## 9. Mettre à jour l'application

Quand une nouvelle version du code est disponible (ex. après une évolution
demandée) :

```bash
cd fiche-projet-app
git pull
docker compose up --build -d
```

Docker Compose ne reconstruit et ne redémarre que ce qui a changé (le
conteneur `db` contenant les données n'est pas affecté). D'éventuelles
évolutions de la structure de la base sont appliquées automatiquement au
démarrage de `app`, sans action de votre part.

**Recommandation** : faites une sauvegarde manuelle juste avant une mise à
jour importante (voir `docs/SAUVEGARDE.md`), par précaution.

---

## 10. Supervision au quotidien

- **Page "Santé"** (`/admin/sante`, réservée aux administrateurs) :
  nombre de dossiers en base, version actuellement déployée, date de la
  dernière sauvegarde détectée. Un premier réflexe en cas de doute.
- **Endpoint technique** `/actuator/health` (public, sans authentification,
  volontairement minimal — il ne renvoie que `UP`/`DOWN`) : utile pour un
  monitoring externe (ex. UptimeRobot, un cron `curl`) ou pour que le
  reverse proxy attende que l'application soit prête.
- **Logs** :
  ```bash
  docker compose logs -f app
  docker compose logs -f db
  docker compose logs -f db-backup
  ```
  Les logs des 3 conteneurs sont automatiquement bornés (10 Mo × 5
  fichiers maximum chacun) pour ne pas remplir le disque.
- **Incident d'envoi d'emails** : si le serveur SMTP tombe en panne, cela
  n'interrompt jamais le fonctionnement de l'application (les échecs
  d'envoi sont journalisés mais n'empêchent aucune action métier). Un
  administrateur peut aussi couper temporairement les notifications
  (`/admin/notifications`) et tester la configuration SMTP avec un email
  de test, sans redémarrer le conteneur.

---

## 11. Checklist avant l'ouverture aux utilisateurs

Points à vérifier une dernière fois avant de communiquer l'accès à
l'ensemble des professeurs/personnel :

- [ ] HTTPS actif (reverse proxy en place, certificat valide) — §5.
- [ ] Écran de consentement Google OAuth2 passé en **Production** (pas
      seulement "Test") — §3.
- [ ] `ALLOWED_EMAIL_DOMAIN` positionné sur le vrai domaine du collège.
- [ ] Adresses réelles renseignées dans `ROLES_ADMIN` / `ROLES_COMPTA` /
      `ROLES_VIESCO` / `ROLES_DIRECTION` (pas des adresses de test).
- [ ] Envoi SMTP réel testé (email de test depuis `/admin/notifications`,
      puis vérifier sa bonne réception, y compris son rendu visuel dans
      Gmail/Outlook).
- [ ] Une sauvegarde a été déclenchée et **restaurée avec succès** sur une
      base de test (`docs/SAUVEGARDE.md`).
- [ ] Le dossier `./backups/` est répliqué vers un emplacement hors du
      serveur.
- [ ] Au moins une connexion de test réussie de bout en bout (connexion
      Google → tableau de bord → création d'un dossier → soumission).

---

## 12. Dépannage courant

| Symptôme | Piste probable |
|---|---|
| `redirect_uri_mismatch` à la connexion Google | L'URI de redirection dans Google Cloud Console ne correspond pas exactement à `https://<domaine>/login/oauth2/code/google` (§3). |
| Erreur "accès refusé" / domaine non autorisé | L'adresse utilisée n'appartient pas au domaine défini dans `ALLOWED_EMAIL_DOMAIN`. |
| Un utilisateur ne voit pas les actions liées à son nouveau rôle | Il doit se déconnecter puis se reconnecter (les rôles sont chargés à la connexion, voir §7). |
| Les emails de notification ne partent pas | Vérifier `MAIL_USERNAME`/`MAIL_PASSWORD` (mot de passe d'application, pas le mot de passe du compte), tester depuis `/admin/notifications`, regarder `docker compose logs app` pour l'erreur SMTP précise. |
| `docker compose up` échoue avec une erreur liée à `DB_PASSWORD` | La variable n'est pas définie dans `.env` (elle est volontairement obligatoire, sans valeur par défaut, pour ne jamais démarrer avec un mot de passe trivial). |
| La page "Santé" affiche "Aucune sauvegarde détectée" | Vérifier que `db-backup` tourne (`docker compose logs db-backup`) et qu'au moins une sauvegarde planifiée a déjà eu lieu (la première ne se déclenche qu'à l'heure planifiée, pas immédiatement au démarrage). |

Si le problème persiste, `docker compose logs app` est le premier réflexe :
les erreurs y sont journalisées en clair.

---

## 13. Qui contacter / où trouver quoi

- **Fonctionnement métier d'une fonctionnalité** (qui peut faire quoi, pourquoi tel choix) → [`docs/CAHIER_DES_CHARGES.md`](CAHIER_DES_CHARGES.md).
- **Sauvegarde/restauration en détail** → [`docs/SAUVEGARDE.md`](SAUVEGARDE.md).
- **Aspects techniques/code** (pour un développeur reprenant le projet) → [`README.md`](../README.md).
- **Anomalie ou évolution du code** → le prestataire/développeur en charge du projet.
