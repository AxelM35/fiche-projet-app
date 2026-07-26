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

// Filtrage cote client (nom, classe, organisateur, periode de depart),
// colonne par colonne, sans aller-retour serveur : le tableau est deja
// entierement charge. Tous les criteres renseignes doivent correspondre
// (ET logique) ; un critere vide ne filtre rien.
function appliquerFiltresDashboard() {
    const recherche = document.getElementById('rechercheProjet').value.trim().toLowerCase();
    const classe = document.getElementById('filtreClasse').value.trim().toLowerCase();
    const organisateur = document.getElementById('filtreOrganisateur').value.trim().toLowerCase();
    const dateDebut = document.getElementById('filtreDateDepartDebut').value;
    const dateFin = document.getElementById('filtreDateDepartFin').value;

    document.querySelectorAll('.kanban-column').forEach(function (colonne) {
        let visibles = 0;
        colonne.querySelectorAll('.project-card').forEach(function (carte) {
            const dateDepart = carte.getAttribute('data-date-depart');
            const correspond = carte.getAttribute('data-nom').includes(recherche)
                && carte.getAttribute('data-classe').includes(classe)
                && carte.getAttribute('data-organisateur').includes(organisateur)
                && (!dateDebut || (dateDepart && dateDepart >= dateDebut))
                && (!dateFin || (dateDepart && dateDepart <= dateFin));
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
}

['rechercheProjet', 'filtreClasse', 'filtreOrganisateur'].forEach(function (id) {
    document.getElementById(id).addEventListener('input', appliquerFiltresDashboard);
});
['filtreDateDepartDebut', 'filtreDateDepartFin'].forEach(function (id) {
    document.getElementById(id).addEventListener('change', appliquerFiltresDashboard);
});
document.getElementById('filtresReinitialiser').addEventListener('click', function () {
    document.getElementById('rechercheProjet').value = '';
    document.getElementById('filtreClasse').value = '';
    document.getElementById('filtreOrganisateur').value = '';
    document.getElementById('filtreDateDepartDebut').value = '';
    document.getElementById('filtreDateDepartFin').value = '';
    appliquerFiltresDashboard();
});
