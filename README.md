# Smart-Health-Nation-Microservices

## Aperçu
**Smart-Health-Nation-Microservices** est une application distribuée basée sur des microservices conçue pour l'urbanisation et l'implémentation du Système d'Information de Santé marocain (*Maroc Smart Health Nation 2030*). L'architecture repose sur des services indépendants communiquant via une passerelle API (`api-gateway`) et déployés sur des environnements **Proxmox VE (Salle TP4)**.

---

## Architecture & Répartition par Groupe

> **Consigne aux coordinateurs (G2, G3, G4...) :** Dès que votre groupe *fork* ce dépôt, ouvrez une **Pull Request** modifiant uniquement votre ligne dans le tableau ci-dessous et en ajoutant votre dossier `service-<nom-du-service>/` à la racine.

| Service / Dossier | Groupe | Rôle & Domaine Fonctionnel | IP Proxmox | Port | Protocole / Standard |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`api-gateway`** | Transversal | Point d'entrée central : routage, authentification JWT et équilibrage de charge | `192.168.1.10` | `8080` | REST / HTTP |
| **`service-identite-ins`** | **G1**  | Authentification, résolution de l'Identité Nationale de Santé (INS via CNIE/DGSN) et droits AMO/ALD | `192.168.1.11` | `8001` | REST / JWT |
| **`service-facturation-amo`** | **G1**  | Génération de la Feuille de Soins Électronique (FSE), codage NGAP, calcul TNR (CNSS/CNOPS) et liquidation | `192.168.1.11` | `8002` | REST / FSE |
| **`service-ia-fraude`** | **G1**  | Contrôle intelligent des remboursements AMO et détection d'anomalies/fraudes (Isolation Forest + GNN) | `192.168.1.11` | `8003` | REST / JSON |
| *`service-<a-completer>`* | **G2** | *À compléter par le Groupe 2 lors de sa première Pull Request* | `192.168.1.12` | `8004` | *À préciser* |
| *`service-<a-completer>`* | **G3** | *À compléter par le Groupe 3 lors de sa première Pull Request* | `192.168.1.13` | `8005` | *À préciser* |
| *`service-<a-completer>`* | **G4** | *À compléter par le Groupe 4 lors de sa première Pull Request* | `192.168.1.14` | `8006` | *À préciser* |
| *`service-<a-completer>`* | **G5** | *À compléter par le Groupe 5 lors de sa première Pull Request* | `192.168.1.15` | `8007` | *À préciser* |

---

## Prérequis
* **Proxmox VE** (Postes salle TP4) ou environnement local
* **Docker** et **Docker Compose**
* **Git**

---

## Démarrage

1. **Cloner le dépôt :**
   ```bash
   git clone https://github.com/<alaaaa-ae>/Smart-Health-Nation-Microservices.git
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

---

## Développement
Chaque service est contenu dans son propre répertoire à la racine (`service-<nom>/`). Pour développer un service spécifique :
1. Naviguer vers le répertoire du service.
2. Consulter les contrats d'échange JSON dans `doc/payloads/` pour aligner les entrées/sorties avec les autres groupes.
3. Suivre le `README.md` spécifique au service et exposer sa documentation OpenAPI/Swagger (`/docs`).

---

## Contribution
1. **Forker** le dépôt central.
2. **Créer une branche** préfixée par votre groupe (`git checkout -b feat/g2-nom-du-service`).
3. **Commiter** vos changements (`git commit -m "feat(g2): ajout du service ..."`).
4. **Pousser** vers votre branche (`git push origin feat/g2-nom-du-service`).
5. **Ouvrir une Pull Request** vers `main` en veillant à ne modifier que votre dossier et votre ligne dans le tableau.
