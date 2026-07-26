# fiche-projet-app

Application web de gestion et de validation des voyages scolaires du College Saint-Helier, en remplacement du workflow historique base sur Google Sheets.

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

## Demarrage local avec Docker Compose

```bash
cp .env.example .env
# completer .env : mot de passe DB, identifiants Google OAuth2, SMTP...
docker compose up --build
```

L'application est alors disponible sur http://localhost:8080.

Un service `db-backup` sauvegarde automatiquement la base PostgreSQL (voir
[docs/SAUVEGARDE.md](docs/SAUVEGARDE.md) pour la configuration et la procédure de restauration).

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

## Architecture

```
src/main/java/fr/collegesthelier/voyages/
├── VoyagesApplication.java     Point d'entree Spring Boot
├── config/                     Securite (SecurityConfig), Async, proprietes (@ConfigurationProperties)
├── security/                   CustomOAuth2UserService (authentification + RBAC)
├── model/                      Entite JPA Projet, enum StatutProjet
├── repository/                 ProjetRepository (Spring Data JPA)
├── dto/                        ProjetFormDTO, RefusFormDTO (validations Jakarta)
├── service/                    ProjetService (workflow), NotificationService (emails async)
├── event/                      ProjetEvent
├── exception/                  Exceptions metier
└── web/                        ProjetController, GlobalExceptionHandler

src/main/resources/
├── application.properties
└── templates/                  dashboard.html (Kanban), formulaire.html, fragments/navbar.html
```
