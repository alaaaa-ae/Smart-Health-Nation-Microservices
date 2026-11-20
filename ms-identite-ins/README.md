# ms-identite-ins (Groupe 1)

## Rôle
Gère l'authentification (JWT), la résolution de l'Identité Nationale de Santé (**INS**) via le simulateur Tiers de Confiance **DGSN (CNIE)** et la vérification de l'ouverture des droits AMO (**CNSS / CNOPS / ALD**).

## Endpoints Exposés
* `GET /health` : État du service
* `GET /api/v1/ins/resolve?cnie={num}` : Résout l'INS et retourne les droits AMO de l'assuré (voir `doc/payloads/g1-ms-identite-ins.json`).
