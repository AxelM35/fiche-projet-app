-- Archivage admin des projets (independant du statut de workflow).
ALTER TABLE projets ADD COLUMN archive BOOLEAN NOT NULL DEFAULT FALSE;
