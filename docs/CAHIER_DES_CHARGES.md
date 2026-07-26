# Cahier des charges — Fiche Projet numérique (gestion des projets de voyages scolaires)
### Collège Saint-Helier — état des lieux, chantiers restants et pistes d'évolution

*Document vivant : état des lieux, décisions prises et pistes encore ouvertes, mis à jour au fil du développement (voir aussi le suivi Git pour le détail commit par commit).*

---

## 1. État des lieux (ce qui est déjà fait)

- Workflow linéaire à 4 rôles : `BROUILLON → EN_ATTENTE_COMPTA → EN_ATTENTE_VIE_SCOLAIRE → EN_ATTENTE_DIRECTION → VALIDE`, avec retour en `A_CORRIGER` qui **conserve les validations déjà acquises** et reprend à l'étape de refus.
- Authentification Google OIDC, RBAC par listes d'emails (`ROLE_PROF` par défaut + `COMPTA`/`VIESCO`/`DIRECTION`/`ADMIN`), `ROLE_ADMIN` peut valider n'importe quelle étape.
- Verrouillage optimiste applicatif (comparaison manuelle de version, pas seulement le check JPA automatique).
- Séparation stricte DTO/entité (`ProjetFormDTO`, `RefusFormDTO`, `ProjetConsultationDTO`), protection Mass Assignment.
- Notifications email asynchrones (`@Async` + `AFTER_COMMIT`) à chaque changement de statut, texte brut, échec catché sans casser le workflow.
- CSRF géré automatiquement (thymeleaf-extras-springsecurity6 + formulaires `th:action`) — **déjà correct, rien à faire ici**.
- UI Material 3 (Bootstrap 5 + variables `--bs-*`/`--md-*`), Kanban 5 colonnes, tuiles de stats, recherche côté client, vue consultation lecture seule, duplication en brouillon.
- 88 tests (H2 en mémoire) couvrant services, contrôleurs et sécurité.

## 2. Chantiers techniques — mise en production

Ce qui est nécessaire avant un vrai lancement, par thème.

### 2.1 Emails
- [ ] Tester l'envoi réel avec un serveur SMTP (jamais fait en conditions réelles) — vérifier délivrabilité, SPF/DKIM si domaine propre.
- [x] Emails HTML : `NotificationService` construit desormais un `MimeMessage` (multipart texte brut + HTML) via `MimeMessageHelper`, rendu par le template Thymeleaf `templates/email/notification.html` (mise en page par tables, styles inline uniquement — pas de dépendance à `style.css` ni aux CDN, pour la compatibilité Outlook/Gmail). Couvre les 7 notifications de workflow et l'email de test admin ; motif de refus mis en évidence, bouton "Consulter le dossier" masqué si aucun lien (email de test). Testé via `EmailTemplateTest` (rendu du template) — **l'envoi réel avec un vrai client mail reste à valider par toi** (rendu visuel Gmail/Outlook), voir point precedent.
- [x] Notification de l'organisateur à chaque étape franchie (pas seulement validation finale/refus) : à `EN_ATTENTE_VIE_SCOLAIRE` et `EN_ATTENTE_DIRECTION`, l'organisateur reçoit désormais aussi un email ("votre dossier a été validé par X, en attente de Y"), en plus de la notification au rôle valideur suivant — 2 emails indépendants par transition (chacun avec son propre `try/catch`, un échec sur l'un n'empêche pas l'autre). Couvre tout le cycle de vie côté organisateur : soumission (déjà via `dateValidationProf`, pas d'email dédié), Compta, Vie Scolaire, Direction, validation finale, refus.

### 2.2 Configuration réelle
- [ ] `ALLOWED_EMAIL_DOMAIN` → `college-sthelier.fr`.
- [ ] Adresses réelles pour `ROLES_ADMIN`/`COMPTA`/`VIESCO`/`DIRECTION` (actuellement toutes identiques pour les tests).
- [ ] Passer l'app Google Cloud OAuth2 de "Test" à "Production" (écran de consentement) — vérifier si Google impose une validation pour les scopes utilisés (`openid,profile,email` sont non sensibles, donc a priori pas de revue Google requise, à confirmer).
- [ ] Rappel opérationnel déjà connu : déconnexion/reconnexion obligatoire après tout changement de rôles.

### 2.3 Déploiement
- [ ] HTTPS via reverse proxy (nginx/Caddy/Traefik) — obligatoire pour OAuth2 en production et pour la confidentialité des données. Marche à suivre détaillée (dont un exemple Caddy minimal) dans [`docs/GUIDE_DEPLOIEMENT.md`](GUIDE_DEPLOIEMENT.md#5-étape-3--https-obligatoire), destiné au responsable informatique en charge du déploiement.
- [x] Migration vers Flyway : migration baseline `V1__init.sql` correspondant au schéma généré par `ddl-auto=update`, `ddl-auto` passé en `validate`. **À tester sur une base vide (`docker compose down -v && docker compose up --build`) avant de considérer que c'est acquis.**
- [x] Stratégie de sauvegarde PostgreSQL : service `db-backup` (dumps quotidiens compressés, rétention configurable, voir `docs/SAUVEGARDE.md`). **Le test de restauration réel reste à faire par toi** — la procédure est documentée mais je ne peux pas l'exécuter depuis mon environnement (pas de Docker).
- [x] CI GitHub Actions (`.github/workflows/ci.yml`) : lance `./mvnw test` sur chaque push/PR vers `main`.
- [x] Endpoint de santé (`spring-boot-starter-actuator`) : `/actuator/health` public (pas d'authentification, voir `SecurityConfig`), seul endpoint exposé (`management.endpoints.web.exposure.include=health`), `show-details=never` (ne renvoie que `{"status":"UP"}`, jamais le détail des composants à un appelant anonyme). Indicateur mail désactivé (`management.health.mail.enabled=false`) : un incident SMTP ne doit pas faire passer tout le conteneur pour en panne. `Dockerfile` : `HEALTHCHECK` intégré à l'image (curl installé dans le runtime), `docker compose ps` / `depends_on: condition: service_healthy` en profiteront automatiquement (utile pour un futur reverse proxy HTTPS). Testé via `ActuatorHealthTest`.

### 2.4 Revue de sécurité
- [x] Premiers en-têtes HTTP (CSP, HSTS, Permissions-Policy) dans `SecurityConfig`.
- [x] Rate limiting sur les routes d'authentification (`/login`, `/oauth2/authorization/google`, `/login/oauth2/code/google`) : `LoginRateLimitingFilter` (fenêtre fixe maison, pas de bibliothèque dédiée — la règle est volontairement simple), 20 requêtes/minute par IP, au tout début de la chaîne de filtres Spring Security (avant toute session/CSRF). Volontairement **pas** un bean Spring (instancié directement dans `SecurityConfig` + `addFilterBefore`), sinon Spring Boot l'aurait *aussi* enregistré comme filtre servlet générique et exécuté deux fois par requête. **Limite connue** : s'appuie sur `request.getRemoteAddr()`, donc suppose qu'aucun reverse proxy ne s'intercale — à revoir (`X-Forwarded-For` + liste de proxies de confiance) une fois le reverse proxy HTTPS en place (voir §2.3). Testé via `LoginRateLimitingFilterTest`.
- [x] Scan de dépendances : `.github/dependabot.yml` (Maven, image Docker de base, GitHub Actions), mises à jour hebdomadaires proposées en PR.
- [x] **CSP durcie, `'unsafe-inline'` retiré** de `script-src` et `style-src`. Tous les scripts et styles inline des templates ont été externalisés :
  - Styles : les ~18 attributs `style="..."` (hors `templates/email/notification.html`, volontairement inline pour la compatibilité Gmail/Outlook, hors de portée de cette CSP) sont remplacés par des classes utilitaires dans `style.css` (`.pb-footer-sticky`, `.pb-page`, `.text-pre-wrap`, `.stat-value-compact`, `.kanban-col`).
  - Scripts : les 2 blocs `<script>` inline (`dashboard.html`, `formulaire.html`) déplacés vers `static/js/dashboard.js` et `static/js/formulaire.js`. Les attributs `onclick`/`onsubmit` (ajout/retrait d'accompagnateur, confirmation de suppression) remplacés par des écouteurs d'événements (délégation d'événements pour le cas des lignes ajoutées dynamiquement) ; la confirmation de suppression généralisée via un attribut `data-confirm` + `static/js/confirmation.js` réutilisable partout.
  - CSP finale : `script-src 'self' https://cdn.jsdelivr.net; style-src 'self' https://cdn.jsdelivr.net https://fonts.googleapis.com` (plus de `'unsafe-inline'`).
  - Vérifié par un lancement réel de l'appli (profil H2 test) : en-tête CSP sans `'unsafe-inline'`, fichiers JS servis, plus aucun `onclick`/`onsubmit`/`style=` dans les templates. **Le clic réel sur les boutons concernés (ajout/retrait accompagnateur, confirmation de suppression, recherche dashboard, modales) reste à valider par toi dans un vrai navigateur** — je n'ai pas d'outil de navigateur dans cet environnement pour l'automatiser.
- [x] **Vérification des secrets et des logs** :
  - Aucun secret commité : `.env` ignoré (jamais présent dans l'historique git), seul `.env.example` (valeurs vides/placeholders) est versionné. Recherche de motifs de clés/tokens (clé privée, clé AWS, token Slack...) dans tout le code : aucune trouvée.
  - Logs applicatifs relus un par un (`log.info/warn/error/debug`, une douzaine d'occurrences) : aucun mot de passe, jeton OAuth2/session, ni donnée de santé/sensible au sens RGPD. Quelques données personnelles apparaissent dans des logs de sécurité légitimes (email de connexion refusée/acceptée dans `CustomOAuth2UserService`, adresse IP dans `LoginRateLimitingFilter`, adresses email de destinataires en cas d'échec d'envoi dans `NotificationService`) — usage proportionné (traçabilité de sécurité/support), mais suppose des logs eux-mêmes protégés (accès restreint au serveur) et non conservés indéfiniment.
  - Correctif appliqué : `docker-compose.yml` ne limitait pas la taille des logs des conteneurs (driver `json-file` par défaut = illimité) → ajout de `logging: max-size: 10m, max-file: 5` sur les 3 services, pour borner à la fois l'usage disque et la durée de rétention de fait des logs (donc des données personnelles qu'ils contiennent).
- [ ] Reste à faire (hors scope de cette revue) : nonces CSP pour un durcissement supplémentaire (pas nécessaire tant que `'unsafe-inline'` est retiré), politique de rétention des logs formalisée (actuellement bornée en taille, pas en durée).

### 2.4bis Audit RGPD (revue des données personnelles stockées)

Inventaire réalisé en relisant le modèle de données (`Projet`, `RoleAttribution`, `JournalEntree`, `Commentaire`) et le service d'authentification. **Mis à jour** suite à l'ajout du fil de commentaires (§3) : la table `commentaires` est désormais couverte ci-dessous.

- **Aucune donnée nominative d'élève mineur stockée** : `effectif` est un simple nombre, `classesConcernees` un libellé de classe (ex. "5A, 5B"), jamais une liste de noms d'élèves. Confirme ce qui était déjà supposé dans ce document.
- **Données personnelles réellement présentes** (toutes des adultes, personnel de l'établissement ou tiers professionnels) :
  - Organisateur du projet : nom, email, téléphone (`organisateurNom/Email/telephoneOrganisateur`).
  - Accompagnateurs : uniquement des noms en texte libre (`accompagnateurs`, pas d'email/téléphone).
  - Contact de l'organisme/prestataire de voyage (facultatif) : nom, téléphone, email.
  - Emails du personnel dans `RoleAttribution` (attribution de rôle), `JournalEntree.auteurEmail` (auteur d'une action du journal d'audit) et `Commentaire.auteurEmail` (auteur d'un message du fil de commentaires d'un dossier).
  - Adresse IP dans les logs de rate limiting (voir ci-dessus).
- **Champs à risque (texte libre)** : `Projet.commentaire`, `Projet.motifRefus` et désormais `Commentaire.texte` (fil de commentaires, §3) sont des zones de texte libre — rien n'empêche techniquement un utilisateur d'y saisir un nom d'élève ou une autre donnée personnelle non prévue par le formulaire. Le fil de commentaires élargit ce risque (plusieurs messages, potentiellement plusieurs auteurs, sur la durée de vie du dossier) par rapport aux deux champs uniques d'origine. Pas de contrôle automatique possible sur du texte libre ; recommandation : sensibiliser les utilisateurs (ex. mention courte dans le libellé du champ/à proximité du fil) à ne pas y saisir de données nominatives d'élèves.
- **Droit à l'effacement** : déjà couvert — un Admin peut supprimer définitivement un dossier (`/projets/{id}/supprimer`), ce qui efface toutes les données personnelles associées, y compris son fil de commentaires (`ProjetService.supprimerDefinitivement` supprime explicitement les lignes `commentaires` correspondantes avant de supprimer le projet, la table n'ayant pas de suppression en cascade au niveau base). Le journal d'audit conserve une trace dénormalisée (nom du projet, jamais les données personnelles du dossier) après une suppression définitive, par design (traçabilité), ce qui est proportionné.
- **Pas de politique de rétention/purge automatique** à ce jour (aucune donnée n'est supprimée automatiquement après N années). Pas obligatoire en soi (le RGPD exige une durée de conservation *justifiée*, pas une suppression automatique), mais à formaliser si le volume de dossiers archivés devient important — piste déjà notée en §3 ("Archivage par année scolaire").
- **Pièces jointes Google Drive** (§3) : les fichiers déposés dans le dossier Drive du projet (devis, autorisations, RIB...) sont hors du périmètre technique de cette appli (gérés directement dans Google Drive) — la politique de conservation/accès de ces fichiers relève de la configuration du Drive partagé de l'établissement, pas du code.
- **Conclusion** : pas de non-conformité bloquante identifiée pour l'usage actuel (staff uniquement, pas de données d'élèves nominatives). Les points d'attention (texte libre, rétention des logs/journal) sont documentés ci-dessus plutôt que "corrigés dans le code", car ce sont des questions d'usage/politique plutôt que des bugs.

### 2.5 Identité visuelle
- [ ] Remplacer le violet M3 générique (#6750A4) par les couleurs officielles du collège, si elles existent (logo, charte graphique).

---

## 3. Pistes d'évolution fonctionnelle (post-lancement)

Idées à évaluer, aucune n'est engagée — à trier selon la valeur perçue.

- ✅ **Pièces jointes (MVP : lien Drive + création automatique du dossier)** — fait. Chaque projet a un champ `lienDrive` (URL du dossier Google Drive du voyage, migration `V5`), affiché comme bouton "Ouvrir le dossier Drive" sur la fiche projet (`formulaire.html` et `consultation.html`).
  - **Création automatique** (`GoogleDriveService`) : à la création d'un brouillon ou à la duplication, l'appli tente de créer automatiquement le dossier ("#{id} - {nom du projet}") dans un **Drive partagé** de l'établissement, via un **compte de service** membre de ce Drive partagé (pas de délégation domaine-wide : le Drive partagé est simplement partagé avec l'adresse email du compte de service, comme un collaborateur classique). Intégration **désactivée par défaut** (`GOOGLE_DRIVE_ENABLED=false`) : sans configuration, aucun appel Drive n'est tenté et le lien se saisit à la main. **Best-effort volontaire** : un échec (config absente, quota, réseau) ne bloque jamais l'enregistrement du projet, le champ reste alors vide et modifiable à la main.
  - Une fois le lien renseigné (auto ou manuel), modifiable indépendamment du statut du dossier (y compris pendant l'instruction ou une fois `VALIDE`) par l'organisateur du dossier ou par n'importe quel rôle de validation (Compta/Vie Scolaire/Direction/Admin) via `ProjetService.modifierLienDrive` — `ROLE_LECTURE_SEULE` voit le lien mais ne peut pas le modifier. URL restreinte à `drive.google.com`/`docs.google.com` (protection contre un lien arbitraire/`javascript:` stocké puis rendu cliquable).
  - Config requise pour activer (voir `.env.example`) : `GOOGLE_DRIVE_ENABLED=true`, `GOOGLE_DRIVE_SHARED_DRIVE_ID` (id du Drive partagé), `GOOGLE_DRIVE_CREDENTIALS_JSON_BASE64` (clé JSON du compte de service, encodée en base64). Compte de service à créer dans Google Cloud Console (API Drive activée), à ajouter comme membre ("Gestionnaire de contenu") du Drive partagé.
  - ⬜ **Intégration complète** (upload/liste/suppression de fichiers directement depuis l'appli) : toujours pas engagée. La création automatique de dossier ci-dessus ne nécessite qu'un scope `drive.file` restreint aux fichiers créés par l'appli ; une gestion complète des fichiers (lister/modifier des fichiers existants) serait un chantier plus lourd (très probablement une revue Google avant la Production, gestion des erreurs API plus étendue). --> **Décision : reporté après le lancement**, gardé tel quel (lien Drive + création auto du dossier) pour l'instant.
- ✅ **Export / impression** — fait. Bouton "Exporter en PDF" sur la fiche projet (`formulaire.html` et `consultation.html`), `GET /projets/{id}/export-pdf`, sans restriction de rôle supplémentaire (mêmes règles d'accès que la consultation du dossier, déjà ouverte à tout utilisateur authentifié). `PdfExportService` rend le template dédié `templates/pdf/fiche-projet-pdf.html` (récapitulatif du dossier + historique de validation : dates/auteurs/détail par étape, filtré depuis le journal d'audit sur les actions de workflow uniquement + fil de commentaires complet via `CommentaireService.lister`) via Thymeleaf, puis le convertit en PDF avec **openhtmltopdf** (HTML repassé par Jsoup pour obtenir un document XML bien formé, requis par openhtmltopdf). Nouvelle méthode `JournalEntreeRepository.findByProjetIdOrderByDateEvenementAsc`. Testé via `PdfExportServiceTest` (brouillon sans historique, dossier avec historique de validation, dossier avec fil de commentaires) et `ProjetControllerTest` (réponse `application/pdf` en pièce jointe).
- ~~**Historique d'audit complet**~~ --> **Décision : point retiré.** Le Journal d'audit déjà implémenté (§3 "Dashboard Admin") répond déjà à ce besoin (qui a fait quoi, quand, avec quel détail).
- ✅ **Archivage par année scolaire** — fait. `AnneeScolaireUtil.calculer` dérive l'année scolaire (ex. "2025-2026", de septembre à août inclus) depuis `dateDepart`, sans nouveau champ sur `Projet`. Sur `/admin/archives` : nouvelle action groupée (`POST /admin/archives/archiver-annee`, avec confirmation JS) archivant en une fois tous les dossiers `VALIDE` non déjà archivés d'une année scolaire choisie (`ProjetService.archiverDossiersValidesDeLAnneeScolaire`, une entrée de journal "Archivage" par dossier), et un filtre par année scolaire sur la liste des dossiers déjà archivés. Sur `/admin/recherche` (et son export CSV) : filtre par année scolaire ajouté aux critères existants. Archivage volontairement pas automatique (l'Admin déclenche). Testé via `AnneeScolaireUtilTest` (bornes septembre/août), `ProjetServiceTest` (sélectivité de l'archivage groupé par année) et `AdminControllerTest` (accès réservé à l'Admin).
- ✅ **Relances automatiques** — fait. `RelanceService` (tâche planifiée `@Scheduled`, tous les jours à 8h) envoie un email de rappel au rôle valideur en attente (Compta/Vie Scolaire/Direction selon l'étape) quand un dossier est bloqué depuis plus de `ficheprojet.relances.seuil-jours` (7 par défaut, configurable via `RELANCES_SEUIL_JOURS`), puis répète la relance toutes les `ficheprojet.relances.periode-jours` (7 par défaut, `RELANCES_PERIODE_JOURS`) tant que le dossier reste bloqué. S'appuie sur `ProjetService.listerDossiersBloques()` (même logique que `/admin/dossiers-bloques`) et sur le journal d'audit pour savoir si/quand une relance a déjà été envoyée durant le blocage courant (pas de nouveau champ sur `Projet` — dernière entrée `"Relance"` postérieure à la date d'entrée dans le statut). Respecte l'interrupteur admin de notifications (`/admin/notifications`). Chaque relance est tracée dans `/admin/journal` (auteur "Systeme (relance automatique)"). Testé via `RelanceServiceTest` (seuil, non-répétition avant la période, répétition après).
- ✅ **Fil de commentaires** — fait. Nouvelle table `commentaires` (migration `V6`, pas de clé étrangère vers `projets`, comme `journal_entrees` — suppression gérée explicitement par `ProjetService.supprimerDefinitivement`) : dossier, auteur, rôle capturé au moment du message, date de création, date de modification (si édité). Affiché en fil chronologique sur la fiche (`formulaire.html` et `consultation.html`, fragment `fragments/commentaires.html`), ajout possible à tout moment quel que soit le statut. Autorisation d'ajouter un commentaire = exactement le même périmètre que la gestion du lien Drive (organisateur du dossier ou n'importe quel rôle de validation, `ROLE_LECTURE_SEULE` exclu, réutilise `ProjetService.peutGererLienDrive`) ; modification/suppression réservées au seul auteur du commentaire, indépendamment de ses rôles actuels. Notification email (nouvel événement `CommentaireEvent` + listener dans `NotificationService`, même schéma async que les autres notifications) des participants déjà présents dans le fil (pas de notification sur le tout premier message, personne d'autre n'y a encore participé). Testé via `CommentaireServiceTest` (autorisations, propriété, nettoyage à la suppression du dossier) et `ProjetControllerTest`.
- ✅ **Rôle lecture seule** (ex. secrétariat) qui peut consulter tous les dossiers sans droit de validation — fait (`ROLE_LECTURE_SEULE`, liste d'emails `ROLES_LECTURE_SEULE`, vide par défaut). Un tel utilisateur voit toujours la vue de consultation (jamais le formulaire éditable), quel que soit le statut du dossier, avec le motif de refus affiché si `A_CORRIGER`.
- ✅ **Statistiques consolidées** — fait. Nouvelle page `/admin/statistiques` (`StatistiquesService`, réservée à `ROLE_ADMIN`), calculée uniquement sur les dossiers actifs (hors archivés) :
  - Budget total engagé (coût global des dossiers `VALIDE`) réparti par année scolaire et par classe (`AnneeScolaireUtil`, `Projet.classesConcernees` tel quel, pas de découpage des dossiers multi-classes).
  - Taux de refus par étape (Comptabilité/Vie Scolaire/Direction) : nb de refus / (nb de validations + nb de refus) à partir du journal d'audit, restreint aux dossiers encore actifs. Nécessite de qualifier l'action par étape dans le journal — `ProjetService.refuser` enregistre désormais `"Refus (Comptabilité)"` etc. au lieu d'un simple `"Refus"` (adapté en conséquence dans `PdfExportService`, qui filtrait sur l'ancien libellé exact).
  - Délai moyen de traitement par étape, calculé directement à partir des dates de validation déjà présentes sur `Projet` (pas besoin du journal), sur tous les dossiers actifs ayant déjà franchi l'étape (pas seulement les dossiers `VALIDE`).
  - Lien "Statistiques" ajouté au menu déroulant Administration. Testé via `StatistiquesServiceTest` (regroupements, exclusion des dossiers archivés, cohérence des taux/délais) et `AdminControllerTest` (accès réservé à l'Admin).
- ✅ **Filtres avancés sur le dashboard** — fait. Le filtre client existant (nom) est complété par 3 champs (classe, organisateur, période de départ "du/au"), ouverts à tous les rôles, entièrement côté client (`static/js/dashboard.js`, pas d'aller-retour serveur) : chaque carte porte désormais des attributs `data-classe`/`data-organisateur`/`data-date-depart` en plus de `data-nom`, tous les critères renseignés se combinent (ET logique). Bouton "Réinitialiser" pour vider les filtres. `/admin/recherche` (export CSV inclus) n'est pas modifié, il reste l'outil de recherche Admin. Testé via `ProjetControllerTest` (présence des champs de filtre et des attributs `data-*` attendus sur une carte).
- **Dashboard Admin** : Créer un dashboard Admin pour pouvoir modifier des paramètres directement dans l'application (modification des rôles, ajout/retrait d'adresse mail, suppression de projet, archivage des années précédentes et autres fonctions utiles à discuter avec le client).
  - ✅ **Gestion des rôles** (`/admin/roles`) : un Admin ajoute/retire des attributions de rôle par email, sans redémarrage. Stocké en base (table `role_attributions`, migration `V2`), en complément des listes `.env` (jamais en remplacement — `ROLES_ADMIN` reste le filet de sécurité contre un verrouillage total). Page réservée à `ROLE_ADMIN` y compris côté contrôleur (contrairement au reste de l'appli, en lecture ouverte à tous).
  - ✅ **Suppression / archivage de projet** : bouton "..." (admin, sur chaque carte du dashboard) → modale "Archiver" (réversible, retire du tableau de bord) ou "Supprimer définitivement" (irréversible, avec confirmation JS). Page `/admin/archives` pour retrouver et désarchiver/supprimer les dossiers archivés. Nouveau champ `Projet.archive` (migration `V3`), indépendant du statut de workflow.
  - ✅ **Journal d'audit** (`/admin/journal`, table `journal_entrees`, migration `V4`) : trace création, soumission, chaque validation, refus (avec motif), archivage/désarchivage, suppression définitive, modification admin d'un dossier validé, réaffectation d'organisateur, ajout/retrait de rôle — avec auteur, date, dossier concerné (nom dénormalisé, survit à une suppression définitive) et détail. Les 200 événements les plus récents.
  - ✅ **Dossiers bloqués** (`/admin/dossiers-bloques`) : dossiers en attente de validation triés du plus ancien au plus récent, avec le nombre de jours d'attente (calculé à partir des dates de validation déjà existantes, pas de nouveau champ) — pour relancer manuellement en attendant d'éventuelles relances automatiques.
  - ✅ **Réaffectation d'organisateur** (Admin, sur la fiche projet) : change l'organisateur d'un dossier sans toucher au statut ni aux validations déjà obtenues.
  - ✅ **Modification d'un dossier `VALIDE`** : un Admin peut désormais rouvrir et corriger un dossier déjà validé (les autres rôles restent bloqués) ; tracé dans le journal d'audit.
  - ✅ **Recherche admin avancée** (`/admin/recherche`) : par nom, organisateur, classe, statut, archivage (tous statuts et archivés confondus, contrairement au dashboard). **Export CSV** (`/admin/recherche/export.csv`) des résultats filtrés.
  - ✅ **Email de test SMTP + interrupteur temporaire des notifications** (`/admin/notifications`) : envoi d'un email de test immédiat (échec remonté à l'écran, contrairement au flux normal qui l'avale), et un interrupteur volontairement non persisté (redémarrage = notifications réactivées).
  - ✅ **Tableau "santé"** (`/admin/sante`) : nombre de dossiers en base, version déployée (MANIFEST du jar), date de la dernière sauvegarde (lue dans `./backups/last/`, monté en lecture seule dans le conteneur `app`). Pas de bouton "sauvegarder maintenant" dans l'appli (délibéré, voir `docs/SAUVEGARDE.md`) — exécuter un processus système ou donner accès au conteneur `db-backup` depuis le serveur web est une surface d'attaque évitable ; commande documentée à lancer soi-même à la place.
  - ⬜ Autres fonctions à discuter avec le client. --> **Décision : laissé ouvert pour l'instant**, à reprendre après le lancement selon les retours terrain.

## 4. Pistes UX/UI

- **Couleurs officielles du collège** (déjà cité en §2.5, mais c'est autant un sujet UI que technique).
- ✅ **Stepper visuel du workflow** sur la fiche projet (1-2-3-4 : Soumission, Comptabilité, Vie Scolaire, Direction), en complément du badge de statut — fait. Affiché sur `formulaire.html` et `consultation.html` (fragment `fragments/stepper.html`). Étape en cours mise en évidence (cercle plein, libellé en couleur primaire) ; étapes déjà validées cochées en vert. Pour un dossier `A_CORRIGER`, l'étape qui a refusé est affichée en rouge (icône alerte) plutôt qu'"en cours" — calculée dans `ProjetController` à partir des dates de validation déjà acquises, avec la même logique que `ProjetService.determinerEtapeDeReprise` (le refus met en évidence l'étape par laquelle la resoumission repassera).
- ✅ **Écran récapitulatif avant soumission** — fait. Le bouton "Soumettre pour validation" (`formulaire.html`) poste desormais le formulaire complet vers `/projets/{id}/preparer-soumission` (via `formaction`, hors du `<form>` principal) : enregistre les modifications en cours (memes regles que "Enregistrer"), puis redirige vers `/projets/{id}/recapitulatif` (nouveau template `recapitulatif.html`, cartes en lecture seule identiques a `consultation.html`) plutot que de soumettre directement. La relecture porte ainsi exactement sur ce qui vient d'etre sauvegarde, meme en cas de frappe non enregistree juste avant. Deux actions : "Modifier le dossier" (retour a la fiche) ou "Confirmer et soumettre" (POST `/projets/{id}/soumettre`, inchange). Si le dossier n'est plus `BROUILLON`/`A_CORRIGER` (deja engage dans le circuit), le recapitulatif redirige simplement vers la fiche.
- ✅ **Responsive mobile** — fait.
  - **Bug corrigé** : la navbar utilisait `navbar-expand-lg` sans bouton hamburger ni `<div class="collapse">` (voir `fragments/navbar.html`) — sous 992px, elle n'avait donc aucun moyen de se replier et débordait. Ajout du couple bouton `.navbar-toggler` + `.collapse.navbar-collapse` standard Bootstrap. Testé (`laNavbarEstRepliableSurMobile`).
  - **Kanban** : défilement horizontal déjà fonctionnel (`overflow-auto`), complété par un `scroll-snap` (une colonne s'accroche à l'écran plutôt que de s'arrêter n'importe où) et, sous 576px, une largeur de colonne en `vw` (une colonne visible + aperçu de la suivante) plutôt que la largeur fixe pensée pour desktop. Marges latérales réduites et barre de recherche en pleine largeur sur très petit écran.
  - **Non testé dans un vrai navigateur** (pas d'outil de navigateur dans mon environnement) : le rendu visuel réel sur mobile (Chrome DevTools ou téléphone) reste à valider par toi.
- ✅ **Feedback visuel** sur les boutons de validation/refus/soumission — fait. `static/js/boutons-validation.js` : un seul écouteur délégué sur l'événement `submit` (via `event.submitter`, identifie le bouton réellement à l'origine même pour un bouton associé par l'attribut `form="..."` comme "Soumettre pour validation"), désactive le bouton et affiche un spinner Bootstrap + "Traitement..." dès que le formulaire est réellement soumis (jamais avant une validation HTML5 bloquante, ex. champ requis vide). Classe marqueur `js-bouton-validation` posée sur : Soumettre, Valider Budget/Vie Scolaire/Direction, Confirmer le refus (dashboard et formulaire), Confirmer et soumettre (récapitulatif). Volontairement pas étendu à "Enregistrer"/"Dupliquer"/"Archiver"/"Supprimer" (hors du périmètre demandé).
- **Aide contextuelle** pour les nouveaux professeurs à la première connexion (tooltip ou courte visite guidée expliquant le workflow).

## 4bis. Audit UX — parcours utilisateur pour un public non technique (juillet 2026)

Audit réalisé après le lancement de la v1, à la demande du porteur du projet : rendre l'app facile à
prendre en main par tout le personnel de l'établissement (pas seulement les profils à l'aise avec le
numérique), sans remettre en cause le stepper visuel ni le responsive mobile déjà livrés (§4). Public
concerné : profs organisateurs (usage occasionnel, 1-2 fois/an), Comptabilité/Vie Scolaire/Direction
(usage récurrent mais rapide), Admin (usage approfondi), secrétariat en lecture seule — personne n'a de
formation dédiée à l'outil. Idées à évaluer, aucune n'est engagée.

**Étapes du parcours jugées les plus à risque a priori** : le formulaire de création (premier contact
avec l'outil), la lecture du dashboard Kanban (retrouver "mes" dossiers parmi ceux de tout
l'établissement), la correction d'un dossier `A_CORRIGER` (bien re-soumettre après correction), et tout
cas d'erreur/autorisation refusée en dehors du flux normal.

**Points de friction identifiés (lecture du code, pas encore vérifié en navigateur réel)** :
- `formulaire.html` : aucun marquage des champs obligatoires (pas de `*`, pas de légende), sur un
  formulaire d'une seule longue page (~25 champs, 6 cartes empilées) sans sommaire ni progression.
  Erreurs de validation (`th:errors`) affichées à côté de chaque champ mais aucun résumé en haut de
  page ni saut automatique vers la première erreur — sur une page aussi longue, un échec de
  soumission peut passer inaperçu.
- `dashboard.html` / `ProjetController.tableauDeBord` : le Kanban montre tous les projets de tous les
  organisateurs, sans filtre par utilisateur côté serveur ; un prof doit taper son propre nom dans le
  filtre "Organisateur" pour isoler ses dossiers, ce qui n'est pas découvrable sans qu'on le lui montre.
- Dossier `A_CORRIGER` : le motif de refus est bien mis en évidence en haut de la fiche (bon point),
  mais rien ne rappelle, au niveau du bouton d'action en bas de page, qu'il faut re-soumettre après
  correction (pas seulement "Enregistrer").
- Gestion des erreurs : `GlobalExceptionHandler` traduit déjà bien les erreurs métier connues
  (dossier introuvable, conflit de version, transition invalide) en messages français clairs et
  redirige proprement. En revanche, aucun `AccessDeniedHandler` ni page 403/404/500 personnalisée
  n'est configuré dans `SecurityConfig` : un utilisateur tombant sur un lien expiré ou une action non
  autorisée atterrit sur la page d'erreur Spring Boot par défaut (technique, en anglais).
- Couleurs officielles du collège toujours pas appliquées (violet M3 générique, déjà cité en §2.5/§4).

**Pistes d'amélioration proposées (aucune codée à ce stade)** :
- ⬜ **Aide contextuelle / onboarding** à la première connexion d'un Prof (tooltip ou courte visite
  guidée expliquant les 4 étapes du workflow) — reprend la piste déjà notée en §4.
- ⬜ **Clarté du formulaire** : légende "champs obligatoires" + astérisques, résumé d'erreurs en haut
  de page avec ancre vers le premier champ en erreur après un "Enregistrer" échoué.
- ⬜ **Vue "Mes dossiers" par défaut pour les profs** sur le dashboard (filtre pré-rempli avec leur
  propre nom, ou onglet séparé du Kanban global), plutôt que de partir du Kanban complet de
  l'établissement.
- ⬜ **Pages d'erreur personnalisées** (403/404/500) en français, ton rassurant, avec lien de retour
  au tableau de bord et contact en cas de blocage.
- ⬜ **Rappel de re-soumission** sur un dossier `A_CORRIGER`, visible au niveau de l'action elle-même
  (pas seulement en haut de page), pour éviter qu'un dossier corrigé reste bloqué en silence faute de
  re-soumission.
- ⬜ **Couleurs officielles du collège** — reprend la piste déjà notée en §2.5/§4, dépend toujours de
  la charte graphique à confirmer avec le client (§6).

Captures d'écran demandées pour affiner cet audit avant priorisation : formulaire de création vierge
(desktop + mobile), formulaire après un "Enregistrer" en échec, dashboard vu par un compte Prof, fiche
`A_CORRIGER`, rendu réel d'un email de notification dans Gmail/Outlook, et un cas d'erreur 403/404
rencontré si possible.

## 5. Priorisation proposée (à valider)

| Phase | Contenu | Objectif |
|---|---|---|
| **1 — Bloquant avant mise en prod** | HTTPS, Flyway, config réelle (domaine/rôles/OAuth prod), sauvegardes PostgreSQL testées, revue de sécurité, test SMTP réel | Rendre le déploiement actuel fiable et sûr |
| **2 — Confort** | CI ✅, couleurs officielles (toujours ouvert, §2.5/§6), emails HTML ✅, stepper visuel du workflow ✅ | Finitions avant l'ouverture aux utilisateurs réels |
| **3 — Itératif avant le lancement** | Pièces jointes ✅ (MVP lien Drive), export PDF ✅, relances automatiques ✅, archivage par année scolaire ✅, fil de commentaires ✅, statistiques consolidées ✅, filtres avancés dashboard ✅ — tous livrés (§3). Restent : intégration Drive complète (reportée après lancement) et autres fonctions dashboard admin (ouvert, retours terrain) | Amélioration continue selon les retours terrain |

## 6. Questions ouvertes (besoin de ta décision)

1. Le collège a-t-il des couleurs officielles/une charte graphique à utiliser à la place du violet M3 générique ? --> Oui à  confirmer avec le client pour les couleurs exactes.
2. Pièces jointes : si on les ajoute un jour, préférence de stockage — disque du serveur, bucket compatible S3, ou Google Drive via API (vu l'écosystème Google déjà en place) ? --> Google Drive. Fait : lien par projet + création automatique du dossier (compte de service + Drive partagé, le collège étant confirmé sur Google Workspace avec accès admin). Une intégration complète (upload/liste depuis l'appli) reste possible plus tard si besoin.
3. Volumétrie attendue (nombre de voyages/an) — utile pour dimensionner archivage et pagination du dashboard. --> une cinquantaine par an
4. Un rôle "lecture seule" (secrétariat, autre) est-il pertinent à moyen terme ? --> Oui
5. Le statut de l'établissement impose-t-il des obligations d'accessibilité (RGAA) à respecter formellement ? --> Non, faire au mieux

---