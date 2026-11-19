# service-facturation-amo (Groupe 1)

## Rôle
Gère le cycle de vie de la **Feuille de Soins Électronique (FSE)** selon les règles officielles CNSS / CNOPS :
* Vérification du délai légal de 60 jours et des droits ouverts (via `service-identite-ins`).
* Codage des actes (**NGAP**, **INPE** praticien) et application du **Tarif National de Référence (TNR)**.
* Calcul automatique de la part AMO (70 % soins courants, 90 % hospitalisation, 100 % ALD) et du reste à charge.
* Transmission de la FSE calculée à `service-ia-fraude` avant liquidation.

## Endpoints Exposés
* `GET /health` : État du service
* `POST /api/v1/fse/liquidate` : Liquide une FSE et déclenche le contrôle IA (voir `doc/payloads/G1-facturation-amo.json`).
