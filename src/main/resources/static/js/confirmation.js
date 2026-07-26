// Confirmation avant soumission d'un formulaire (ex. suppression definitive) :
// remplace l'attribut onsubmit="return confirm(...)" inline, incompatible
// avec une CSP script-src sans 'unsafe-inline'. Le message vient de
// l'attribut data-confirm plutot que d'etre code en dur ici, pour rester
// reutilisable par n'importe quel formulaire de l'application.
document.querySelectorAll('form[data-confirm]').forEach(function (formulaire) {
    formulaire.addEventListener('submit', function (evenement) {
        if (!window.confirm(formulaire.getAttribute('data-confirm'))) {
            evenement.preventDefault();
        }
    });
});
