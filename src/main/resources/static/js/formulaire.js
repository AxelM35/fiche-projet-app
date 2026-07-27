// Ajout/retrait dynamique des lignes "Accompagnateurs" du formulaire projet.
// Delegation d'evenements sur le conteneur (plutot que onclick="..." sur
// chaque bouton, incompatible avec la CSP script-src sans 'unsafe-inline') :
// couvre aussi bien les lignes presentes au chargement que celles ajoutees
// ensuite, sans avoir a re-attacher un listener a chaque ajout.
(function () {
    const conteneur = document.getElementById('listeAccompagnateurs');
    const boutonAjouter = document.getElementById('boutonAjouterAccompagnateur');

    if (!conteneur) {
        return;
    }

    function creerLigneAccompagnateur() {
        const ligne = document.createElement('div');
        ligne.className = 'input-group mb-2';

        const champ = document.createElement('input');
        champ.type = 'text';
        champ.className = 'form-control';
        champ.name = 'accompagnateurs';
        champ.placeholder = "Nom de l'accompagnateur";

        const boutonSupprimer = document.createElement('button');
        boutonSupprimer.type = 'button';
        boutonSupprimer.className = 'btn btn-outline-danger';
        boutonSupprimer.innerHTML = '&times;';

        ligne.appendChild(champ);
        ligne.appendChild(boutonSupprimer);
        return ligne;
    }

    if (boutonAjouter) {
        boutonAjouter.addEventListener('click', function () {
            conteneur.appendChild(creerLigneAccompagnateur());
        });
    }

    conteneur.addEventListener('click', function (evenement) {
        const boutonSupprimer = evenement.target.closest('.btn-outline-danger');
        if (boutonSupprimer) {
            boutonSupprimer.closest('.input-group').remove();
        }
    });
})();

// "Je ne connais pas encore le budget" (voir formulaire.html) : desactive
// les 3 champs concernes tant que la case est cochee, pour qu'ils ne soient
// pas soumis (un champ desactive est exclu de la requete par le navigateur,
// coherent avec ProjetController.validerCoherenceBudget qui les rend
// facultatifs uniquement dans ce cas) et eviter une saisie partielle
// incoherente. Applique l'etat initial au chargement (dossier deja
// enregistre avec la case cochee), puis a chaque changement.
(function () {
    const caseBudgetInconnu = document.getElementById('budgetInconnu');
    if (!caseBudgetInconnu) {
        return;
    }

    const champsBudget = ['coutGlobal', 'coutParEleve', 'montantSubvention']
        .map(function (id) { return document.getElementById(id); })
        .filter(Boolean);

    function appliquerEtatChampsBudget() {
        champsBudget.forEach(function (champ) {
            champ.disabled = caseBudgetInconnu.checked;
        });
    }

    caseBudgetInconnu.addEventListener('change', appliquerEtatChampsBudget);
    appliquerEtatChampsBudget();
})();

// Amene le focus sur le premier champ en erreur au chargement (apres un
// "Enregistrer" echoue) : sur un formulaire aussi long, le premier champ
// invalide pouvait rester hors ecran sans que rien ne le signale, meme avec
// le resume d'erreurs en haut de page (voir formulaire.html).
(function () {
    const premierChampInvalide = document.querySelector('#formProjet .is-invalid');
    if (premierChampInvalide) {
        premierChampInvalide.scrollIntoView({behavior: 'smooth', block: 'center'});
        premierChampInvalide.focus({preventScroll: true});
    }
})();
