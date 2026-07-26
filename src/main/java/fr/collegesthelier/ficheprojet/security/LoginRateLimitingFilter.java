package fr.collegesthelier.ficheprojet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Limite le nombre de requetes par adresse IP sur les routes d'authentification
 * (page de connexion, redirection vers Google, retour de Google) : ralentit un
 * script tentant d'abuser du flux OAuth2 (DoS applicatif, tentatives repetees
 * avec des comptes Google differents pour sonder quelles adresses sont
 * autorisees sur le domaine). Fenetre fixe simple (pas de bibliotheque
 * dediee : la regle est volontairement basique) reinitialisee par IP toutes
 * les LARGEUR_FENETRE.
 * <p>
 * Limite connue : request.getRemoteAddr() suppose qu'aucun reverse proxy ne
 * s'intercale entre le client et l'application. Le jour ou un reverse proxy
 * HTTPS sera mis en place (voir docs/CAHIER_DES_CHARGES.md), il faudra soit
 * activer server.forward-headers-strategy=native avec une liste de proxies
 * de confiance, soit lire X-Forwarded-For explicitement - jamais le faire
 * sans liste de confiance, un en-tete client est sinon trivialement
 * falsifiable et rendrait cette limite inoperante.
 * <p>
 * Volontairement pas un bean Spring (@Component/@Bean) : instancie
 * directement dans SecurityConfig et ajoute au filter chain via
 * addFilterBefore. Un filtre expose comme bean serait sinon *en plus*
 * enregistre par Spring Boot comme filtre servlet generique (applique a
 * toutes les routes), executant la logique deux fois par requete.
 */
@Slf4j
public class LoginRateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> CHEMINS_LIMITES = Set.of(
            "/login", "/oauth2/authorization/google", "/login/oauth2/code/google");

    private static final int LIMITE_REQUETES = 20;
    private static final Duration LARGEUR_FENETRE = Duration.ofMinutes(1);

    /** Purge des compteurs inactifs pour ne pas laisser grossir la map indefiniment sur un serveur de longue duree. */
    private static final Duration DUREE_INACTIVITE_AVANT_PURGE = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Compteur> compteursParIp = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // getRequestURI() plutot que getServletPath() : ce dernier depend de la
        // configuration de mapping du DispatcherServlet (vide dans certains
        // contextes, notamment MockMvc en test) et n'est pas fiable ici.
        return !CHEMINS_LIMITES.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Compteur compteur = compteursParIp.computeIfAbsent(ip, cle -> new Compteur());

        if (compteur.depasseLaLimite()) {
            log.warn("Rate limiting : trop de requetes d'authentification depuis {} sur {}", ip, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Trop de tentatives de connexion. Merci de reessayer dans une minute.");
            return;
        }

        purgerCompteursInactifsOccasionnellement();
        filterChain.doFilter(request, response);
    }

    private void purgerCompteursInactifsOccasionnellement() {
        // Purge opportuniste (pas de tache planifiee dediee) : declenchee au fil
        // de l'eau, suffisant vu le trafic attendu sur ces routes precises.
        if (compteursParIp.size() < 1000) {
            return;
        }
        Instant limite = Instant.now().minus(DUREE_INACTIVITE_AVANT_PURGE);
        compteursParIp.entrySet().removeIf(entree -> entree.getValue().debutFenetre.get().isBefore(limite));
    }

    private static final class Compteur {
        private final AtomicInteger nombreRequetes = new AtomicInteger(0);
        private final AtomicReference<Instant> debutFenetre = new AtomicReference<>(Instant.now());

        synchronized boolean depasseLaLimite() {
            if (Duration.between(debutFenetre.get(), Instant.now()).compareTo(LARGEUR_FENETRE) >= 0) {
                debutFenetre.set(Instant.now());
                nombreRequetes.set(0);
            }
            return nombreRequetes.incrementAndGet() > LIMITE_REQUETES;
        }
    }
}
