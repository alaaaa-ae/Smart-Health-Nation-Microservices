# Smart-Health-Nation-Microservices

## Aperçu
**Smart-Health-Nation-Microservices** est une plateforme distribuée basée sur des microservices conçue pour l'urbanisation et l'implémentation du Système d'Information de Santé marocain (*Maroc Smart Health Nation 2030*). L'architecture repose sur des services indépendants communiquant via une passerelle API (`api-gateway`) et déployés sur des environnements **Proxmox VE (Salle TP4)**.

---

## Convention de Nommage
Afin de garantir la cohérence entre l'arborescence Git, le DNS interne Docker/Proxmox et les routes de l'API Gateway :
- **Dossiers Git & Services Docker :** `ms-<domaine-metier>` en minuscules avec tirets (`kebab-case`, sans accents).
- **Contrats d'interface (`doc/payloads/`) :** `g<numero>-ms-<nom-du-service>.json` (ex. `g1-ms-identite-ins.json`).
- **Branches Git :** `feat/g<numero>-ms-<domaine-metier>` (ex. `feat/g4-ms-mdp-clinique`).

---

## Architecture & Répartition par Groupe

> **Consigne aux équipes :** Dès que votre groupe *fork* ce dépôt, complétez les dossiers `ms-<nom>/` qui lui sont attribués et ouvrez une **Pull Request**. Mettez à jour vos IP Proxmox réelles si nécessaire.

| Service / Dossier | Groupe | Rôle & Domaine Fonctionnel | IP Proxmox | Port | Route API Gateway |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`api-gateway`** | Transversal | Point d'entrée central : routage, validation JWT et équilibrage de charge | `192.168.1.10` | `8080` | `/*` |
| **`ms-identite-ins`** | **G1** | Résolution de l'Identité Nationale de Santé (INS via CNIE/DGSN), droits AMO/ALD (CNSS/CNOPS) et JWT | `192.168.1.11` | `8001` | `/api/v1/ins/**` |
| **`ms-facturation-amo`** | **G1** | Génération et liquidation de la Feuille de Soins Électronique (FSE), codage NGAP, INPE et calcul TNR | `192.168.1.11` | `8002` | `/api/v1/fse/**` |
| **`ms-ia-fraude`** | **G1** | Audit médico-financier temps réel des FSE avant paiement et détection d'anomalies (Isolation Forest + GNN) | `192.168.1.11` | `8003` | `/api/v1/fraude/**` |
| **`ms-pharmacy-sentinel`** | **G2** | Collecte quotidienne des ventes d'officines par province/classe de produit et détection d'alertes sanitaires avec validation humaine | `192.168.1.12` | `8004` | `/api/v1/sentinel/**` |
| **`ms-citizen-multilingual`** | **G2** | Diffusion d'alertes territoriales validées et interfaces citoyennes multilingues (Arabe, Darija, Amazigh, Français) avec gestion du consentement | `192.168.1.12` | `8005` | `/api/v1/citizen/**` |
| **`ms-entrepot-omop`** | **G3** | Health Data Hub : réception des données cliniques FHIR, pseudonymisation, conversion au standard OMOP et contrôle qualité | `192.168.1.13` | `8006` | `/api/v1/entrepot/**` |
| **`ms-recherche`** | **G3** | Health Data Hub : soumission et validation comité des projets de recherche, accès sécurisé aux données autorisées et registre du diabète | `192.168.1.13` | `8007` | `/api/v1/recherche/**` |
| **`ms-ia-complications`** | **G3** | Health Data Hub : modèle prédictif du risque de complication clinique avec explicabilité des facteurs | `192.168.1.13` | `8008` | `/api/v1/complications/**` |
| **`ms-ai-grc`** | **G4** | Registre national des IA en santé, classification par niveau de risque et suivi des analyses d'impact CNDP | `192.168.1.14` | `8009` | `/api/v1/ai-grc/**` |
| **`ms-certification-dm-ia`** | **G4** | Instruction des dossiers DM-IA, évaluation sur jeux de test scellés et registre public des IA certifiées | `192.168.1.14` | `8010` | `/api/v1/certification/**` |
| **`ms-macert-soc`** | **G4** | Guichet national de déclaration des incidents de cybersécurité en santé, SOC mutualisé et notification de re-certification IA | `192.168.1.14` | `8011` | `/api/v1/macert/**` |
| **`ms-mdp-clinique`** | **G4** | Dossier Médical Partagé / DPI centralisé basé sur l'INS (HL7 FHIR R4 / DICOM) couvrant consultations, prescriptions, biologie et consentement | `192.168.1.14` | `8012` | `/fhir/v1/mdp/**` |
| **`ms-health-data-exchange`** | **G5** | Plateforme d'interopérabilité interhospitalière FHIR R4, fédération des documents cliniques, consentements granulaires et traçabilité | `192.168.1.15` | `8013` | `/fhir/v1/exchange/**` |
| **`ms-hospital-discharge`** | **G5** | Orchestration du workflow de sortie hospitalière, préremplissage des comptes rendus et vérification des prérequis médicaux | `192.168.1.15` | `8014` | `/api/v1/discharge/**` |
| **`ms-medical-scribe`** | **G5** | Assistant de documentation clinique (Speech-to-Text, NLP, IA générative) avec validation humaine intégrée | `192.168.1.15` | `8015` | `/api/v1/scribe/**` |

---

## Prérequis
- **Proxmox VE** (Postes salle TP4) ou environnement local
- **Docker** et **Docker Compose**
- **Git**

---

## Démarrage

1. **Cloner le dépôt :**
   ```bash
   git clone https://github.com/alaaaa-ae/Smart-Health-Nation-Microservices.git
   cd Smart-Health-Nation-Microservices
   ```

2. **Configurer les variables d'environnement :**
   ```bash
   cp .env.example .env
   ```

3. **Démarrer les services en utilisant Docker Compose :**
   ```bash
   docker-compose up -d --build
   ```
   Les blocs de services sont actuellement commentés en attendant les implémentations et Dockerfiles. La commande ne démarrera donc aucun microservice ; décommentez chaque bloc après l'ajout de son implémentation.

## Développement

Chaque service est contenu dans son propre répertoire à la racine (`ms-<nom>/`). Pour développer un service spécifique :

1. Naviguer vers le répertoire du service.
2. Consulter les contrats d'échange JSON dans `doc/payloads/` pour aligner les entrées/sorties avec les autres groupes.
3. Suivre le `README.md` spécifique au service et exposer sa documentation OpenAPI/Swagger (`/docs`).

## Contribution

1. **Forker** le dépôt central.
2. **Créer une branche** préfixée par votre groupe (`git checkout -b feat/g2-ms-pharmacy-sentinel`).
3. **Commiter** vos changements (`git commit -m "feat(g2): ajout de ms-pharmacy-sentinel"`).
4. **Pousser** vers votre branche (`git push origin feat/g2-ms-pharmacy-sentinel`).
5. **Ouvrir une Pull Request** vers `main` en veillant à ne modifier que vos dossiers, vos routes dans `api-gateway/` et vos contrats dans `doc/payloads/`.