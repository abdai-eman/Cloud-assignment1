# Cloud Computing Assignment1: Country Lookup REST Service

This project implements a cloud-native reverse geocoding REST web service built with **Spring Boot**, containerized using an efficient **multi-stage Dockerfile**, and deployed onto a local **Kubernetes (Minikube)** cluster with multiple replicas.

---

## Project Structure & Deliverables
* **Part 1:** Spring Boot REST Service validating coordinates and querying the OpenStreetMap Nominatim API.
* **Part 2:** Multi-stage `Dockerfile` creating an optimized runtime image using Eclipse Temurin JDK and Alpine JRE.
* **Part 3:** Minikube Kubernetes Deployment (`deployment.yaml`) configured with **2 replicas** and a ClusterIP Service.
* **Screencast Video:** 

---

## 1. Spring Boot REST Service (Part 1)

### Functionality & Validation
The service exposes a single endpoint at `/api/country` which takes `latitude` and `longitude` query parameters.
* **Input Validation:** 
  * Latitude must be a numeric value between `-90` and `90`.
  * Longitude must be a numeric value between `-180` and `180`.
  * Missing, blank, or out-of-range coordinates return an HTTP `400 Bad Request`.
* **External API Integration:** Queries the OpenStreetMap Nominatim API via Spring `WebClient`. Returns HTTP `502 Bad Gateway` if the external API fails or yields unusable data.

### Build and Run Locally (Maven)
To compile and run the application locally from your terminal:

```bash
# Clean and package the application jar
mvn clean package

# Run the Spring Boot application
mvn spring-boot:run
```

### Testing the Endpoint
Once the application starts, you can test it locally using `curl`:

```bash
curl "http://localhost:8080/api/country?latitude=45.0&longitude=9.0"
```

**Expected JSON Response:**
```json
{
  "country": "Italy",
  "countryCode": "IT",
  "attribution": "Data © OpenStreetMap contributors, ODbL 1.0: https://www.openstreetmap.org/copyright"
}
```

---

## 2. Multi-Stage Dockerfile (Part 2)

The project uses a multi-stage build: the first stage builds the project with Maven and Eclipse Temurin 17, while the second stage packages only the compiled artifact into a lightweight Alpine JRE image.

### Build and Run with Docker
Run the following commands from the root directory:

```bash
# 1. Build the multi-stage Docker image
docker build -t geolocator-service:latest -f Dockerfile .

# 2. Run the Docker container locally
docker run -p 8080:8080 geolocator-service:latest
```

### Docker Image Size
To check the optimized size of the final image, run:
```bash
docker images geolocator-service:latest
```
*(The final image size is optimized to approximately **~160MB**).*

---

## 3. Kubernetes / Minikube Deployment (Part 3)

The application is deployed to a local Kubernetes cluster using Minikube with **2 replicas** configured in `deployment.yaml`.

### Step-by-Step Deployment Instructions

1. **Start Minikube:**
   ```bash
   minikube start --driver=docker
   ```

2. **Load the Docker Image into Minikube:**
   ```bash
   minikube image load geolocator-service:latest
   ```

3. **Apply the Kubernetes Manifest:**
   ```bash
   kubectl apply -f deployment.yaml
   ```

4. **Verify Replica Pods (Confirm 2 replicas are running):**
   ```bash
   kubectl get pods
   ```
   *You will see two running pods with names starting with `country-lookup-...`.*

5. **Expose and Access via Port Forwarding:**
   ```bash
   kubectl port-forward svc/country-lookup 8080:8080
   ```

6. **Test the Service:**
   Open a separate terminal window and run:
   ```bash
   curl "http://localhost:8080/api/country?latitude=45.0&longitude=9.0"
   ```

---

AI assistance was used to help prepare the implementation, tests and documentation. Review the code and follow the course's disclosure rules before submitting.
---