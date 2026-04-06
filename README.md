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

The backend runs on H2 by default for easy setup. By default the WAR stores the H2 files in `%USERPROFILE%\\expense-data` on Windows or `~/expense-data` on Linux/macOS, so it stays stable across restarts and across systems unless you override `EXPENSE_DATA_DIR`.

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
- `frontend`: `npm.cmd run lint`
- `frontend`: `npm.cmd run build`

## Build WAR for Apache Tomcat

```powershell
cd backend
.\mvnw.cmd clean package
```

WAR output: `backend\target\expense.war`

Tomcat deployment:

1. Copy `backend\target\expense.war` into your Tomcat `webapps` folder.
2. Start Tomcat.
3. Open `http://localhost:8080/expense/`

Notes:

- The WAR already includes the built React frontend.
- API calls automatically use the Tomcat app context path, so `/expense/` works without extra frontend changes.
- If PostgreSQL variables are not provided, the same WAR starts with its own local H2 database automatically.
- This app is built on Spring Boot 3 and should be deployed to a Jakarta-compatible Tomcat version such as Tomcat 10.1+.

## API docs

- Swagger UI: `http://localhost:8080/swagger-ui.html`
