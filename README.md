# Expense Tracker with AI-Powered Categorization

A Spring Boot REST API for tracking personal expenses, with JWT authentication,
automatic expense categorization via the Gemini API, and concurrent bulk CSV
import using a fixed-size `ExecutorService` thread pool.

## Features

- **JWT-based authentication** — register/login endpoints issue a signed JWT;
  all expense endpoints require a valid token.
- **Expense CRUD** — create, list (paginated), retrieve, update, and delete
  expenses, scoped to the authenticated user.
- **AI categorization** — free-text expense descriptions are classified into
  one of a fixed set of categories (`Food`, `Travel`, `Bills`, `Shopping`,
  `Entertainment`, `Other`) via a call to the Gemini API.
- **Concurrent CSV import** — bulk-upload a CSV of expenses; each row is
  parsed, categorized, and saved in parallel across a fixed thread pool, with
  a total/success/failed summary returned to the caller.

## Tech Stack

| Layer | Choice |
|---|---|
| Language / Runtime | Java 17 |
| Framework | Spring Boot 3.3 (Web, Data JPA, Security, Validation) |
| Database | PostgreSQL |
| Auth | JWT (`jjwt`) + Spring Security, stateless sessions |
| AI | Google Gemini API (via `RestTemplate`) |
| CSV parsing | Apache Commons CSV |
| Build | Maven |

## Project Structure

```
src/main/java/com/expensetracker/
├── entity/         # JPA entities (User, Expense)
├── repository/      # Spring Data JPA repositories
├── dto/             # Request/response DTOs (never expose entities directly)
├── security/        # JWT filter, JWT util, Spring Security config
├── service/         # Business logic (auth, expenses, Gemini, CSV import)
├── controller/       # REST controllers
├── exception/        # Custom exceptions + global exception handler
└── config/           # Bean configuration (RestTemplate, ExecutorService)
```

## Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 14+ running locally or reachable over the network
- A Gemini API key ([Google AI Studio](https://aistudio.google.com/apikey))

## Environment Variables

All configuration that could be sensitive or environment-specific is read
from environment variables in `application.properties` — nothing is
hardcoded, and the required variables have **no default values**, so the
app fails fast on startup with a clear error if one is missing, rather than
silently running with a guessable fallback.

| Variable | Required | Description | Example |
|---|---|---|---|
| `DB_URL` | Yes | JDBC connection string for PostgreSQL | `jdbc:postgresql://localhost:5432/expense_tracker` |
| `DB_USERNAME` | Yes | Database username | `postgres` |
| `DB_PASSWORD` | Yes | Database password | *(your local password)* |
| `JWT_SECRET` | Yes | Secret key used to sign JWTs — must be at least 32 characters for HS256 | *(generate one, see below)* |
| `JWT_EXPIRATION_MS` | No (defaults to `86400000`, 24h) | Token lifetime in milliseconds | `86400000` |
| `GEMINI_API_KEY` | Yes | Your Gemini API key | *(from Google AI Studio)* |
| `GEMINI_API_URL` | No (has a working default) | Override if you want a different Gemini model | — |

Generate a strong JWT secret:
```bash
openssl rand -base64 32
```

## Running Locally

**1. Create the database**
```sql
CREATE DATABASE expense_tracker;
```

**2. Set environment variables**
```bash
export DB_URL=jdbc:postgresql://localhost:5432/expense_tracker
export DB_USERNAME=postgres
export DB_PASSWORD=your_local_password
export JWT_SECRET=$(openssl rand -base64 32)
export GEMINI_API_KEY=your_gemini_api_key
```

**3. Run**
```bash
mvn spring-boot:run
```

Spring Data JPA (`ddl-auto=update`) creates the `users` and `expenses`
tables automatically on first startup.

## API Reference

### Auth
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Create an account, returns a JWT |
| POST | `/api/auth/login` | Authenticate, returns a JWT |

### Expenses (require `Authorization: Bearer <token>`)
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/expenses` | Create an expense |
| GET | `/api/expenses?page=0&size=10` | List expenses (paginated) |
| GET | `/api/expenses/{id}` | Get a single expense |
| PUT | `/api/expenses/{id}` | Update an expense |
| DELETE | `/api/expenses/{id}` | Delete an expense |
| POST | `/api/expenses/categorize` | Suggest a category for free-text |
| POST | `/api/expenses/import` | Bulk-import expenses from a CSV file |

### Example requests

```bash
# Register
curl -X POST localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane","email":"jane@test.com","password":"pass123"}'

# Login
curl -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@test.com","password":"pass123"}'

# Create an expense
curl -X POST localhost:8080/api/expenses \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"amount":45.50,"description":"Uber to airport","category":"Travel","date":"2026-08-10"}'

# Categorize free text
curl -X POST localhost:8080/api/expenses/categorize \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"description":"Dinner at Olive Garden"}'

# Bulk import (CSV columns: description,amount,date)
curl -X POST localhost:8080/api/expenses/import \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@expenses.csv"
```

## Key Design Decisions

- **Stateless JWT auth** — `SecurityConfig` disables session creation
  entirely; `JwtAuthFilter` validates the Bearer token once per request and
  populates the security context directly.
- **Row-level ownership enforcement** — expense lookups use
  `findByIdAndUserId`, so a user cannot access another user's expense by ID
  guessing; a mismatch returns 404 rather than leaking existence via 403.
- **DTOs never expose entities** — request/response shapes are decoupled
  from the JPA model, so fields like the password hash can't accidentally
  serialize into a response.
- **Gemini failures degrade gracefully** — any failure calling Gemini
  (missing key, network error, unexpected response) falls back to the
  `"Other"` category instead of failing the request.
- **Explicit `ExecutorService` over `@Async`** — CSV import uses a shared
  fixed thread pool (`ExecutorServiceConfig`) with one `Callable` submitted
  per row; results are collected via `Future.get()`. This keeps the
  concurrency mechanism explicit and easy to reason about and explain,
  rather than hidden behind a proxy.
- **Per-row fault isolation** — a malformed CSV row returns an error string
  instead of throwing, so one bad row doesn't abort the rest of the batch.
