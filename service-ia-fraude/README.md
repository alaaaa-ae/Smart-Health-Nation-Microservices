# service-ia-fraude (Groupe 1)

## Rôle
Microservice intelligent d'audit médico-financier des remboursements AMO (CNSS / DMFAR / CNOPS) :
* Vérifie la cohérence des actes facturés (codage NGAP, montants TNR, doublons de dates/praticiens).
* Croise si disponible avec le DPI clinique (HL7 FHIR) pour confirmer la réalité de l'acte.
* Applique des modèles de détection d'anomalies (**Isolation Forest** / **Graph Neural Networks**) et génère un score explicable pour la supervision humaine.

## Endpoints Exposés
* `GET /health` : État du service
* `POST /api/v1/fraud/analyze` : Évalue le risque de fraude d'une FSE (voir `doc/payloads/G1-ia-fraude.json`).
