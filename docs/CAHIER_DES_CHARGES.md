# Cahier des charges — Application de gestion des voyages scolaires
### Collège Saint-Helier — état des lieux, chantiers restants et pistes d'évolution

*Document de travail, à discuter et prioriser ensemble avant de démarrer le développement.*

---

## 1. État des lieux (ce qui est déjà fait)

- Workflow linéaire à 4 rôles : `BROUILLON → EN_ATTENTE_COMPTA → EN_ATTENTE_VIE_SCOLAIRE → EN_ATTENTE_DIRECTION → VALIDE`, avec retour en `A_CORRIGER` qui **conserve les validations déjà acquises** et reprend à l'étape de refus.
- Authentification Google OIDC, RBAC par listes d'emails (`ROLE_PROF` par défaut + `COMPTA`/`VIESCO`/`DIRECTION`/`ADMIN`), `ROLE_ADMIN` peut valider n'importe quelle étape.
- Verrouillage optimiste applicatif (comparaison manuelle de version, pas seulement le check JPA automatique).
- Séparation stricte DTO/entité (`ProjetFormDTO`, `RefusFormDTO`, `ProjetConsultationDTO`), protection Mass Assignment.
- Notifications email asynchrones (`@Async` + `AFTER_COMMIT`) à chaque changement de statut, texte brut, échec catché sans casser le workflow.
- CSRF géré automatiquement (thymeleaf-extras-springsecurity6 + formulaires `th:action`) — **déjà correct, rien à faire ici**.
- UI Material 3 (Bootstrap 5 + variables `--bs-*`/`--md-*`), Kanban 5 colonnes, tuiles de stats, recherche côté client, vue consultation lecture seule, duplication en brouillon.
- 17 tests (H2 en mémoire) couvrant service + contrôleur.

## 2. Chantiers techniques — mise en production

Ce qui est nécessaire avant un vrai lancement, par thème.

### 2.1 Emails
- [ ] Tester l'envoi réel avec un serveur SMTP (jamais fait en conditions réelles) — vérifier délivrabilité, SPF/DKIM si domaine propre.
- [ ] Passer de `SimpleMailMessage` (texte brut) à des emails HTML (`MimeMessage` + template Thymeleaf), plus lisibles et à l'image de l'appli.

### 2.2 Configuration réelle
- [ ] `ALLOWED_EMAIL_DOMAIN` → `college-sthelier.fr`.
- [ ] Adresses réelles pour `ROLES_ADMIN`/`COMPTA`/`VIESCO`/`DIRECTION` (actuellement toutes identiques pour les tests).
- [ ] Passer l'app Google Cloud OAuth2 de "Test" à "Production" (écran de consentement) — vérifier si Google impose une validation pour les scopes utilisés (`openid,profile,email` sont non sensibles, donc a priori pas de revue Google requise, à confirmer).
- [ ] Rappel opérationnel déjà connu : déconnexion/reconnexion obligatoire après tout changement de rôles.

### 2.3 Déploiement
- [ ] HTTPS via reverse proxy (nginx/Caddy/Traefik) — obligatoire pour OAuth2 en production et pour la confidentialité des données.
- [x] Migration vers Flyway : migration baseline `V1__init.sql` correspondant au schéma généré par `ddl-auto=update`, `ddl-auto` passé en `validate`. **À tester sur une base vide (`docker compose down -v && docker compose up --build`) avant de considérer que c'est acquis.**
- [x] Stratégie de sauvegarde PostgreSQL : service `db-backup` (dumps quotidiens compressés, rétention configurable, voir `docs/SAUVEGARDE.md`). **Le test de restauration réel reste à faire par toi** — la procédure est documentée mais je ne peux pas l'exécuter depuis mon environnement (pas de Docker).
- [x] CI GitHub Actions (`.github/workflows/ci.yml`) : lance `./mvnw test` sur chaque push/PR vers `main`.
- [ ] Exposer un endpoint de santé (`spring-boot-starter-actuator` `/actuator/health`) pour le monitoring du conteneur.

### 2.4 Revue de sécurité
- [x] Premiers en-têtes HTTP (CSP, HSTS, Permissions-Policy) dans `SecurityConfig` — CSP garde `'unsafe-inline'` (scripts/styles inline dans les templates), à durcir plus tard avec des nonces. Ne remplace pas la revue complète ci-dessous.
- [ ] Revue complète avant mise en ligne (rate limiting login, scan des dépendances — Dependabot ou OWASP dependency-check, externalisation des scripts/styles inline pour retirer `'unsafe-inline'`).
- [ ] Vérifier qu'aucun secret n'est committé (`.env` déjà ignoré — bon point) et que les logs ne journalisent pas de données personnelles sensibles.
- [ ] Réfléchir au RGPD si des données d'élèves mineurs venaient à être stockées nominativement à l'avenir (voir §3, pièces jointes) — actuellement seul un effectif chiffré est stocké, pas de liste nominative.

### 2.5 Identité visuelle
- [ ] Remplacer le violet M3 générique (#6750A4) par les couleurs officielles du collège, si elles existent (logo, charte graphique).

---

## 3. Pistes d'évolution fonctionnelle (post-lancement)

Idées à évaluer, aucune n'est engagée — à trier selon la valeur perçue.

- **Pièces jointes** : devis fournisseur, autorisation parentale type, RIB, attestation d'assurance. C'est souvent le vrai point de friction d'un workflow papier/Sheets → à fort impact perçu, mais pose la question du stockage (disque local du conteneur ? bucket S3-compatible ? Google Drive via l'API, cohérent avec l'écosystème Google déjà utilisé pour l'auth ?). Le prototype en Google AppScript permettait de créer un dossier dans un Google Drive pour que chaque utilisateur puisse mettre des fichiers utiles. Voir avec le client comment transposer cette feature en conservant l'API Google.
- **Export / impression** : générer un PDF récapitulatif de la fiche validée (utile pour les archives papier de l'établissement ou l'inspection académique).
- **Historique d'audit complet** : aujourd'hui seules 4 dates de validation sont conservées ; un vrai journal d'événements (qui a fait quoi, quand, avec quel commentaire) donnerait une traçabilité plus fine, utile en cas de litige ou de question a posteriori.
- **Archivage par année scolaire** : avec les années qui s'accumulent, prévoir un filtre/archivage pour ne pas alourdir le tableau de bord (actuellement pas de pagination ni de filtre par année).
- **Relances automatiques** : email de rappel si un dossier reste bloqué plus de N jours à une étape (compta/vie scolaire/direction qui oublie de traiter).
- **Fil de commentaires** sur un dossier plutôt qu'un unique motif de refus, pour permettre des échanges (ex : la direction demande une précision sans forcément refuser).
- ✅ **Rôle lecture seule** (ex. secrétariat) qui peut consulter tous les dossiers sans droit de validation — fait (`ROLE_LECTURE_SEULE`, liste d'emails `ROLES_LECTURE_SEULE`, vide par défaut). Un tel utilisateur voit toujours la vue de consultation (jamais le formulaire éditable), quel que soit le statut du dossier, avec le motif de refus affiché si `A_CORRIGER`.
- **Statistiques consolidées** : budget total engagé par année/par classe, taux de refus par étape, délai moyen de traitement par rôle seulement pour le rôle Admin
- **Filtres avancés sur le dashboard** : par classe, par période, par organisateur (aujourd'hui recherche client simple uniquement).
- **Dashboard Admin** : Créer un dashboard Admin pour pouvoir modifier des paramètres directement dans l'application (modification des rôles, ajout/retrait d'adresse mail, suppression de projet, archivage des années précédentes et autres fonctions utiles à discuter avec le client).
  - ✅ **Gestion des rôles** (`/admin/roles`) : un Admin ajoute/retire des attributions de rôle par email, sans redémarrage. Stocké en base (table `role_attributions`, migration `V2`), en complément des listes `.env` (jamais en remplacement — `ROLES_ADMIN` reste le filet de sécurité contre un verrouillage total). Page réservée à `ROLE_ADMIN` y compris côté contrôleur (contrairement au reste de l'appli, en lecture ouverte à tous).
  - ✅ **Suppression / archivage de projet** : bouton "..." (admin, sur chaque carte du dashboard) → modale "Archiver" (réversible, retire du tableau de bord) ou "Supprimer définitivement" (irréversible, avec confirmation JS). Page `/admin/archives` pour retrouver et désarchiver/supprimer les dossiers archivés. Nouveau champ `Projet.archive` (migration `V3`), indépendant du statut de workflow.
  - ✅ **Journal d'audit** (`/admin/journal`, table `journal_entrees`, migration `V4`) : trace création, soumission, chaque validation, refus (avec motif), archivage/désarchivage, suppression définitive, modification admin d'un dossier validé, réaffectation d'organisateur, ajout/retrait de rôle — avec auteur, date, dossier concerné (nom dénormalisé, survit à une suppression définitive) et détail. Les 200 événements les plus récents.
  - ✅ **Dossiers bloqués** (`/admin/dossiers-bloques`) : dossiers en attente de validation triés du plus ancien au plus récent, avec le nombre de jours d'attente (calculé à partir des dates de validation déjà existantes, pas de nouveau champ) — pour relancer manuellement en attendant d'éventuelles relances automatiques.
  - ✅ **Réaffectation d'organisateur** (Admin, sur la fiche projet) : change l'organisateur d'un dossier sans toucher au statut ni aux validations déjà obtenues.
  - ✅ **Modification d'un dossier `VALIDE`** : un Admin peut désormais rouvrir et corriger un dossier déjà validé (les autres rôles restent bloqués) ; tracé dans le journal d'audit.
  - ⬜ Recherche admin avancée (tous statuts + archivés) et export CSV.
  - ⬜ Email de test SMTP + interrupteur temporaire des notifications.
  - ⬜ Tableau "santé" (dernière sauvegarde, nombre de dossiers, version) + sauvegarde à la demande.
  - ⬜ Autres fonctions à discuter avec le client.

## 4. Pistes UX/UI

- **Couleurs officielles du collège** (déjà cité en §2.5, mais c'est autant un sujet UI que technique).
- **Stepper visuel du workflow** sur la fiche projet (1-2-3-4 avec étape courante mise en évidence), en complément du badge de statut actuel — rendrait la progression plus lisible pour l'organisateur.
- **Écran récapitulatif avant soumission** : relire les infos clés avant de passer en `EN_ATTENTE_COMPTA`, pour éviter les allers-retours.
- **Responsive mobile** : vérifier spécifiquement le rendu du Kanban 5 colonnes sur petit écran (probable besoin de scroll horizontal ou de vue liste alternative).
- **Feedback visuel** sur les actions asynchrones (spinner sur les boutons de validation/refus le temps de la requête).
- **Aide contextuelle** pour les nouveaux professeurs à la première connexion (tooltip ou courte visite guidée expliquant le workflow).

## 5. Priorisation proposée (à valider)

| Phase | Contenu | Objectif |
|---|---|---|
| **1 — Bloquant avant mise en prod** | HTTPS, Flyway, config réelle (domaine/rôles/OAuth prod), sauvegardes PostgreSQL testées, revue de sécurité, test SMTP réel | Rendre le déploiement actuel fiable et sûr |
| **2 — Confort** | CI (tests auto), couleurs officielles, emails HTML, stepper visuel du workflow | Finitions avant l'ouverture aux utilisateurs réels |
| **3 — Itératif avant le lancement** | Pièces jointes, export PDF, historique d'audit, calendrier, relances automatiques, filtres avancés | Amélioration continue selon les retours terrain |

## 6. Questions ouvertes (besoin de ta décision)

1. Le collège a-t-il des couleurs officielles/une charte graphique à utiliser à la place du violet M3 générique ? --> Oui à  confirmer avec le client pour les couleurs exactes.
2. Pièces jointes : si on les ajoute un jour, préférence de stockage — disque du serveur, bucket compatible S3, ou Google Drive via API (vu l'écosystème Google déjà en place) ? --> Google Drive via API
3. Volumétrie attendue (nombre de voyages/an) — utile pour dimensionner archivage et pagination du dashboard. --> une cinquantaine par an
4. Un rôle "lecture seule" (secrétariat, autre) est-il pertinent à moyen terme ? --> Oui
5. Le statut de l'établissement impose-t-il des obligations d'accessibilité (RGAA) à respecter formellement ? --> Non, faire au mieux

---