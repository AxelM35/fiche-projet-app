// Feedback visuel pendant la requete sur les boutons de validation/refus/
// soumission (marques par la classe js-bouton-validation) : desactive le
// bouton et affiche un spinner des que le formulaire est reellement soumis.
// Un seul ecouteur delegue sur document, valable sur toutes les pages qui
// chargent ce script (dashboard, formulaire, recapitulatif) : couvre aussi
// bien un bouton a l'interieur de son <form> qu'un bouton associe via
// l'attribut form="..." (formulaire.html), grace a event.submitter qui
// identifie le bouton reellement a l'origine de la soumission.
//
// Ne se declenche qu'apres la validation native du navigateur (l'evenement
// submit ne part pas si un champ required est vide, par exemple) : jamais
// de spinner affiche pour une soumission qui n'a pas reellement lieu.
document.addEventListener('submit', function (evenement) {
    const bouton = evenement.submitter;
    if (!bouton || !bouton.classList.contains('js-bouton-validation')) {
        return;
    }

    bouton.disabled = true;
    bouton.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Traitement...';
});
