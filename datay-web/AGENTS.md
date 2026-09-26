# Agent Guidelines for Datafusion

This is a JHipster 8.11.0 project with Vue 3 frontend (TypeScript) and Spring Boot 3.4.5 backend (Java 17).

## Build Commands

### Frontend (Vue/TypeScript)

```bash
# Install dependencies
./npmw install

# Development
npm run start                    # Start Vite dev server (port 9000)
npm run webapp:dev               # Same as start
npm run watch                    # Run frontend + backend concurrently

# Build
npm run webapp:build             # Production build
npm run webapp:build:dev         # Dev build

# Testing
npm test                         # Run all tests with coverage
npm run test:watch               # Watch mode
npm run vitest-run               # Run tests without watch

# Run a single test file
npx vitest run src/main/webapp/app/shared/sort/sorts.spec.ts

# Linting
npm run lint                     # ESLint check
npm run lint:fix                 # ESLint auto-fix
npm run prettier:check           # Prettier check
npm run prettier:format          # Prettier format
```

### Backend (Java/Maven)

```bash
# Start Spring Boot
./mvnw                           # Start with batch mode
./mvnw -Dskip.installnodenpm -Dskip.npm    # Skip frontend build

# Development
npm run backend:start            # Start backend only (no frontend)
npm run backend:debug            # Start with JDWP debug on port 8000

# Testing
npm run backend:unit:test        # Run backend tests (quiet mode)
./mvnw verify                    # Run all backend tests
./mvnw test -Dtest=FlowTest      # Run single test class

# Build
npm run build                    # Full production build
npm run java:jar                 # Build jar
npm run java:jar:dev             # Build dev jar

# Code quality
npm run backend:nohttp:test      # Checkstyle validation
npm run backend:doc:test         # Generate javadoc
```

### Full Stack / CI

```bash
npm run ci:backend:test          # Backend: info + javadoc + checkstyle + tests
npm run ci:frontend:test         # Frontend: build + test
```

## Code Style

### TypeScript / Vue

- **Indent**: 2 spaces (via `.editorconfig`)
- **Print width**: 140 characters
- **Quotes**: Single quotes
- **Trailing commas**: ES5 (trailing in multi-line)
- **Semicolons**: Required
- **Imports order**: Vue → Vue ecosystem → External → Internal (@/)
- **Type imports**: Use `@typescript-eslint/consistent-type-imports` (error level)
  - Import types with `import type { SomeType }` when only using type

### Java

- **Indent**: 4 spaces
- **Format**: Uses Spotless Maven plugin with Google Java Format
- Run `mvnw spotless:apply` to auto-format

### General

- **Line endings**: LF (Unix)
- **Charset**: UTF-8
- **Trailing whitespace**: Trim
- **Final newline**: Required

## Linting Configuration

### ESLint (Frontend)

Key rules in `eslint.config.mjs`:

- `@typescript-eslint/consistent-type-imports: error`
- `vue/multi-word-component-names: off`
- `no-console`: warn in production, off in dev
- `no-debugger`: warn in production, off in dev

### Prettier (Frontend)

Configured in `.prettierrc`:

- Plugins: `prettier-plugin-packagejson`, `prettier-plugin-java`
- Print width: 140, tabWidth: 2, singleQuote: true

### Pre-commit Hooks

Husky + lint-staged configured in `.lintstagedrc.cjs`:

- Runs prettier --write on staged files before commit

## Testing

### Frontend (Vitest)

- **Location**: `src/main/webapp/app/**/*.spec.ts`
- **Environment**: happy-dom
- **Coverage thresholds**: statements 85%, branches 75%, lines 85%
- **Reporter**: vitest-sonar-reporter (output: `target/test-results/TESTS-results-vitest.xml`)

### Backend (JUnit 5)

- **Location**: `src/test/java/com/data/datafusion/`
- **Naming**: `*Test.java` for unit tests
- **Integration tests**: `*IntegrationTest.java`

## Project Structure

```
src/main/
├── webapp/app/              # Vue TypeScript source
│   ├── account/             # Account management
│   ├── admin/                # Admin panel (users, logs, metrics)
│   ├── core/                 # Core components (home, navbar, error)
│   ├── entities/             # Generated JHipster entities
│   ├── router/               # Vue Router config
│   └── shared/               # Shared utilities, config, services
├── java/                     # Java source
│   └── com/data/datafusion/
│       ├── config/           # Spring configuration
│       ├── domain/           # JPA entities
│       ├── repository/        # JPA repositories
│       ├── service/          # Business services
│       └── web/              # REST controllers
└── resources/
    └── config/              # Application configuration (yml/properties)
```

## Path Aliases

TypeScript paths (defined in `tsconfig.app.json`):

- `@/*` → `src/main/webapp/app/*`
- `@content/*` → `src/main/webapp/content/*`

Vite aliases (defined in `vite.config.mts`):

- Same as above

## Key Technologies

- **Frontend**: Vue 3.5, Pinia 3.0, Vue Router 4.5, Element Plus 2.x, Vue Flow 1.x
- **Backend**: Spring Boot 3.4.5, Java 17, JPA/Hibernate, Spring Security
- **Build**: Vite 6, Maven 3.2.5, npm 11.3.0, Node 22.15.0+
- **Testing**: Vitest 3.1, JUnit 5, Happy DOM, @vue/test-utils

## Error Handling

### Frontend

- Axios interceptors configured in `src/main/webapp/app/shared/config/axios-interceptor.ts`
- 401 errors trigger logout and login modal
- Error component at `src/main/webapp/app/core/error/`

### Backend

- Standard Spring Boot error handling
- H2 console available at `/h2-console` (dev profile)
- Actuator endpoints at `/management/*`

## Important Notes

1. **JHipster markers**: Files contain `jhipster-needle-*` comments - these are injection points for generator. Don't remove them.

2. **Vue version**: The app runs on a single Vue 3 runtime. Do not reintroduce Vue 2 / `@vue/compat` or `bootstrap-vue` (v2). Use Element Plus for all UI components. The shared dialog wrapper `app-modal` (Element Plus `el-dialog`) replaces the former `b-modal` API (`ref.show()/hide()`, `#modal-title`, `#modal-footer`).

3. **Spring profiles**: Default is `dev`. Active profile set in `pom.xml` as `spring.profiles.active=dev`.

4. **Docker**: Backend runs on port 8080, frontend Vite dev server on port 9000 with proxy to backend.
