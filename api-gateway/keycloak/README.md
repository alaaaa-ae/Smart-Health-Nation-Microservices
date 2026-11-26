# Authentification centralisée avec Keycloak

Ce dossier accueille la configuration IAM du groupe G1 pour la plateforme. Keycloak authentifie les acteurs habilités et émet leurs jetons d’accès. La passerelle vérifie les jetons et route les appels vers les services concernés.

Les acteurs prévus sont le patient, le médecin, le réceptionniste, le pharmacien, le comité de recherche et le chercheur approuvé.

## Accès des chercheurs

1. Le groupe Entrepôt reçoit la demande initiale et les pièces justificatives avant la création d’un compte G1.
2. Un membre habilité du comité de recherche se connecte via Keycloak pour instruire le dossier.
3. Après acceptation, G1 active l’accès du chercheur. L’Entrepôt limite les données consultables au périmètre approuvé par le comité.
