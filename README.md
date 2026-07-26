# fiche-projet-app

Fiche Projet numérique : application web de gestion et de validation des projets de voyages scolaires du Collège Saint-Helier, en remplacement du workflow historique base sur Google Sheets.

## Stack technique

- **Backend** : Java 17, Spring Boot 3 (Web, Data JPA, Security, Mail, Validation)
- **Base de donnees** : PostgreSQL, pilotee via Hibernate
- **Frontend** : Thymeleaf + Bootstrap 5 (CDN)
- **Securite** : Spring Security avec authentification Google OAuth2, RBAC par role
- **Infrastructure** : Docker / Docker Compose

## Fonctionnement

Chaque projet de voyage suit un workflow lineaire de validation :

```
BROUILLON -> EN_ATTENTE_COMPTA -> EN_ATTENTE_VIE_SCOLAIRE -> EN_ATTENTE_DIRECTION -> VALIDE
```

A tout moment durant une etape d'attente, le dossier peut etre **refuse** : il repasse au statut `A_CORRIGER` et le motif est enregistre. Les validations deja obtenues aux etapes anterieures sont conservees : le professeur corrige puis resoumet le dossier, qui reprend directement a l'etape qui a refuse, sans faire revalider ceux qui avaient deja donne leur accord.

Un tableau de bord Kanban (`/dashboard`) affiche les projets regroupes par etape. Chaque fiche projet (`/projets/{id}`) presente le detail du dossier organise en cartes thematiques (Le Voyage, Le Responsable, Le Groupe, Le Budget) avec des actions contextuelles selon le role de l'utilisateur connecte.

## Roles (RBAC)

Tout utilisateur Google authentifie avec une adresse du domaine autorise recoit `ROLE_PROF`. Des listes d'emails configurees dans `application.properties` (ou via variables d'environnement) attribuent en plus :

- `ROLE_COMPTA` : validation budgetaire
- `ROLE_VIESCO` : validation vie scolaire
- `ROLE_DIRECTION` : validation finale
- `ROLE_ADMIN` : administration

Un utilisateur peut cumuler plusieurs roles, à une exception près : un email
inscrit dans `ROLES_LECTURE_SEULE` reçoit `ROLE_LECTURE_SEULE` **à la place**
de `ROLE_PROF` (jamais les deux). Ce rôle est destiné à un observateur (ex.
secrétariat) qui consulte tous les dossiers sans jamais pouvoir en créer,
modifier ou valider un seul.

Un `ROLE_ADMIN` peut aussi attribuer des roles directement depuis
l'application (`/admin/roles`, lien "Administration" dans la barre de
navigation), sans redémarrage. Ces attributions sont stockées en base et
s'ajoutent toujours aux listes `.env` (jamais ne les remplacent) : retirer
quelqu'un ajouté via `.env` nécessite toujours de modifier `.env`.

## Demarrage local avec Docker Compose

```bash
cp .env.example .env
# completer .env : mot de passe DB, identifiants Google OAuth2, SMTP...
docker compose up --build
```

L'application est alors disponible sur http://localhost:8080.

Un service `db-backup` sauvegarde automatiquement la base PostgreSQL (voir
[docs/SAUVEGARDE.md](docs/SAUVEGARDE.md) pour la configuration et la procédure de restauration).

Pour un déploiement réel (HTTPS, identifiants Google/SMTP réels, checklist
avant ouverture aux utilisateurs...), voir le guide dédié
[docs/GUIDE_DEPLOIEMENT.md](docs/GUIDE_DEPLOIEMENT.md), écrit pour un profil
administrateur infrastructure plutôt que développeur.

## Demarrage sans Docker (developpement)

Necessite un PostgreSQL local et le JDK 17+.

```bash
export DB_PASSWORD=changeme
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
```

Les tests d'integration utilisent une base H2 en memoire (voir `src/test/resources/application-test.properties`) et ne necessitent ni PostgreSQL ni identifiants OAuth2/SMTP reels.

## Fonctionnalités complémentaires

Au-delà du workflow de validation, l'application propose :

- **Export PDF** de la fiche (récapitulatif + historique de validation + fil de commentaires) depuis n'importe quel dossier.
- **Fil de commentaires** par dossier (échanges organisateur/valideurs, indépendants du motif de refus).
- **Pièces jointes** : lien Google Drive par dossier, avec création automatique du dossier partagé (optionnelle, voir `.env.example`).
- **Relances automatiques** par email sur les dossiers bloqués depuis trop longtemps.
- **Dashboard Admin** (`/admin/...`) : gestion des rôles, recherche avancée + export CSV, archivage (unitaire ou groupé par année scolaire), journal d'audit, dossiers bloqués, statistiques consolidées, état de santé de l'application.
- **Filtres avancés** côté client sur le tableau de bord (nom, classe, organisateur, période de départ).

Le détail de chaque fonctionnalité (décisions, fichiers concernés, tests) est documenté dans [docs/CAHIER_DES_CHARGES.md](docs/CAHIER_DES_CHARGES.md).

## Architecture

```
src/main/java/fr/collegesthelier/ficheprojet/
├── FicheProjetApplication.java Point d'entree Spring Boot (@EnableScheduling pour les relances)
├── config/                     Securite (SecurityConfig), Async, proprietes (@ConfigurationProperties :
│                                roles, notifications, relances, Drive, securite)
├── security/                   CustomOAuth2UserService (authentification + RBAC), LoginRateLimitingFilter
├── model/                      Entites JPA : Projet, Commentaire, JournalEntree, RoleAttribution,
│                                enums StatutProjet / RoleMetier
├── repository/                 Spring Data JPA : ProjetRepository, CommentaireRepository,
│                                JournalEntreeRepository, RoleAttributionRepository
├── dto/                        DTO de formulaire (ProjetFormDTO, RefusFormDTO, CommentaireFormDTO...) et
│                                de lecture (ProjetConsultationDTO, StatistiquesDTO, TableauDeBordStatsDTO...)
├── service/                    ProjetService (workflow), CommentaireService, StatistiquesService,
│                                JournalService, NotificationService (emails async), RelanceService,
│                                GoogleDriveService, PdfExportService, RoleAdminService, SanteService,
│                                AnneeScolaireUtil
├── event/                      ProjetEvent, CommentaireEvent
├── exception/                  Exceptions metier
└── web/                        ProjetController (fiches), AdminController (dashboard admin),
                                 LoginController, GlobalExceptionHandler, GlobalModelAttributes

src/main/resources/
├── application.properties
├── db/migration/               Migrations Flyway (V1 baseline, puis une par evolution de schema)
└── templates/                  dashboard.html (Kanban), formulaire.html / consultation.html (fiche),
                                 admin-*.html (dashboard admin), pdf/ (export PDF), fragments/ (navbar,
                                 stepper, commentaires)
```
