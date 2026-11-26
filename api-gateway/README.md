# API Gateway — Smart Health Nation

Point d'entrée unique de l'architecture microservices. Les règles ci-dessous définissent les routes publiques et leur service propriétaire, avec les cibles d'hébergement Proxmox.

## Convention de routage

G1 assure le routage intergroupes et l’authentification centralisée. La configuration IAM a son propre [dossier Keycloak](keycloak/README.md).

| Route API Gateway | Service | Groupe | IP Proxmox | Port |
| :--- | :--- | :--- | :--- | :--- |
| `/api/v1/ins/**` | `ms-identite-ins` | G1 | `192.168.1.11` | `8001` |
| `/api/v1/fse/**` | `ms-facturation-amo` | G1 | `192.168.1.11` | `8002` |
| `/api/v1/fraude/**` | `ms-ia-fraude` | G1 | `192.168.1.11` | `8003` |
| `/api/v1/sentinel/**` | `ms-pharmacy-sentinel` | G2 | `192.168.1.12` | `8004` |
| `/api/v1/citizen/**` | `ms-citizen-multilingual` | G2 | `192.168.1.12` | `8005` |
| `/api/v1/entrepot/**` | `ms-entrepot-omop` | G3 | `192.168.1.13` | `8006` |
| `/api/v1/recherche/**` | `ms-recherche` | G3 | `192.168.1.13` | `8007` |
| `/api/v1/complications/**` | `ms-ia-complications` | G3 | `192.168.1.13` | `8008` |
| `/api/v1/ai-grc/**` | `ms-ai-grc` | G4 | `192.168.1.14` | `8009` |
| `/api/v1/certification/**` | `ms-certification-dm-ia` | G4 | `192.168.1.14` | `8010` |
| `/api/v1/macert/**` | `ms-macert-soc` | G4 | `192.168.1.14` | `8011` |
| `/fhir/v1/mdp/**` | `ms-mdp-clinique` | G4 | `192.168.1.14` | `8012` |
| `/fhir/v1/exchange/**` | `ms-health-data-exchange` | G5 | `192.168.1.15` | `8013` |
| `/api/v1/discharge/**` | `ms-hospital-discharge` | G5 | `192.168.1.15` | `8014` |
| `/api/v1/scribe/**` | `ms-medical-scribe` | G5 | `192.168.1.15` | `8015` |

Once a service's Compose block is enabled, local Docker upstreams can use its service DNS name and port (for example, `http://ms-identite-ins:8001`). On Proxmox, use the IP and port shown above. Each group should add its implementation and publish its OpenAPI/Swagger documentation at `/docs` in its service.
