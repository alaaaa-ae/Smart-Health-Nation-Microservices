# API Gateway — Smart Health Nation

Point d'entrée unique de l'architecture microservices.

## Convention de Routage
Chaque groupe ajoute ses règles de routage ici lors de son intégration :
* `/api/v1/ins/**` $ightarrow$ `service-identite-ins` (G1 - Port `8001`)
* `/api/v1/fse/**` $ightarrow$ `service-facturation-amo` (G1 - Port `8002`)
* `/api/v1/fraud/**` $ightarrow$ `service-ia-fraude` (G1 - Port `8003`)
