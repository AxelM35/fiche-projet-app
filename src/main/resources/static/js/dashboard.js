// Comportements du tableau de bord : modales reutilisees (refus, gestion
// admin d'un dossier) et filtrage cote client. Externalise depuis un
// <script> inline pour respecter la CSP script-src (pas de 'unsafe-inline').
document.getElementById('modalRefus').addEventListener('show.bs.modal', function (evenement) {
    const bouton = evenement.relatedTarget;
    const projetId = bouton.getAttribute('data-projet-id');
    document.getElementById('formRefus').action = '/projets/' + projetId + '/refuser';
});

document.getElementById('modalGererProjet').addEventListener('show.bs.modal', function (evenement) {
    const bouton = evenement.relatedTarget;
    const projetId = bouton.getAttribute('data-projet-id');
    document.getElementById('gererProjetNom').textContent = bouton.getAttribute('data-projet-nom');
    document.getElementById('formArchiverProjet').action = '/projets/' + projetId + '/archiver';
    document.getElementById('formSupprimerProjet').action = '/projets/' + projetId + '/supprimer';
});

// Filtrage cote client, par nom de projet, colonne par colonne (sans
// aller-retour serveur : le tableau est deja entierement charge).
document.getElementById('rechercheProjet').addEventListener('input', function (evenement) {
    const recherche = evenement.target.value.trim().toLowerCase();

    document.querySelectorAll('.kanban-column').forEach(function (colonne) {
        let visibles = 0;
        colonne.querySelectorAll('.project-card').forEach(function (carte) {
            const correspond = carte.getAttribute('data-nom').includes(recherche);
            carte.classList.toggle('d-none', !correspond);
            if (correspond) {
                visibles += 1;
            }
        });

        const compteur = colonne.querySelector('.compteur-colonne');
        if (compteur) {
            compteur.textContent = visibles;
        }
        const messageVide = colonne.querySelector('.message-vide');
        if (messageVide) {
            messageVide.classList.toggle('d-none', visibles > 0);
        }
    });
});
