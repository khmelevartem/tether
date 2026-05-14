# Tether

P2P-передача файлов между устройствами на разных OS — по локальной Wi-Fi сети, без облака, без аккаунтов, без сжатия.

**Сценарий, который Tether закрывает:** фото с Android-телефона на MacBook сегодня едет через мессенджер (сжатие), почту (лимиты) или кабель. Tether заменяет это двумя тапами на одной Wi-Fi — оригинальный файл идёт напрямую между устройствами.

**Таргеты:** Android, iOS, macOS, Desktop (JVM на Windows/Linux). Kotlin Multiplatform + Compose Multiplatform.

**Статус:** ранний MVP. Дискавери, базовый протокол передачи и Desktop CLI работают; UI и pairing в работе.

## Документация

- [Vision & принципы](docs/product/vision.md) — что мы строим и почему.
- [Roadmap](docs/product/roadmap.md) — что в MVP, что после, что отложено.
- [Фичи](docs/product/features/README.md) — статус по каждой фиче и ссылки на спеки.
- [Tech stack](docs/product/tech-stack.md) — выбор стека.
- [Security](docs/product/security.md) — модель угроз, pairing, шифрование.
- [Engineering docs](docs/engineering/README.md) — архитектура, модули, DI, тестирование.

## Quick start

Требуется JDK 21 (Temurin).

### Desktop CLI (отладочный раннер)

Стартует `FileServer` + mDNS-дискавери, читает команды из stdin (`list`, `send <peer> <path>`, `quit`).

```bash
# первый раз — собрать uber JAR и поставить wrapper в ~/.local/bin
./gradlew :composeApp:installJar -q

# убедись, что ~/.local/bin в PATH
export PATH="$PATH:$HOME/.local/bin"

# запуск с дефолтами (случайный порт, имя устройства = "Tether-$USER")
tether

# свои имя и порт
tether --name MyMac --port 8080
```

Проверка, что сервер живой: `curl http://localhost:<port>/health` → `Tether OK`.

Пример сессии:
```
> send Phone /tmp/photo.jpg
[send] 12.3 MB / 50.0 MB  (3.4 MB/s)
[send] OK — 14523 ms  →  /tmp/tether-downloads/photo.jpg
```

### Desktop UI

Запускает Compose-интерфейс с реальным wiring (discovery + file server):

```bash
./gradlew :composeApp:run -q
```

Для распространения (native app bundle):

```bash
./gradlew :composeApp:packageReleaseDistributionForCurrentOS
```

### Android

```bash
./gradlew :composeApp:assembleDebug
```

APK — в `composeApp/build/outputs/apk/debug/`. Или запускай run-конфигурацию `composeApp` из Android Studio.

### iOS

Открой `iosApp/` в Xcode и запусти, либо используй iOS run-конфигурацию из IDE (Android Studio / Fleet с KMP-плагином).

### macOS (Apple Silicon)

`macosArm64` компилируется в native binary. Запуск — через IDE run-конфигурацию или Xcode. Чтобы проверить mDNS без UI:

```bash
dns-sd -B _tether._tcp.    # должно появиться _tether._tcp.
```

## Для контрибьюторов

- [CLAUDE.md](CLAUDE.md) — что обязан знать AI-агент или новый контрибьютор: архитектурные инварианты, git conventions, worktree-дисциплина.
- [docs/engineering/](docs/engineering/README.md) — архитектура и правила написания кода (DI, modules, testing).
- [.claude/skills/](.claude/skills/) — multi-agent скиллы: `/implement` (issue → PR оркестратор), `/code-review` (параллельный multi-agent review).
- [.claude/commands/](.claude/commands/) — slash-команды для типовых workflow (`/close-issue`, `/check-review`, `/grooming`, `/retro`, `/quick-issue`; `/work-on-issue` — manual fallback).

Тесты:
```bash
./gradlew allTests -q
```

KtLint запускается автоматически через git hook — руками не вызывай.
