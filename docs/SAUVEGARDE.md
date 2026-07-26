# Sauvegarde et restauration de la base PostgreSQL

## Comment ça marche

Le service `db-backup` (dans `docker-compose.yml`) fait un `pg_dump` planifié de la base
et écrit les dumps compressés dans `./backups/` (sur la machine hôte, hors du volume
Docker de PostgreSQL) :

```
backups/
├── daily/
├── weekly/
├── monthly/
└── last/
```

- Fréquence par défaut : une fois par jour (`BACKUP_SCHEDULE=@daily` dans `.env`).
- Rétention par défaut : 7 jours / 4 semaines / 6 mois (`BACKUP_KEEP_DAYS`,
  `BACKUP_KEEP_WEEKS`, `BACKUP_KEEP_MONTHS` dans `.env`).
- `./backups/` est ignoré par git (`.gitignore`) — **ce n'est pas une sauvegarde
  hors site**. Pour une vraie protection (perte de disque, du serveur...), copier
  régulièrement ce dossier ailleurs (autre machine, stockage cloud...).

Le service démarre avec les autres via `docker compose up`, aucune action
supplémentaire n'est nécessaire une fois `.env` renseigné.

La page admin "Santé" (`/admin/sante`) affiche la date de la dernière sauvegarde
trouvée dans `./backups/last/` (lecture seule, montée dans le conteneur `app`).

## Déclencher une sauvegarde immédiate

Volontairement **pas de bouton dans l'application** pour ça : il faudrait soit
exécuter un processus système (`pg_dump`) depuis le serveur web à la demande
d'une requête HTTP, soit donner à l'appli un accès au conteneur `db-backup`
(socket Docker exposé = tout le serveur devient pilotable depuis l'appli web).
Les deux sont une surface d'attaque à éviter dans un panneau admin.

À la place, depuis ton terminal :

```bash
docker compose exec db-backup /backup.sh
```

C'est le script interne à l'image `prodrigestivill/postgres-backup-local`
(d'après sa documentation) — si la commande ne trouve pas le script, vérifie
son emplacement exact avec `docker compose exec db-backup ls /`.

## Tester une restauration

**Une sauvegarde qui n'a jamais été restaurée n'est pas fiable.** Ce test se fait
sur une base temporaire, sans toucher à la base réelle.

1. Repérer un dump récent :
   ```bash
   ls backups/daily
   ```

2. Démarrer un PostgreSQL jetable sur un autre port :
   ```bash
   docker run --rm -d --name pg-test-restore \
     -e POSTGRES_DB=voyages_scolaires \
     -e POSTGRES_USER=voyages_user \
     -e POSTGRES_PASSWORD=test \
     -p 5433:5432 \
     postgres:16-alpine
   ```

3. Restaurer le dump dedans (adapter le nom de fichier) :
   ```bash
   gunzip -c backups/daily/voyages_scolaires-*.sql.gz | \
     docker exec -i pg-test-restore psql -U voyages_user -d voyages_scolaires
   ```

4. Vérifier que les données sont bien là :
   ```bash
   docker exec -it pg-test-restore psql -U voyages_user -d voyages_scolaires -c "SELECT count(*) FROM projets;"
   ```

5. Nettoyer :
   ```bash
   docker stop pg-test-restore
   ```

A faire périodiquement (par exemple à chaque changement de version majeure de
l'application), pas seulement une fois à la mise en place.

## Restauration réelle (reprise après incident)

Sur la base réelle, en cas de perte de données :

```bash
docker compose stop app
gunzip -c backups/daily/voyages_scolaires-<date>.sql.gz | \
  docker compose exec -T db psql -U voyages_user -d voyages_scolaires
docker compose start app
```

Si la base contient déjà des données partielles/corrompues, il faut d'abord la
vider (`DROP SCHEMA public CASCADE; CREATE SCHEMA public;` avant le restore) pour
éviter des conflits de clés primaires avec le contenu du dump.
