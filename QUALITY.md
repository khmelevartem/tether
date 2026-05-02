# Code Quality & CI/CD

## Linting with KtLint

This project uses default **KtLint** for Kotlin code style enforcement.

## Running Tests

### All Tests
```bash
./gradlew allTests
```

## CI/CD Pipeline

GitHub Actions runs automatically on:
- **push** to all branches
- **pull requests** to `main`

Pipeline checks:
1. **KtLint** — Code style enforcement
2. **Tests** — JVM and Common test suites

See `.github/workflows/ci.yml` for details.
