-- Lien vers le dossier Google Drive des pieces jointes du projet (MVP :
-- simple URL geree/partagee en dehors de l'application, pas d'integration
-- API Drive).
ALTER TABLE projets ADD COLUMN lien_drive VARCHAR(500);
