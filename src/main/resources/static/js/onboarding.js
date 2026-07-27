// Aide contextuelle (onboarding) : affiche automatiquement le guide du
// workflow a un Prof lors de sa toute premiere visite du dashboard, marque
// "vue" en localStorage (cote navigateur, jamais un aller-retour serveur)
// pour ne plus jamais la reafficher automatiquement ensuite. Le bouton
// "Comment ca marche ?" reste disponible pour la rouvrir manuellement a tout
// moment (ouverture geree nativement par Bootstrap via data-bs-toggle, ce
// script ne gere que l'ouverture automatique et la memorisation).
const CLE_ONBOARDING_VU = 'fiche-projet-onboarding-vu';

// L'element n'existe dans le DOM que pour un Prof (sec:authorize dans
// dashboard.html) : ce script est charge sur chaque visite du dashboard,
// quel que soit le role connecte.
const modalOnboardingElement = document.getElementById('modalOnboarding');
if (modalOnboardingElement) {
    modalOnboardingElement.addEventListener('hidden.bs.modal', function () {
        localStorage.setItem(CLE_ONBOARDING_VU, 'true');
    });

    if (!localStorage.getItem(CLE_ONBOARDING_VU)) {
        new bootstrap.Modal(modalOnboardingElement).show();
    }
}
