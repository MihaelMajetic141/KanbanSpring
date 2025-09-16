
# 💻 Kanban Spring application

A Spring Boot application that enables CRUD endpoints for task management, implements Spring Security and live WebSocket updates via Stomp. <br>
Comes with PostgreSQL database and Docker Compose setup for one‐command startup.

---

## 🛠️ Tech Stack

- **Back-end:** Java • Spring Boot • JPA • Hibernate • Spring Security • JWT
- **Database:** PostgreSQL  
- **WebSockets:** Stomp
- **Build:** Maven
- **Containerization:** Docker • Docker Compose
- **Tests:** JUnit5 + Mockito • Integration: @SpringBootTest + Testcontainers (PostgreSQL)
- **Documentation:** Swagger • OpenAPI

---

## ⚙️ Features

- ✅ CRUD endpoints for `Task` and `Project`
- ✅ Live updates broadcasted through WebSocket Stomp endpoint
- ✅ Spring Security
- ✅ User authentication and authorization
- ✅ Fully containerized (app + DB)

---

## 🔧 Prerequisites

- [Java 21](https://www.oracle.com/java/technologies/downloads/#java21) (for local build)  
- [Maven](https://maven.apache.org/install.html)
- [Docker & Docker Compose](https://docs.docker.com/get-started/get-docker/)
- [Git](https://github.com/git-guides/install-git)

---

## 🚀 Quick Start with Docker Compose

1. **Clone the repo**  
   ```bash
   git clone https://github.com/MihaelMajetic141/KanbanSpring
   cd KanbanSpring
   ```

2. **Build & run - Ensure ports 8080 & 5433 are free to use.**

   ```bash
   docker-compose up --build
   ```

3. **Browse the API**

    * Service: `http://localhost:8080`
    * Health:  `http://localhost:8080/actuator/health`
    * Swagger OpenAPI:  `http://localhost:8080/swagger-ui/index.html#/`
   

4. **Reset & rebuild** (fresh DB)

   ```bash
   docker-compose down -v
   docker-compose up --build
   ```

---

## 🔬 Testing

Run tests with:

   ```bash
   mvn clean verify
   ```

- Runs unit and integration tests (using Testcontainers for PostgreSQL).
- Generates a JaCoCo coverage report in target/site/jacoco/index.html.

Optional - Using IntelliJ IDEA:
- Open the project in IntelliJ.
- Right-click src/test/java/com/kanban and select Run 'All Tests' with Coverage.
- View coverage in the Coverage tool window.

---