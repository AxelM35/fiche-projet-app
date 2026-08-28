# fiche-projet-app

Fiche Projet numérique : application web de gestion et de validation des projets de voyages scolaires, en remplacement d'un workflow sur tableur.

L'application est conçue pour être déployée telle quelle par n'importe quel établissement du second degré : aucun nom d'établissement, domaine ou adresse n'est écrit en dur. Tout se configure dans `.env` (voir `.env.example`), et chaque déploiement dispose de sa propre instance et de sa propre base.

## Aperçu

<!--
Captures d'écran à déposer dans docs/img/ puis à référencer ci-dessous
(voir docs/img/README.md pour les cadrages attendus) :

![Tableau de bord Kanban](docs/img/dashboard.png)
![Fiche projet](docs/img/fiche-projet.png)
![Dashboard administrateur](docs/img/admin.png)
-->

## Stack technique

- **Backend** : Java 17, Spring Boot 4 (Web, Data JPA, Security, Mail, Validation)
- **Base de données** : PostgreSQL, schéma versionné avec Flyway (Hibernate en `ddl-auto=validate`, il ne modifie jamais le schéma lui-même)
- **Frontend** : Thymeleaf + Bootstrap 5 (CDN)
- **Sécurité** : Spring Security avec authentification Google OAuth2, RBAC par rôle
- **Infrastructure** : Docker / Docker Compose

Le `pom.xml` cible Java 17 (version plancher supportée), tandis que les images Docker de build et d'exécution utilisent le JDK 25. La chaîne d'intégration continue teste les deux versions, afin de ne jamais livrer sur un JDK qui n'aurait pas été couvert par les tests.

## Fonctionnement

Chaque projet de voyage suit un workflow linéaire de validation :

```
BROUILLON -> EN_ATTENTE_COMPTA -> EN_ATTENTE_VIE_SCOLAIRE -> EN_ATTENTE_DIRECTION -> VALIDE
```

À tout moment durant une étape d'attente, le dossier peut être **refusé** : il repasse au statut `A_CORRIGER` et le motif est enregistré. Les validations déjà obtenues aux étapes antérieures sont conservées : le professeur corrige puis resoumet le dossier, qui reprend directement à l'étape qui a refusé, sans faire revalider ceux qui avaient déjà donné leur accord.

Un tableau de bord Kanban (`/dashboard`) affiche les projets regroupés par étape. Chaque fiche projet (`/projets/{id}`) présente le détail du dossier organisé en cartes thématiques (Le Voyage, Le Responsable, Le Groupe, Le Budget) avec des actions contextuelles selon le rôle de l'utilisateur connecté.

## Rôles (RBAC)

Tout utilisateur Google authentifié avec une adresse du domaine autorisé (`ALLOWED_EMAIL_DOMAIN`) reçoit `ROLE_PROF`. Des listes d'emails configurées dans `application.properties` (ou via variables d'environnement) attribuent en plus :

- `ROLE_COMPTA` : validation budgétaire
- `ROLE_VIESCO` : validation vie scolaire
- `ROLE_DIRECTION` : validation finale
- `ROLE_ADMIN` : administration

Un utilisateur peut cumuler plusieurs rôles, à une exception près : un email
inscrit dans `ROLES_LECTURE_SEULE` reçoit `ROLE_LECTURE_SEULE` **à la place**
de `ROLE_PROF` (jamais les deux). Ce rôle est destiné à un observateur (ex.
secrétariat) qui consulte tous les dossiers sans jamais pouvoir en créer,
modifier ou valider un seul.

Un `ROLE_ADMIN` peut aussi attribuer des rôles directement depuis
l'application (`/admin/roles`, lien "Administration" dans la barre de
navigation), sans redémarrage. Ces attributions sont stockées en base et
s'ajoutent toujours aux listes `.env` (jamais ne les remplacent) : retirer
quelqu'un ajouté via `.env` nécessite toujours de modifier `.env`.

## Configuration d'un établissement

Tout ce qui identifie l'établissement se règle dans `.env` :

| Variable | Rôle |
|---|---|
| `ETABLISSEMENT_NOM` | Nom affiché dans la barre de navigation, sur la page de connexion et dans les emails. Vide : la mention est masquée. |
| `ALLOWED_EMAIL_DOMAIN` | Seul domaine Google autorisé à se connecter. **Sans cette variable, aucune connexion n'est possible** (refus par défaut). |
| `ROLES_ADMIN` / `ROLES_COMPTA` / `ROLES_VIESCO` / `ROLES_DIRECTION` | Adresses des valideurs de chaque étape. |
| `MAIL_FROM`, `APP_BASE_URL` | Expéditeur des notifications et URL publique de l'instance. |

## Démarrage local avec Docker Compose

```bash
cp .env.example .env
# compléter .env : nom de l'établissement, domaine autorisé, mot de passe DB,
# identifiants Google OAuth2, SMTP...
docker compose up --build
```

L'application est alors disponible sur http://localhost:8080.

Un service `db-backup` sauvegarde automatiquement la base PostgreSQL (voir
[docs/SAUVEGARDE.md](docs/SAUVEGARDE.md) pour la configuration et la procédure de restauration).

Pour un déploiement réel (HTTPS, identifiants Google/SMTP réels, checklist
avant ouverture aux utilisateurs...), voir le guide dédié
[docs/GUIDE_DEPLOIEMENT.md](docs/GUIDE_DEPLOIEMENT.md), écrit pour un profil
administrateur infrastructure plutôt que développeur.

## Démarrage sans Docker (développement)

Nécessite un PostgreSQL local et le JDK 17 ou supérieur.

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

Les tests d'intégration utilisent une base H2 en mémoire (voir `src/test/resources/application-test.properties`) et ne nécessitent ni PostgreSQL ni identifiants OAuth2/SMTP réels.

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
src/main/java/fr/ficheprojet/
├── FicheProjetApplication.java Point d'entrée Spring Boot (@EnableScheduling pour les relances)
├── config/                     Sécurité (SecurityConfig), Async, propriétés (@ConfigurationProperties :
│                                établissement, rôles, notifications, relances, Drive, sécurité)
├── security/                   CustomOAuth2UserService (authentification + RBAC), LoginRateLimitingFilter
├── model/                      Entités JPA : Projet, Commentaire, JournalEntree, RoleAttribution,
│                                enums StatutProjet / RoleMetier
├── repository/                 Spring Data JPA : ProjetRepository, CommentaireRepository,
│                                JournalEntreeRepository, RoleAttributionRepository
├── dto/                        DTO de formulaire (ProjetFormDTO, RefusFormDTO, CommentaireFormDTO...) et
│                                de lecture (ProjetConsultationDTO, StatistiquesDTO, TableauDeBordStatsDTO...)
├── service/                    ProjetService (workflow), CommentaireService, StatistiquesService,
│                                JournalService, NotificationService (emails async), NotificationToggleService,
│                                RelanceService, GoogleDriveService, PdfExportService, RoleAdminService,
│                                SanteService, AnneeScolaireUtil
├── event/                      ProjetEvent, CommentaireEvent
├── exception/                  Exceptions métier
└── web/                        ProjetController (fiches), AdminController (dashboard admin),
                                 LoginController, SignalementErreurController, GlobalExceptionHandler,
                                 GlobalModelAttributes

src/main/resources/
├── application.properties
├── db/migration/               Migrations Flyway (V1 baseline, puis une par évolution de schéma)
└── templates/                  dashboard.html (Kanban), formulaire.html / consultation.html (fiche),
                                 admin-*.html (dashboard admin), pdf/ (export PDF), fragments/ (navbar,
                                 stepper, commentaires)
```
