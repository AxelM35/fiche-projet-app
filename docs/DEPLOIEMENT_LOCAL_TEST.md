# Déployer une instance de test en local (Windows)

Ce guide décrit comment faire tourner l'application **sur un poste Windows**,
isolée de l'instance réelle, pour tester ou faire une démonstration. Il part du
cas le plus courant : vous avez **copié le fichier `.env` d'une autre machine**.

> Pour un déploiement réel (serveur, HTTPS, ouverture aux utilisateurs), ce
> n'est pas ce document qu'il faut suivre mais
> [`GUIDE_DEPLOIEMENT.md`](GUIDE_DEPLOIEMENT.md).

**Point important avant de commencer** : l'authentification passe
exclusivement par Google (il n'existe aucun mot de passe propre à
l'application). Même en local, le poste doit donc avoir accès à Internet, et
l'adresse `http://localhost:8080` doit être autorisée dans Google Cloud
Console — c'est l'étape 4, la seule qui ne se règle pas dans `.env`.

---

## 1. Installer les prérequis

| Outil | Pourquoi | Où |
|---|---|---|
| **Docker Desktop** (avec WSL 2) | Fait tourner l'application et sa base | [docs.docker.com/desktop/install/windows-install](https://docs.docker.com/desktop/install/windows-install/) |
| **Git pour Windows** | Récupérer le code | [git-scm.com/download/win](https://git-scm.com/download/win) |

Rien d'autre à installer : ni Java, ni Maven, ni PostgreSQL. Tout est compilé
et exécuté dans les conteneurs.

Prévoir environ **4 Go d'espace disque** (images Docker + dépendances Maven
téléchargées à la première compilation) et laisser Docker Desktop démarré :
l'icône baleine dans la barre des tâches doit indiquer *Engine running*.

## 2. Récupérer le code

Dans PowerShell :

```powershell
cd $HOME\Documents
git clone https://github.com/AxelM35/fiche-projet-app.git
cd fiche-projet-app
```

## 3. Mettre en place le fichier `.env`

Copiez le `.env` récupéré de l'autre machine **à la racine du projet**, à côté
de `docker-compose.yml`.

Trois pièges classiques sous Windows :

- **Le nom du fichier.** L'Explorateur masque les extensions connues : un
  fichier créé avec le Bloc-notes s'appelle souvent `.env.txt` alors qu'il
  s'affiche comme `.env`. Vérifiez avec `dir` dans PowerShell — la colonne
  Name doit afficher exactement `.env`. Pour renommer :
  `Rename-Item .env.txt .env`.
- **L'éditeur.** Modifiez le fichier avec VS Code ou Notepad++, pas avec
  Word ni WordPad.
- **Les guillemets.** Les valeurs s'écrivent sans guillemets :
  `DB_PASSWORD=motdepasse`, jamais `DB_PASSWORD="motdepasse"` (les guillemets
  feraient partie du mot de passe).

Le fichier `.env` est ignoré par Git (voir `.gitignore`) : il ne risque pas
d'être committé par erreur. Il contient malgré tout des secrets réels
(identifiants Google, mot de passe SMTP) — traitez ce poste de test en
conséquence, et ne le laissez pas traîner sur une machine partagée.

## 4. Adapter le `.env` au test local

**C'est l'étape à ne pas sauter.** Un `.env` de production recopié tel quel
fonctionne, mais l'instance de test enverra alors de **vrais emails aux
collègues** à chaque changement de statut, et créera de **vrais dossiers**
dans le Drive de l'établissement. Modifiez ces quatre lignes :

```dotenv
# L'URL écrite dans les liens des emails : doit pointer vers l'instance locale
APP_BASE_URL=http://localhost:8080

# Vide = aucune notification n'est envoyée (l'application le journalise et
# poursuit normalement). Indispensable pour ne pas écrire aux vrais valideurs.
MAIL_FROM=

# Aucun dossier créé dans le Drive partagé de l'établissement ; le lien de
# pièces jointes reste saisissable à la main sur la fiche.
GOOGLE_DRIVE_ENABLED=false

# Base locale, sans rapport avec celle de production : mettez ce que vous voulez.
DB_PASSWORD=test-local
```

Pour dérouler **tout le workflow de validation avec un seul compte**, mettez
votre propre adresse dans les quatre listes de rôles. Un utilisateur peut
cumuler les rôles, et rien n'interdit de valider un dossier que l'on a
soi-même créé :

```dotenv
ROLES_ADMIN=vous@votredomaine.fr
ROLES_COMPTA=vous@votredomaine.fr
ROLES_VIESCO=vous@votredomaine.fr
ROLES_DIRECTION=vous@votredomaine.fr
ROLES_LECTURE_SEULE=
```

⚠️ Laissez bien `ROLES_LECTURE_SEULE` vide : une adresse inscrite dans cette
liste reçoit `ROLE_LECTURE_SEULE` **à la place** de `ROLE_PROF` et ne peut
alors plus rien créer.

En revanche, **ne touchez pas** à `ALLOWED_EMAIL_DOMAIN`, `GOOGLE_CLIENT_ID`
ni `GOOGLE_CLIENT_SECRET` : ce sont eux qui permettent la connexion.

## 5. Autoriser `localhost` dans Google Cloud Console

L'identifiant OAuth copié n'autorise pour l'instant que l'URL de l'instance
réelle. Il faut lui ajouter l'adresse locale :

1. Ouvrez [console.cloud.google.com](https://console.cloud.google.com/), projet
   de l'établissement, menu **APIs & Services > Identifiants**.
2. Cliquez sur l'ID client OAuth 2.0 utilisé par l'application (celui dont la
   valeur correspond à `GOOGLE_CLIENT_ID` dans votre `.env`).
3. Dans **URI de redirection autorisés**, cliquez *Ajouter un URI* et saisissez
   exactement :
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
4. Enregistrez.

Quelques précisions utiles :

- C'est un **ajout**, pas un remplacement : l'URI de production reste en place
  et l'instance réelle continue de fonctionner normalement.
- `http` (et non `https`) est correct ici : Google accepte le HTTP en clair
  pour `localhost` uniquement, précisément pour ce cas d'usage.
- La prise en compte est parfois différée de quelques minutes côté Google.
- Si l'écran de consentement est encore en statut *Test*, seuls les comptes
  déclarés comme testeurs pourront se connecter — ajoutez le vôtre.

## 6. Démarrer l'application

```powershell
docker compose up --build
```

La **première** exécution compile l'application et télécharge les dépendances
Maven : comptez 5 à 10 minutes, avec beaucoup de lignes de téléchargement.
C'est normal, et les démarrages suivants prennent quelques secondes.

L'application est prête quand les logs affichent
`Started FicheProjetApplication in ... seconds`.

Pour tester sans le service de sauvegarde automatique (inutile en local) :

```powershell
docker compose up --build db app
```

## 7. Vérifier que tout fonctionne

1. **La sonde de santé**, dans un autre terminal :
   ```powershell
   curl.exe http://localhost:8080/actuator/health
   ```
   Réponse attendue : `{"status":"UP"}`. Elle ne demande pas
   d'authentification : si elle répond, l'application et sa base se parlent
   correctement.
2. **La connexion** : ouvrez <http://localhost:8080>, cliquez sur *Se
   connecter avec Google* et choisissez un compte du domaine autorisé. Vous
   arrivez sur le tableau de bord.
3. **Le workflow** : créez un dossier de test, soumettez-le, puis validez-le
   successivement au titre de la Comptabilité, de la Vie Scolaire et de la
   Direction. Testez aussi un refus, qui doit renvoyer le dossier en
   `A_CORRIGER` sans perdre les validations déjà obtenues.

Utilisez bien `http://localhost:8080` et pas `http://127.0.0.1:8080` :
l'URI de redirection déclarée chez Google porte sur `localhost`, et les deux
écritures ne sont pas interchangeables pour lui.

## 8. Commandes du quotidien

| Objectif | Commande (à la racine du projet) |
|---|---|
| Démarrer en arrière-plan | `docker compose up -d` |
| Voir les logs de l'application | `docker compose logs -f app` |
| Arrêter (en conservant les données) | `docker compose down` |
| **Tout remettre à zéro** (supprime la base de test) | `docker compose down -v` |
| Reconstruire après un `git pull` | `docker compose up --build -d` |
| État des conteneurs | `docker compose ps` |

Les données de test vivent dans un volume Docker nommé `db_data`, totalement
distinct de la base de production : `docker compose down -v` repart d'une base
vide, ce qui est la façon la plus simple de recommencer un test proprement.

## 9. Dépannage

**`la variable DB_PASSWORD doit être définie dans .env`**
Docker Compose ne trouve pas votre fichier : mauvais nom (`.env.txt`), ou
commande lancée depuis un autre dossier que la racine du projet.

**`redirect_uri_mismatch` au moment de la connexion**
L'étape 5 n'est pas faite, pas encore propagée, ou l'URI comporte une faute.
Elle doit être exactement `http://localhost:8080/login/oauth2/code/google` :
pas de `/` final, pas de `https`, pas de majuscule.

**`Ports are not available` / `port is already allocated` (8080 ou 5432)**
Un autre programme occupe le port. Pour identifier le coupable :
`netstat -ano | findstr :8080`. Un PostgreSQL installé sur le poste occupe
typiquement le 5432 ; on peut alors changer la publication du port dans
`docker-compose.yml` (`"127.0.0.1:5433:5432"`) sans rien changer d'autre :
l'application joint la base par le réseau interne de Docker, pas par ce port.

**Renvoyé vers la page de connexion avec une erreur, ou accès refusé**
L'adresse Google utilisée n'appartient pas au domaine `ALLOWED_EMAIL_DOMAIN`.
Sans ce domaine renseigné, aucune connexion n'est possible (refus par défaut).

**Aucun email reçu pendant le test**
C'est attendu si vous avez laissé `MAIL_FROM=` vide (étape 4) ; les logs
indiquent `Aucune adresse d'expediteur configuree (MAIL_FROM)`. Pour tester
réellement les envois, renseignez `MAIL_FROM` et un SMTP, en veillant à ce que
les destinataires (les listes `ROLES_*`) soient votre propre adresse.

**`Schema validation: missing table [commentaires]` (ou toute autre table), et l'application redémarre en boucle**
Le schéma de la base n'a pas été créé. Depuis la version publiée ici, il est
géré par **Flyway** (`src/main/resources/db/migration`), qui applique les
migrations au démarrage avant qu'Hibernate ne valide le schéma
(`ddl-auto=validate`, il ne crée jamais rien lui-même). Le message nomme la
première table manquante rencontrée, pas nécessairement la seule.

Regardez le tout début des logs : entre l'ouverture du pool de connexions
(`HikariPool-1 - Start completed`) et Hibernate, vous devez voir Flyway
annoncer ses migrations (`Migrating schema "public" to version "1 - init"`,
jusqu'à la version 6). **Si aucune ligne Flyway n'apparaît**, c'est que le
code compilé est une version antérieure à l'adoption de Flyway : le dossier
de travail n'est pas un clone de ce dépôt, mais une copie plus ancienne du
projet. Vérifiez-le d'un coup d'œil :

```powershell
dir src\main\java\fr
```

Le seul sous-dossier attendu est `ficheprojet`. Toute autre arborescence
signale un code plus ancien. La correction consiste à repartir d'un clone
propre et d'une base vierge :

```powershell
docker compose down -v          # supprime la base de test incomplète
cd ..
git clone https://github.com/AxelM35/fiche-projet-app.git fiche-projet-test
cd fiche-projet-test
# recopier le .env, puis :
docker compose build --no-cache
docker compose up
```

`--no-cache` évite de réutiliser une couche Docker construite à partir de
l'ancien code. Pour inspecter ce que contient réellement la base (adaptez
l'utilisateur et le nom de base à votre `.env`) :

```powershell
docker compose exec db psql -U fiche_projet_user -d fiche_projet -c "\dt"
```

**Le build échoue ou Docker ne démarre pas**
Vérifiez que Docker Desktop est bien lancé (*Engine running*) et que la
virtualisation WSL 2 est active. En cas de doute, `docker run hello-world`
valide l'installation indépendamment du projet.

## 10. Ce qui diffère d'une instance réelle

- **Pas de HTTPS** : acceptable pour `localhost`, mais interdit dès que
  l'instance est accessible depuis le réseau (voir `GUIDE_DEPLOIEMENT.md` §5).
- **Pas d'emails** tant que `MAIL_FROM` est vide — les relances automatiques
  sur dossiers bloqués sont donc muettes elles aussi.
- **Pas de création de dossiers Drive** avec `GOOGLE_DRIVE_ENABLED=false`.
- **Sauvegardes sans objet** : le service `db-backup` tourne mais ses dumps
  n'ont aucun intérêt sur un poste de test.

Ce poste ne doit pas être exposé à d'autres utilisateurs : sans HTTPS, les
échanges d'authentification circuleraient en clair sur le réseau.
