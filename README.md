# LedgerLocal Pro

A professional personal-finance workspace built from the provided product documentation.

## What is included

- Spring Boot backend with JWT authentication, Flyway migrations, H2 dev storage, and PostgreSQL-ready config in a single application.properties file
- Finance domain for accounts, categories, counterparties, transactions, lend/borrow, settlements, dashboard, and reports
- Responsive React frontend for quick add, full entry, ledger management, reports, and settings
- Seeded demo account and sample transactions for instant review

## Demo login

- Email: `demo@ledgerlocal.app`
- Password: `demo1234`

## Run locally

### Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Backend URL: `http://localhost:8080`

### Frontend

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Frontend URL: `http://localhost:5173`

## Production-style frontend preview

```powershell
cd frontend
npm.cmd run build
npm.cmd run preview -- --host 127.0.0.1 --port 4173
```

Preview URL: `http://127.0.0.1:4173`

## PostgreSQL mode

The backend runs on H2 by default for easy setup. By default the app stores the H2 files in `%USERPROFILE%\\expense-data` on Windows or `~/expense-data` on Linux/macOS, so it stays stable across restarts and across systems unless you override `EXPENSE_DATA_DIR`.

To use PostgreSQL from the same `application.properties`, activate the `postgres` profile and set the connection environment variables:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE='postgres'
$env:POSTGRES_URL='jdbc:postgresql://localhost:5432/expense_app'
$env:POSTGRES_USER='postgres'
$env:POSTGRES_PASSWORD='postgres'
.\mvnw.cmd spring-boot:run
```

To use a custom local H2 data folder instead of the default path:

```powershell
$env:EXPENSE_DATA_DIR='D:\expense-data'
```

## Build checks completed

- `backend`: `.\mvnw.cmd test`
- `backend` standalone package: `.\mvnw.cmd -DskipTests package`
- `backend` with bundled frontend: `.\mvnw.cmd -DskipTests package -Pbundle-frontend`
- `frontend`: `npm.cmd run lint`
- `frontend`: `npm.cmd run build`

## Backend-only deploy build

```powershell
cd backend
.\mvnw.cmd -DskipTests package
```

JAR output: `backend\target\expense.jar`

Use this mode for Railway, Render, or any separate backend deployment where the frontend is hosted independently.

## Bundled frontend build

```powershell
cd backend
.\mvnw.cmd -DskipTests package -Pbundle-frontend
```

This mode keeps the older combined packaging flow for environments where you want Spring Boot to serve the built frontend assets from the same artifact.

## Run bundled artifact

1. Build with `bundle-frontend` if you want the backend jar to serve the frontend too.
2. Run `java -jar backend\target\expense.jar`
3. Open `http://localhost:8080/`

Notes:

- The bundled build includes the built React frontend.
- If PostgreSQL variables are not provided, the same jar starts with its own local H2 database automatically.

## API docs

- Swagger UI: `http://localhost:8080/swagger-ui.html`
