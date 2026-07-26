# =========================================================================
# Etape 1 - Build : compile l'application avec Maven dans un conteneur jetable
# =========================================================================
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build

# Copie du wrapper Maven en premier pour profiter du cache Docker sur les
# dependances tant que le pom.xml ne change pas.
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# =========================================================================
# Etape 2 - Runtime : image finale legere, sans outils de build
# =========================================================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# curl : necessaire uniquement pour le HEALTHCHECK ci-dessous (interroge
# /actuator/health depuis l'interieur du conteneur), absent de l'image JRE
# minimale par defaut.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Utilisateur non-root pour l'execution du conteneur (bonne pratique securite)
RUN groupadd --system ficheprojet && useradd --system --gid ficheprojet ficheprojet
COPY --from=build /build/target/fiche-projet.jar app.jar
RUN chown ficheprojet:ficheprojet app.jar
USER ficheprojet

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
