# Чеклист подготовки к собеседованию
### Senior Android / KMP Developer · 2026

---

## Блок 0 — Gradle (заявлен как сильная сторона)

> Подаётся в резюме как сильная сторона → ждать углублённых вопросов, а не «что такое `implementation`». Уметь объяснять механику (что делает Gradle под капотом) и компромиссы, а не только API.

### Модель сборки и жизненный цикл
- [ ] Три фазы: **Initialization** (читает `settings.gradle`, строит граф проектов) → **Configuration** (исполняет build-скрипты ВСЕХ проектов, строит task graph) → **Execution** (гоняет выбранные таски). Что исполняется на configuration, а что на execution
- [ ] Почему «тяжёлый» код в теле build-скрипта (вне таски) — антипаттерн: configuration-фаза гоняется на КАЖДУЮ сборку, даже `./gradlew help`
- [ ] `Project` vs `Task` vs `Gradle` объекты — что когда живёт
- [ ] `settings.gradle(.kts)` — за что отвечает, `include` vs `includeBuild`
- [ ] Gradle Daemon — зачем, что кэширует между сборками, когда «протухает»

### Configuration avoidance (ключевой senior-вопрос)
- [ ] `tasks.register` vs `tasks.create` — почему register ленивый и предпочтителен
- [ ] `Provider<T>` / `Property<T>` — ленивые значения, почему не читать `.get()` на configuration-фазе
- [ ] `TaskProvider` — отложенная конфигурация, `configure { }`
- [ ] Почему `afterEvaluate { }` — code smell и что вместо него

### Инкрементальность и кэши
- [x] Up-to-date checks — как Gradle решает пропустить таску (хэши inputs/outputs)
- [ ] `@Input` / `@InputFiles` / `@OutputFiles` / `@OutputDirectory` — аннотации кастомной таски, как влияют на incremental
- [x] **Build Cache** (local + remote) — переиспользование outputs между сборками/машинами/CI; чем отличается от up-to-date
- [x] Что делает таску `cacheable` и почему не все таски кэшируемы (non-deterministic outputs, absolute paths)
- [x] **Configuration Cache** — что кэширует (результат configuration-фазы), какие ограничения накладывает на скрипты (нельзя `Project` в execution, нельзя `Task.project`)
- [ ] `--profile`, Build Scan (`--scan`) — где смотреть, что тормозит сборку

### Управление зависимостями
- [x] `api` vs `implementation` — что протекает в транзитивный classpath потребителя, влияние на incremental compilation и build time
- [ ] `compileOnly` / `runtimeOnly` / `testImplementation` — когда что
- [ ] Configurations как граф — resolvable vs consumable vs dependency-scope
- [ ] **Version Catalogs** (`libs.versions.toml`) — зачем, type-safe accessors, `bundles`, `plugins`
- [ ] BOM / `platform()` / `enforcedPlatform()` — выравнивание версий транзитивных зависимостей
- [ ] Dependency constraints vs `resolutionStrategy.force` — разница, когда что
- [ ] Как разрулить конфликт версий — стратегия по умолчанию (highest wins), как переопределить
- [ ] Variant-aware resolution и **attributes** — как Gradle выбирает нужный артефакт (особенно остро в KMP/Android)

### Плагины и переиспользование build-логики
- [ ] Script plugins vs precompiled script plugins vs binary plugins — три уровня
- [ ] **Convention plugins** — precompiled `*.gradle.kts` в `build-logic`, как убирают копипасту между модулями
- [ ] `buildSrc` vs `build-logic` как included build — почему `build-logic`/composite предпочтительнее (buildSrc инвалидирует кэш всего проекта при любом изменении)
- [ ] **Composite builds** (`includeBuild`) — подмена зависимости локальным проектом, разработка библиотеки + потребителя вместе
- [ ] Жизненный цикл плагина: `Plugin<Project>.apply()`, `project.extensions.create()` для DSL-конфига
- [ ] `PluginManagement` блок в settings — откуда резолвятся плагины

### Производительность
- [ ] Parallel execution (`org.gradle.parallel`), `--max-workers` — на чём параллелит (на уровне проектов/тасок)
- [ ] `org.gradle.jvmargs` в `gradle.properties` — heap демона, типичные OOM
- [ ] JVM Toolchains (`kotlin { jvmToolchain(17) }`) — зачем, как Gradle авто-провижинит JDK
- [ ] `dependsOn` vs `mustRunAfter` vs `finalizedBy` — ordering vs dependency; implicit deps через input/output провайдеры

### Kotlin DSL и KMP-специфика (твой проект)
- [ ] Kotlin DSL vs Groovy DSL — type safety, IDE-автокомплит, цена компиляции скриптов
- [ ] Как KMP-плагин порождает source sets и компиляции на target; иерархия `commonMain` → `androidMain`/`iosMain`/`jvmMain` в терминах Gradle-конфигов
- [ ] `dependsOn` между source set'ами vs `associateWith` (friend-компиляция) — на уровне Gradle (см. твой разбор `desktopCli associateWith main`)
- [ ] Почему рассинхрон версий Kotlin/Compose-плагина — реальный риск в multi-module KMP (Compose Compiler version-locked к Kotlin)
- [ ] Android Gradle Plugin поверх KMP — flavors, build types, как влияют на variant resolution

---

## Блок 1 — Kotlin

### Система типов
- [ ] `Nothing` vs `Unit` vs `Void` — чем отличаются, где применяются
- [ ] Платформенные типы (`String!`) — откуда берутся, чем опасны
- [ ] `@Nullable` / `@NotNull` в Java и как Kotlin их интерпретирует
- [x] `as` vs `as?` — когда бросает исключение

### Классы и объекты
- [ ] `data class` — что генерирует компилятор: `equals`, `hashCode`, `toString`, `copy`, `componentN`
- [ ] `copy` — поверхностная копия, что с этим делать
- [x] `value class` (`@JvmInline`) — боксинг/анбоксинг, когда vs `data class`
- [x] `data object` (Kotlin 1.9+) — чем отличается от `object` и `data class`
- [x] `sealed class` vs `sealed interface` — когда что, exhaustive `when`
- [ ] `object` — singleton, companion object, thread-safety, порядок инициализации
- [ ] `inner class` vs вложенный класс — захват ссылки, утечки памяти
- [ ] Делегирование через `by` — `lazy`, `observable`, `vetoable`, кастомные делегаты

### Функции
- [x] `inline` — что делает компилятор, когда применять, оверхед лямбд
- [ ] `reified` — почему только с `inline`, как обходит type erasure
- [ ] Extension functions — компилируются в статический метод, не могут переопределить member
- [ ] Scope functions (`let` / `run` / `with` / `apply` / `also`) — receiver vs argument, возвращаемое значение
- [ ] Функциональные типы под капотом — `Function0`, `Function1`, SAM-conversion

### Generics
- [ ] Variance: `out` (ковариантность) vs `in` (контравариантность) vs инвариантность, PECS
- [ ] Type erasure в JVM — что стирается, почему нельзя `list is List<String>`
- [ ] Star projection `*` — что означает, когда использовать
- [ ] `reified` как решение type erasure для inline-функций

### Kotlin 2.x
- [ ] K2 compiler — единая IR для всех платформ, почему быстрее
- [ ] Context Parameters / Context Receivers — зачем, как альтернатива extension functions
- [ ] Kotlin/Native memory model — старая (заморозка) vs новая (1.7.20+), GC

---

## Блок 2 — Coroutines

### Механика
- [x] `suspend` под капотом — CPS, state machine, что компилятор делает с функцией
- [x] `Continuation<T>` — интерфейс, как реализуется возобновление
- [ ] Корутины vs потоки — почему «легковесные», как маппируются на потоки
- [ ] `CoroutineContext` — `Job`, `Dispatcher`, `CoroutineName`, `CoroutineExceptionHandler`, наследование
- [x] `suspend` vs `blocking` — чем `delay` отличается от `Thread.sleep`

### Structured concurrency
- [x] Что такое structured concurrency — scope, parent-child, когда parent завершается
- [x] `Job` vs `SupervisorJob` — поведение при ошибке дочернего корутина
- [x] `coroutineScope` vs `supervisorScope` — разница в обработке исключений
- [x] Отмена: `cancel()`, `CancellationException`, `isActive`, `ensureActive`, `yield`
- [ ] `NonCancellable` — зачем, как использовать в `finally`
- [ ] Исключение в `async` без `await` — куда девается, когда выбрасывается

### Dispatchers
- [ ] `Main` / `IO` / `Default` / `Unconfined` — когда что, размеры пулов
- [ ] `IO` vs `Default` — IO расширяется до 64, Default = кол-во CPU
- [x] `withContext` — создаёт ли новую корутину? (нет — switch контекста)
- [ ] `MainCoroutineDispatcher.immediate` — что делает, зачем
- [ ] Кастомные диспетчеры через `Executors.asCoroutineDispatcher()`

### Exception handling
- [ ] `CoroutineExceptionHandler` — только для `launch` в root scope, не работает с `async`
- [ ] `launch` vs `async` — первый роняет сразу, второй хранит в Deferred
- [ ] Propagation — как исключение идёт вверх по иерархии
- [ ] `try-catch` внутри корутины — работает только в том же scope

### Flow
- [ ] Cold flow vs Hot flow — новый поток на каждого collector vs один общий
- [x] `StateFlow` vs `SharedFlow` — replay, conflation, начальное значение
- [x] `StateFlow` под нагрузкой — механизм conflation, 100 collectors + 120 updates/sec
- [x] `flatMapLatest` vs `flatMapMerge` vs `flatMapConcat` — параллелизм
- [ ] `combine` vs `zip` — эмит при любом изменении vs ждёт пару
- [ ] `debounce`, `throttleFirst` — поиск с задержкой
- [x] `buffer` / `conflate` / `collectLatest` — backpressure
- [ ] `callbackFlow` — интеграция callback API
- [ ] `channelFlow` — отличие от `flow { }`
- [ ] Отмена Flow — автоматически при отмене корутины-collector

### Channels
- [x] `Channel` vs `Flow` — когда что
- [ ] `produce` builder
- [ ] Почему `BroadcastChannel` deprecated и что вместо (SharedFlow)

---

## Блок 3 — Android Core

### Lifecycle
- [x] Activity / Fragment lifecycle — rotation, process death, multi-window
- [ ] `onSaveInstanceState` vs `ViewModel` — что переживает что
- [ ] `savedStateHandle` в ViewModel — как работает при process death
- [x] Разница `onStop` vs `onPause` в multi-window

### Process death & State
- [x] Что такое process death — когда, что теряется, что остаётся
- [ ] `SavedStateHandle` + `ViewModel` — правильная стратегия
- [ ] `rememberSaveable` в Compose — связь с `SavedStateHandle`
- [ ] `Parcelable` vs `Serializable` — производительность, `@Parcelize`

### ViewModel
- [ ] `viewModelScope` — что отменяет, когда
- [ ] Почему ViewModel не должен знать о View / Context
- [ ] `ViewModel` factory pattern, `viewModels()` delegate
- [ ] `ViewModel` + `SavedStateHandle` — паттерн для complex state

### Lifecycle + Coroutines
- [ ] `lifecycleScope` vs `viewModelScope` — когда что
- [ ] `repeatOnLifecycle` — почему нужен, что было не так с `launchWhenStarted`
- [ ] `flowWithLifecycle` — альтернатива для одного Flow
- [ ] `LiveData` vs `StateFlow` — когда что, `asLiveData()`

### Background work
- [x] `WorkManager` vs `ForegroundService` vs `AlarmManager` — когда что
- [ ] `WorkManager` — guaranteed execution, Doze mode, цепочки, constraints
- [ ] Doze mode & App Standby — как влияют на background
- [ ] Как WorkManager использует `JobScheduler` под капотом

### Memory & Leaks
- [ ] `WeakReference` / `SoftReference` — когда применять
- [ ] Типичные источники утечек: анонимные классы, `static` + Context, listeners
- [ ] Heap Dump — как читать, retained heap vs shallow size
- [ ] LeakCanary — как интегрировать, что показывает

---

## Блок 4 — Compose

### Механика
- [ ] Recomposition — что триггерит, что не триггерит
- [ ] `remember` vs `rememberSaveable` — что переживает recomposition, что переживает смерть
- [ ] `@Stable` / `@Immutable` — что говорят компилятору, когда применять
- [ ] Compose compiler — как определяет стабильность типов
- [ ] `derivedStateOf` — когда использовать вместо `remember`

### Side-effects
- [x] `LaunchedEffect` / `SideEffect` / `DisposableEffect` — когда что
- [x] `rememberCoroutineScope` — зачем, когда vs `LaunchedEffect`
- [ ] `produceState` — bridge от non-Compose к State

### Архитектура
- [x] State hoisting — паттерн, зачем
- [x] `CompositionLocal` — когда применять, чем опасен
- [ ] Navigation в Compose — `NavController`, `NavHost`, back stack, deep links

### Performance
- [ ] Как профилировать recomposition в Android Studio
- [ ] Overdraw в Compose — как избежать
- [x] `key()` — когда нужен в списках. Это **рантайм**-механизм Lazy-layout (не компилятор): ключ задаёт идентичность элемента, к которой привязано его `remember`-состояние и subtree-композиция. Дефолтная идентичность — **индекс/позиция**, поэтому без ключа при insert/remove/**reorder** состояние «прилипает» к позиции, а не к данным (бликает на чужой элемент, прыгает скролл/анимация). Стабильный ключ заставляет Compose **перенести** существующую композицию (со state) на новую позицию, а не пересоздавать. В этом PR ключ был `device.id` (`name@host:port`) — при переподключении пира на новом порту он менялся, и Compose **диспоузил** всю строку вместе с дочерним `PeerTransferComponent` и состоянием передачи; перешли на fingerprint-стабильный `peer.id`, чтобы строка сохраняла идентичность через смену порта.
- [ ] Baseline Profiles для Compose — как ускоряет cold start

---

## Блок 5 — Архитектура

### Паттерны
- [x] Clean Architecture — слои, направление зависимостей, зачем UseCase
- [ ] MVVM vs MVI vs UDF — разница, когда что
- [ ] Repository pattern — зачем, что абстрагирует
- [ ] `sealed class` для UI State — Loading / Success / Error

### Модульность
- [ ] Зачем модульность — build time, изоляция, reuse
- [ ] Feature modules vs Library modules vs App module
- [ ] Dynamic Feature Modules — зачем, как влияют на APK
- [ ] Convention plugins в Gradle — что такое, зачем

### DI
- [ ] Hilt — `@HiltViewModel`, scopes, `@EntryPoint`
- [x] Koin — отличие от Hilt, service locator vs DI
- [x] Без фреймворка (твой опыт) — manual DI, когда оправдан в KMP

---

## Блок 6 — KMP

### Механизм
- [x] `expect` / `actual` — как работает, ограничения
- [x] Alternatives to expect/actual — interfaces + DI, когда что
- [x] Структура модулей: `commonMain` / `androidMain` / `iosMain` / `desktopMain`

### Kotlin/Native
- [ ] Старая memory model (заморозка, `@Frozen`) vs новая (1.7.20+)
- [ ] Как работает GC в Kotlin/Native
- [ ] `suspend` функции в iOS — Swift Export, `async/await` bridge

### Библиотеки
- [x] Ktor Client — multiplatform HTTP
- [ ] SQLDelight — multiplatform database
- [ ] Kotlinx.serialization — vs Gson/Moshi, почему KMP-friendly
- [ ] Kotlinx.datetime, Kotlinx.coroutines multiplatform

---

## Блок 7 — Performance

### Rendering
- [ ] 16ms/frame бюджет — jank, что происходит при превышении
- [ ] Overdraw — как измерить, как устранить
- [ ] `invalidate()` vs `requestLayout()` — какой этап запускает
- [ ] Hardware-accelerated canvas — что поддерживается

### Инструменты
- [ ] Android Studio Profiler — CPU, Memory, Energy
- [ ] Systrace / Perfetto — трассировка, визуализация frame drops
- [ ] Heap Dump analysis — как читать
- [ ] Baseline Profiles + Macrobenchmark — как создать, сколько даёт cold start

### APK / Size
- [ ] R8 vs ProGuard — в чём R8 умнее
- [ ] `shrinkResources` — как работает
- [ ] App Bundle vs APK — почему Bundle меньше
- [ ] APK Analyzer в Android Studio

### Startup
- [ ] Cold / Warm / Hot start — что происходит на каждом
- [ ] `App Startup` library — правильная инициализация зависимостей
- [ ] Baseline Profiles в CI

---

## Блок 8 — Тестирование

- [ ] `JUnit5` + `MockK` — базовые тесты ViewModel и UseCase
- [x] `Fake` vs `Mock` — когда что, архитектурные последствия
- [ ] `Turbine` — тестирование Flow, задержки, cancellation
- [ ] `Compose Testing` — семантическое дерево, `assertIsDisplayed`, `performClick`
- [x] `TestCoroutineDispatcher` / `UnconfinedTestDispatcher` — контроль времени в тестах
- [ ] Integration tests vs Unit tests — что где тестировать

---

## Блок 9 — Java (JVM-контекст)

- [ ] GC — как работает, поколения, когда срабатывает
- [ ] Heap vs Stack — что хранится где
- [ ] ClassLoader — как загружается класс, parent delegation
- [ ] `synchronized` — monitor, reentrant, оверхед
- [x] `volatile` — видимость, не атомарность
- [x] `happens-before` — гарантии JMM
- [ ] `ThreadLocal` — зачем, утечки
- [ ] `@JvmStatic` / `@JvmField` / `@JvmOverloads` — Kotlin-Java interop
- [ ] SAM-conversion — как работает, ограничения

---

## Блок 10 — On-device AI (новое направление)

### ML Kit
- [ ] Text recognition (OCR) — как интегрировать, online vs offline модели
- [ ] Language identification + Translation (58 языков, offline)
- [ ] Face detection, barcode scanning — API уровень
- [ ] Когда ML Kit, а когда LiteRT — критерии выбора

### LiteRT / TFLite
- [ ] Quantization: FP32 → INT8 — почему 4x меньше, потеря точности
- [ ] Hardware delegates: GPU, NNAPI, Hexagon DSP — когда что
- [ ] `Interpreter` API — загрузка модели, `ByteBuffer`, input/output tensors
- [ ] Конвертация моделей из PyTorch/Keras — концептуально

### Gemini Nano / LLM
- [ ] MediaPipe LLM Inference API — Gemma 2B/3B, Phi-2
- [ ] AICore — системный сервис (API 34+), Gemini Nano
- [ ] 4-bit quantization — почему меняет расклад для мобильных
- [ ] On-device vs cloud — трейдоффы: latency, privacy, offline, battery, accuracy

### Архитектурный нарратив (для интервью)
- [ ] Уметь объяснить выбор on-device vs cloud для конкретного use case
- [ ] Рассказать про выбор модели: размер / точность / скорость / батарея
- [ ] Объяснить роль делегатов для аппаратного ускорения

---

## Блок 11 — System Design (Senior-уровень)

- [x] Offline-first приложение — sync стратегия, conflict resolution
- [ ] Лента с пагинацией — Paging 3, стратегии кэша
- [ ] Чат / real-time — WebSocket vs SSE vs polling, reconnect логика
- [ ] B2B/B2C из одной кодовой базы (твой кейс) — flavors, feature flags, разная монетизация
- [ ] Push notifications — FCM, foreground vs background, deep links
- [ ] Кэширование — стратегии (LRU, TTL), Room vs DataStore vs InMemory

---

## Блок 12 — Soft skills / Behavioral

- [ ] STAR-история: GLS-интеграция (кросс-команда, хаос, срок)
- [ ] STAR-история: B2B/B2C разделение (архитектура, риски, результат)
- [ ] STAR-история: DI-стандарт (проблема разногласий → решение → внедрение)
- [ ] STAR-история: PDF-оптимизация (проблема → инструменты → цифры)
- [ ] Ответ на «расскажи о слабости» — «не доделывал, осознал, вот конкретный рост»
- [ ] Ответ на «как используешь AI» — AI-augmented engineering, не вайб-кодинг
- [ ] Elevator pitch — 30 секунд о себе, без воды

---

## Дополнительные вопросы из задач

Сюда `/close-issue` дописывает вопросы, которые задавал по контексту конкретной задачи (не из основных блоков). Уже отмечены как выполненные — сама постановка и разбор прошли в момент закрытия issue.

- [x] Что в поведении Claude Code меняется, когда инструкция лежит как skill (`.claude/skills/<name>/SKILL.md` с YAML frontmatter `description`) против command (plain prompt в `.claude/commands/<name>.md`)? Почему Anthropic смержил commands в skills, и какие практические следствия для проекта.
- [x] Где проходит граница между обоснованным override ревьюверского `[REQUIRED]` блока и deflection'ом — какие признаки делают override честным (knowledge gap у ревьювера, опора на более авторитетный источник, асимметрия в пользу согласия), а какие превращают его в маскировку scope creep (накопление overrides, удобство, апелляция к «общему контексту» без конкретики).
- [x] Какой механизм даёт skill auto-invocation, которого нет у command — что Claude Code загружает в контекст и когда (progressive disclosure: metadata всегда, body лениво при вызове), и почему промоут не «бесплатный» — каждая строка `description` сидит в контексте каждого turn'а, поэтому короткие шаблоны без natural-language триггеров оставляют как commands.
- [x] Какие практики проектирования long-lived artifacts (docs, agent prompts, specs) минимизируют сцепление с конкретными доменными концептами — чтобы отзыв концепта стоил 1–2 файла, а не 13: (a) single source of truth и ссылки на канон вместо его цитирования; (b) layer separation с точной границей «state semantics — product, state visual realization — UI»; (c) функциональное имя слота вместо собственного (`progress bar` вместо визуального описания конкретного элемента); (d) агент-промты — такой же long-lived слой, в нём те же правила применимы.
- [x] Должен ли ревьюер блокировать PR на основании правила, которого нет в каноне (кодстайл / архгайды), — и почему. Глубинный механизм: блок без цитируемого канона разрушает контракт «автор — ревьюер» (нет воспроизводимости между ревьюерами), создаёт силент-дрейф между тем, что енфорсится, и тем, к чему у автора есть доступ. Корректный путь: SUGGESTION/nit допустим без канона (мягкий сигнал); REQUIRED требует строки канона; если правило ощущается как настоящее (scale × frequency × pain) — эскалация на обновление канона ДО следующего применения, иначе invented-rule-проблема просто переносится с ревьюера на коллектив.
- [x] Как Compose Multiplatform рендерит на каждой из трёх платформ и почему это даёт «single visual language». Compose владеет composition → measure → layout везде одинаково, вниз делегирует только draw. Бэкенды РАЗНЫЕ: Android рисует через родной `android.graphics.Canvas` (аппаратное ускорение через RenderNode; Skia внутри ОС, но НЕ Skiko), Desktop и iOS — через Skiko (Skia for Kotlin). Визуальное парити берётся НЕ из единого рендерера, а из того, что Compose сам рисует каждый пиксель кастомными примитивами вместо инстанцирования нативных OS-компонентов (противоположность нативным тулкитам из Option 2 ADR). Риск «iOS младше Android» — про интеграционный слой (UIKit interop, ввод текста, accessibility, momentum-скролл, run-loop), не про Skia: рисующее ядро зрелое везде, молодая именно платформенная склейка на Apple.
- [x] Зачем нужен enforcement probe (положить заведомо битый артефакт, прогнать чек, убедиться, что упал с ожидаемым сообщением, удалить), если есть юнит-тесты самого правила или мы используем заведомо рабочий инструмент типа lychee. Юнит-тест покрывает логику правила («битая ссылка → ошибка»); probe валидирует wiring — что правило/инструмент действительно подключён в реальную цепь (ServiceLoader для ktlint custom rule, gradle task graph, CI workflow step, git hook chain) и что exit code пропихнут наружу без проглатывания shell-обёрткой. Это место «всё по отдельности работает, а вместе — нет»: probe бьёт через ту же дверь, что и реальный нарушитель. Принципиально это валидация negative case вместе с positive — нужно убедиться, что чек не только не срабатывает, когда всё правильно, но и срабатывает, когда есть реальное нарушение.
- [x] Почему рассинхрон Kotlin / Compose Multiplatform версий между KMP-библиотекой и приложением — настоящий риск, а не паранойя. Две сцепленные оси: (1) Compose Compiler — это Kotlin compiler plugin, version-locked к Kotlin; он переписывает `@Composable` в стейт-машину с инжектированным `$composer: Composer`, битмаской `$changed`, group keys для slot table — то есть calling convention функции определяется Kotlin'ом, что её компилировал; library публикует уже скомпилированные `.klib`, потребитель линкуется по символьному имени, но битмаски/groups могут разъехаться → линкуется, но рекомпозиция врёт (hard-to-grep failure). (2) `androidx.compose.runtime` — единственная зависимость в графе Gradle; библиотека на CMP-alpha форсит весь app на alpha-runtime, который ломается между alpha-дропами; нельзя «alpha для шитов, stable для экранов» — Gradle resolution выбирает одну версию. Поэтому «match Kotlin, требует CMP-alpha» (2.x) хуже, чем «match CMP, отстаёт на Kotlin minor» (1.x): Kotlin-lag — soft failure, проверяется за минуты; CMP-alpha — заложник чужого release cycle на весь app.
- [x] Какие свойства `synchronized`-блока (с `Any()`-lock или `NSLock`), `kotlinx.coroutines.sync.Mutex.withLock`, и atomic ops (`AtomicBoolean`/`ConcurrentLinkedQueue`) делают каждый из них fit или unfit в трёх контекстах: (a) suspend `start()` с suspend-prelude перед мутацией; (b) non-suspend `stop()`/`republish()` из lifecycle hook'а с уже отменённым scope'ом; (c) NSD background callback на JVM-потоке вне корутинного контекста. Ключевые свойства: blocking-vs-suspending (synchronized паркует OS thread → стопает другие корутины на shared pool; Mutex суспендится); cancellation-awareness (Mutex реагирует на cancel ожидания, synchronized — нет); reentrancy (synchronized reentrant, Mutex НЕ reentrant — повторный `withLock` из той же корутины = deadlock); fairness (Mutex FIFO, JVM `synchronized` — unfair); и context requirement (Mutex требует coroutine context, atomic ops — нет). Mixing Mutex + atomics safe HERE because the two state pieces (lifecycle fields vs `resolving`+`resolveQueue`) DISJOINT — никогда не мутируются вместе, нет транзакции, требующей атомарности по обоим; если бы пересеклись — всё пришлось бы свести под один primitive. Atomics работают НЕ потому что «одно property», а потому что нужные операции (CAS, poll) сами атомарны и композируются без mutex-секции. В NSD callback'е выбор atomic вместо `runBlocking { mutex.withLock {} }` — потому что (i) блокирует Bonjour/NSD-демона до return из callback, (ii) теряет cancellation-awareness Mutex'а, превращая его в дорогое `synchronized`. `runBlocking { stop() }` в onDestroy валиден потому что lifecycleScope к этому моменту уже отменён — `launch` туда no-op, а teardown должен завершиться синхронно или громко упасть.
- [x] Как в Compose Multiplatform реализовать accessibility-требование «реанонсировать ту же копию баннера при каждом повторном тапе по той же busy-карточке» — учитывая, что `Modifier.semantics { liveRegion = LiveRegionMode.Assertive }` под капотом мапится в `AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED`, и фреймворк диспатчит событие **только когда значение узла поменялось**. Identical-copy второй раз — узел не «изменился», TalkBack/VoiceOver молчат. `LaunchedEffect(bannerCopy)` повторный тап не поймает (key идентичен), `StateFlow<String>` — тоже conflated по `equals`. Решение: триггер должен быть **событием** (sequence number / UUID в `SharedFlow<UiEvent>`), не состоянием; обход live-region heuristic через платформенный bypass — Android: `LocalView.current.announceForAccessibility(text)` в `LaunchedEffect(tapEvent)`, iOS: `UIAccessibility.post(.announcement, argument: text)` через `expect/actual`. Граница `StateFlow` (состояние, conflated) vs `SharedFlow`/`Channel` (события, без conflation) — именно та, где UI-копия и accessibility-анонс расходятся: баннер сам по себе — state, тап-факт — event.
- [x] Что делает `combine` на нескольких `StateFlow` под капотом и какие гарантии (или их отсутствие) это даёт. Внутри — `channelFlow`, по корутине на каждый upstream, массив-снапшот последних значений; на новое значение из любого upstream — эмиссия n-ки вниз. Dispatcher не меняется — downstream работает в контексте collector'а (или `launchIn`-scope'а). Per-upstream rendezvous-буфер ёмкости 1: если upstream быстрее downstream, промежуточные значения этого upstream'а **теряются**, downstream увидит только последнее. В сочетании со `StateFlow` (сам conflated) и suspending-кодом в downstream-блоке — гарантированная потеря промежуточных состояний. Никогда не строить логику на «увижу каждое значение», только на «увижу самое свежее на момент готовности»; если нужна последовательность событий без потерь — `SharedFlow` с реплеем или `Channel`. Практическое следствие в `AutoSendDispatcher`: после suspending-вызова в combine-блоке `pendingFilesRepository.pending.value` перечитывается ещё раз (`pendingAfter`), потому что за время `.first()` на DataStore-пре свежий `setPending` мог прийти и conflated upstream покажет уже его. `return@combine` на этом tick'е — не отмена потока: следующий tick combine отстреливает сам штатно.
- [x] Как устроена refcount-механика на границе Kotlin/Native ARC ↔ Core Foundation при вставке `NSObject`-значения в CF-словарь через `CFBridgingRetain` → `CFDictionarySetValue` → `CFRelease`. (a) `CFBridgingRetain(nsObj)` = `__bridge_retained`: переносит владение из ARC в ручной CF, возвращает CFTypeRef с +1, ARC больше не тронет; голый `CFRetain` — просто +1 уже-CF-объекту, не переход границы. (b) Без `CFBridgingRetain` (передать `value.rawValue` напрямую) — формально может работать через toll-free bridging: retain-callback словаря сделает `CFRetain` через мост и инкрементит ARC-refcount, объект переживёт ARC-release; но тайминг хрупкий, Apple-конвенция — явный переход через `CFBridgingRetain/Release`. (c) `kCFTypeDictionaryValueCallBacks` — struct указателей на функции (`retain`=CFRetain, `release`=CFRelease, плюс copyDescription/equal/hash) переданные в `CFDictionaryCreateMutable`; они и заставляют словарь брать +1 на вставке и отпускать при удалении или собственной деаллокации. `null` вместо них = словарь хранит сырые указатели без владения. (d) Без финального `CFRelease(cfValue)` — утечка: у нас две +1 (наша из `CFBridgingRetain` и словаря из retain-callback), словарную он отдаст сам при деаллокации, нашу — нет, потому что она выдернута из-под ARC. Refcount-трассировка: после `CFBridgingRetain` cfValue=+1 (наш) → `CFDictionarySetValue` ⇒ dict-retain-callback ⇒ cfValue=+2 (наш + dict) → `CFRelease(cfValue)` ⇒ cfValue=+1 (только dict). Объект жив, пока жив dict; при `CFRelease(dict)` callback освободит value.
- [x] Как `StateFlow` реализует distinct-until-changed «у источника» и чем это отличается от оператора `.distinctUntilChanged()` на cold Flow. У `MutableStateFlowImpl` это часть `updateState` / `compareAndSet`-loop: новый value сравнивается со старым по `equals` (не `===`), и если совпали — publish подавляется на producer-стороне, до всех subscribers сразу. У cold-Flow оператора `.distinctUntilChanged()` сравнение per-collector: каждый instance оператора хранит свой `previous`; два collector'а в разных ветках пайплайна имеют независимую память. Пилфолл с mutable полями внутри `data class` value (`MutableList`, `var`): после `_state.value = oldRef.copy(list = sameMutableListRef)` + мутация in-place — `equals` со старым value совпадёт, эмиссия не выйдет, хотя «состояние сменилось». Идиоматика: в `StateFlow` класть только immutable снапшоты, мутацию делать через `.copy(list = newList)`. Отдельно держать в голове, что distinct-у-источника — **не** то же самое, что conflation для медленных collector'ов: поздний subscriber всегда видит current `.value` (replay = 1), а если он медленнее producer'а — пропустит промежуточные эмиссии (даже распознанные как различающиеся). Это две разные плоскости — equals-based suppression на publish vs rendezvous-конфляция на consume. Практическое следствие в #327: `selectedConflictPeer.update { peerId }` на повторном busy-тапе по той же карточке = CAS внутри видит equals → noop → downstream `combine` не эмитнёт, баннер не пере-рендерится. Это намеренно — same-peer повторный re-announcement отложен до платформенных actuals.


- [x] Почему `Modifier.fillMaxHeight()` на дочернем элементе внутри `LazyColumn` схлопывается в нулевую высоту (элемент-невидимка), хотя в `@Preview` рендерится верно, и какова цена обхода через `Modifier.height(IntrinsicSize.Min)` против решения через draw-фазу. Механика: `LazyColumn` — скроллящийся контейнер по вертикали, поэтому меряет каждый item с `maxHeight = Constraints.Infinity` (unbounded). `fillMaxHeight()` резолвится в `maxHeight` входящих constraints; при `Infinity` финитного размера не выходит — модификатор схлопывается, дочерний элемент без собственной intrinsic-высоты меряется в 0. В `@Preview` хост раздаёт bounded constraints, поэтому там `fillMaxHeight` заполняет реальное число — баг невидим в превью, виден только в проде (ровно эта асимметрия и пропустила его мимо первой волны ревью). `Modifier.height(IntrinsicSize.Min)` на родителе чинит, но НЕ «инвертирует порядок измерения» — он добавляет **отдельный intrinsic-query проход**: родитель спрашивает у детей их min-intrinsic высоту, фиксирует свою высоту по самому высокому ребёнку (контент-Column), и только потом меряет детей против уже-bounded высоты, в которую `fillMaxHeight` и заполняется. Цена — лишний проход измерения (для одного item в LazyColumn ограничен и дёшев, но опасен при вложенных intrinsics — они композируются мультипликативно — или если поставить на сам LazyColumn, форсируя измерение всех элементов). Более робастное решение, выбранное здесь: рисовать 3dp peer-identity полосу в `drawBehind` (в том же блоке, что и нижний бордер) через `drawRect` по `size.height` финального измерения. В draw-фазе размер уже известен и всегда корректен независимо от constraints родителя — целый класс constraint-багов становится непредставимым, а не «запатченным». Граница: `IntrinsicSize.Min` валиден (bounded, per-item, shallow), но draw-time-решение строго лучше по робастности, особенно когда баг уже один раз пробил ревью.
- [ ] (#418, отвечено неуверенно — вернуться) Почему упакованное `jpackage`-приложение падало с `NoClassDefFoundError: sun.misc.Unsafe` (а EC-ключи дали бы `NoSuchAlgorithmException`), хотя `./gradlew run` работал? Ключ: (1) Это **не** минификация библиотек (R8/ProGuard стрипает классы из app-jar'ов), а `jlink` — он собирает урезанный образ JVM-рантайма из подмножества JDK-**модулей** (JPMS: `java.base`, `java.desktop`, `jdk.unsupported`, `jdk.crypto.ec`…); `jpackage` кладёт этот приватный JRE внутрь инсталлятора, и отсутствующий модуль физически не попадает в рантайм → reflective-загрузка класса из него падает. (2) `./gradlew run` иммунен, потому что запускается на **полном системном JDK** (все ~70 модулей) и **не проходит стадию `jlink`** — она только в `createDistributable`/`packageXxx`. (3) `jdeps`/`suggestModules` — **статический** анализатор байткода: `java.management`/`java.instrument`/`jdk.unsupported` (`sun.misc.Unsafe`) имеют **прямую** ссылку в байткоде → видны (но Compose их не добавляет автоматически — `suggestModules` лишь советует, в `modules(...)` копируешь руками). `jdk.crypto.ec` (SunEC) **не** имеет байткод-ссылки: провайдер грузится через JCA по **строковому** ключу `getInstance("EC")` (service-lookup + рефлексия) — строку статический анализ не видит, поэтому добавляешь по знанию домена. Тот же слепой угол у любого `ServiceLoader`/`Class.forName`/`getInstance("…")`.
- [x] Чем `associateWith` (friend-компиляция, напр. `desktopCli associateWith main`) отличается от `dependsOn` (иерархия source set'ов, `desktopMain dependsOn commonMain`): (1) почему `internal` пересекает границу в обоих случаях, но по РАЗНЫМ механизмам — у `associateWith` через friend-paths (`-Xfriend-paths` + разрешение звать mangled `internal`-имена), у `dependsOn` потому что source set'ы сливаются в ОДНУ компиляцию на таргет (буквально один модуль); (2) почему только `dependsOn`-иерархия поддерживает `expect/actual` (матчинг деклараций внутри одной таргет-компиляции), а `associateWith` — нет; (3) почему успешная компиляция через `associateWith` НЕ гарантирует наличие класса в рантайм-артефакте — compile-classpath/friend-paths ≠ packaging, поэтому `cliJar` обязан явно бандлить `mainCompilation.output`, иначе чистый компайл и `NoClassDefFoundError` в рантайме.
- [x] Есть ли самоподкрепляющийся контур в системе, где `close-issue` ставит `size:` лейбл, сверяясь с фактической нагрузкой ревью данного PR, а `grooming` пере-выводит сами бэнды как среднюю нагрузку на размер по тем же закрытым задачам, — и если есть риск тавтологии, что в схеме его удерживает? Петля формально существует (метка и калибровка крутятся вокруг одной величины), но не замкнута: (1) bootstrap-baseline — 96 размеченных задач выставлены вручную по структурным признакам *вне* петли, бэнды S 0.6 / M 3.3 / L 11.7 сошлись на этой выборке, разовый выброс их не утянет; (2) внешний сигнал — число ROOT-комментов берётся из реакции человека на код, петля его не контролирует, поэтому это не «модель учит модель», а внешний якорь; (3) defer-only update — бэнды переписываются осознанным шагом в grooming и видны через PR-diff, любая аномалия отлавливается до закрепления. Остаточный риск: дрейф среднего при долгой монотонной серии одного размера (M подползёт вверх, новая реально-M покажется «лёгкой») и self-confirmation на границе (механически ставить S по «~1 коммент»). Защита — рубрика-как-метод (приор по типу + структурные множители) в `create-issue` / `close-issue`, плюс git-видимость бэндов: внезапный скачок M 3.3 → 6.0 — сигнал на ручной разбор, а не «обнови и забудь».
- [ ] Как в сессиях после пейринга проверяется, что на том конце — доверенное устройство, если публичный ключ и его fingerprint перехватываемы в LAN? Разделить две роли: (a) одноразовое SAS-сравнение — отвечает на «правильный ли это публичный ключ?», человек подтверждает идентификационные ключи на первом контакте (канал ещё не аутентифицирован — пина нет), на совпадении каждая сторона пинит публичный ключ пира; больше не повторяется; (b) per-session механизм — отвечает на «владеет ли пир приватным ключом, парным запиненному публичному?»: устройство подписывает свежие данные TLS-хендшейка приватным ключом, пир проверяет подпись по запиненному публичному (`CertificateVerify` в TLS 1.3), свежесть данных рубит replay. Секрет — приватный ключ в Keychain/Keystore/DPAPI, никогда не на проводе; публичный ключ/fingerprint — лишь идентификаторы, их перехват имперсонации не даёт (подпись без приватного ключа не подделать). Аналогия — SSH `known_hosts`.
- [x] Почему review-wave — фан-аут ~13 узких ревью-агентов, а не один общий: механизм, цена с ростом N, тест на отдельный агент. **Механизм** — context isolation: узкий чеклист в своём окне не размывается и проверяет ось глубже. **Цены с ростом N**: токены (~30–40k floor на старт каждого агента) и boundary-maintenance (границы «what you do NOT check» между агентами). **Тест на отдельный агент**: владеет ли проверкой, которой не делает никто другой и которой нужен свой контекст. `review-ux-brief` проходит (5/7 проверок — уникальное UX-суждение); `review-glossary` формально нет (doc-conformance как guides), но держится на реюзе в `create-issue`.
- [x] Как устроены WindowInsets в Compose и почему замена корневого `safeContentPadding()` на `safeContent.only(WindowInsetsSides.Vertical)` (а не `safeDrawing`) — единственная корректная. (1) Состав семейств: `safeDrawing` = `systemBars` ∪ `displayCutout` (отрисованный системный UI), `safeGestures` = system/home-gesture зоны, `safeContent` = `safeDrawing` ∪ `safeGestures`. На жестовой навигации `safeGestures.bottom` (зона перехвата свайпа) ШИРЕ `safeDrawing.bottom` (тонкая nav-полоска), поэтому `safeContent.bottom` > `safeDrawing.bottom` — переключение на `safeDrawing` молча урезает нижний отступ, и нижняя интерактивная строка попадает в зону, где свайп перехватывает ОС. (2) Consumption: `windowInsetsPadding` не только добавляет padding, но и вычитает потреблённое из того, что видят вложенные `windowInsetsPadding` (анти-двойной-отступ); старый `safeContentPadding` потреблял все 4 стороны, новый — только вертикаль, так что горизонталь больше никем не потребляется. В этом приложении потомки инсеты не читают (фиксированный `spacing.lg`), поэтому горизонтальные инсеты просто не применяются никем — это by design и даёт flush-look. (3) Desktop no-op: у окна нет системных зарезервированных областей, `safeContent`/`safeDrawing` резолвятся в ноль, `.only(...)` от нуля — тоже ноль.
- [x] Почему main-thread оркестратора — 70–95% стоимости задачи, через cost-модель prompt-caching Anthropic, и почему вынос чтения 30K-дока в сабагента дешевле, чем чтение в свой тред. Три множителя несут весь аргумент: `cache_read = 0.1× input` (тёплое перечитывание истории в 10 раз дешевле свежего ввода — поэтому таскать 200K-контекст по ходам вообще выживаемо), `cache_create = 1.25× input` (запись кэша — премия 25% над свежим вводом), отсюда idle-обрыв `cache_create / cache_read = 12.5×`: после простоя >5 мин (TTL ephemeral-кэша) весь накопленный контекст пере-биллится по 12.5× тёплой ставки, а не «чуть дороже». Модель stateless, транскрипт append-only → ход N пере-отправляет ходы 1…N-1 целиком. Вынос в сабагент дешевле НЕ из-за per-token ставки, а из-за частоты: 30K-док, прочитанный в тред оркестратора, оплачивается как cache_read на КАЖДОМ оставшемся ходу прогона (0.1× × 30K × N), а в короткоживущем сабагенте — оплачивается ОДИН раз, и наверх едет лишь ~1K дайджест. «Заплачено однажды» против «заплачено каждый ход до конца прогона» — множитель на N и есть выигрыш. Контр-цена выноса: спавн тащит ~30K преамбулы (системный промпт + схемы тулов), поэтому офлоадить стоит только достаточно большой ридинг.
- [x] (#417) Чем отличаются роли `UIFileSharingEnabled` и `LSSupportsOpeningDocumentsInPlace` на iOS, есть ли у них рантайм-диалог, и что определяет имя/иерархию папки приложения в Files. `UIFileSharingEnabled=YES` — единственный load-bearing ключ для #417: делает песочничную `Documents/` видимой и редактируемой в Files (и по кабелю в Finder); «открываемость пользователем» обеспечивает именно он. `LSSupportsOpeningDocumentsInPlace=YES` — про **входящее** направление: когда другое приложение/система открывает документ **в** наше приложение, документ приходит как оригинал «на месте» (security-scoped URL), а не копией в `Documents/Inbox/`; в паре с `UIFileSharingEnabled` задаёт in-place-семантику для файлов в экспонированной папке. Оба — **статические capability-флаги LaunchServices без рантайм-диалога** (нет `NS…UsageDescription`, нет `requestAuthorization`-API — в отличие от Photos/Camera/Location). Сегодня второй ключ **латентен**: эффект включается, только когда приложение объявляет `CFBundleDocumentTypes` и обрабатывает `application(_:open:)` — Tether этого не делает, open-in целью не является, так что для DoD #417 он belt-and-suspenders. Имя записи под «On My iPhone → …» = `CFBundleDisplayName` → fallback `CFBundleName` (= `PRODUCT_NAME` = `Tether`); отдельного `CFBundleDisplayName` в plist нет, поэтому берётся `CFBundleName`. Система показывает песочничную `Documents/` **под именем приложения**, поэтому файлы прямо в `Documents/` дают одноуровневый `On My iPhone → Tether/`, а подпапка `Documents/Tether/` дала бы дублирующий `Tether → Tether` — ради чего корень загрузок и перенесён с `Documents/Tether/` на `Documents/`. Отдельно: temp-copy перед отправкой — **только для фото** (`PHPicker` не отдаёт читаемый security-scoped URL); файлы/папки из `UIDocumentPickerViewController(asCopy=false)` стримятся из оригинала на месте, и этот ключ к отправке (#194) отношения не имеет.
- [x] Почему вывод `fingerprint = SHA-256(X.509 SPKI публичного ключа)` безопаснее случайного per-install fingerprint — и что это НЕ даёт. Три слоя, и этот PR строит только нижний. (1) **Binding/commitment (этот PR):** раньше `pubkey` и `fingerprint` — два независимых поля в `/hello` и mDNS TXT, можно анонсировать любую их пару; теперь fingerprint — криптографическое обязательство к ключу: получатель пересчитывает `SHA-256(SPKI(полученный pubkey))` и режет несовпадение, а collision-resistance не даёт подобрать другой pubkey под тот же fingerprint. Это **целостность, не аутентификация** — атакующий генерит свой keypair и анонсит согласованную пару `(pubkey', H(pubkey'))`, проверка пройдёт. Поэтому derivation — **необходимое, но не достаточное** условие, пол под двумя верхними слоями: (2) **SAS** (пейринг) — «правильный ли это ключ?», человек сравнивает строку, производную от ключа; работает только если видимый fingerprint ДЕНОТИРУЕТ запиненный ключ (иначе сравнение через комнату ничего не проверяет про то, что пинит TLS) — это и есть смысл строки threat-model «mandatory SAS; visible fingerprint»; (3) **TLS `CertificateVerify`** (per-session) — «владеет ли пир приватным ключом, парным запиненному публичному». (б) **Divergence routing↔trust:** роутинг/discovery ключит пиров по `Device.fingerprint` (self-suppression, `PeerIdentity`), trust — по `publicKey`; два независимо персистимых значения дрейфуют (Keychain восстановлен, DataStore нет) → пир «известен routing'у, неизвестен trust'у». Derivation = единый источник истины (ключ): fingerprint не может разойтись с ключом, из которого выведен (single-source-of-truth / no-mirror-state). Конкретная routing-атака, которую это закрывает ещё до пейринга/TLS: анонс `(чужой_pubkey, твой_fingerprint)` занимал твой `PeerIdentity`-слот; теперь анонс обязан нести `fingerprint == SHA-256(чужой_pubkey) ≠ твой` → отбивается. Итог: PR делает fingerprint не «доверенным», а «честным» (не может соврать, какой ключ он обозначает) — предпосылка, позволяющая SAS и TLS сделать его доверенным.
- [x] (#224) Почему очистку staged-копий расшаренных файлов перенесли с `close()` на отдельный сигнал «доставка подтверждена», и как устроена гарантия «ровно один раз». (1) `close()` непригоден как триггер удаления: `PeerFileSender.send` зовёт `source.close()` в `finally` — на КАЖДОЙ попытке, и на успехе, и на провале; а `BatchSender` идёт сквозь пофайловые ошибки (`i++; continue`), поэтому после первого прохода закрыты все источники → каталог удалился бы до возможного retry. Это НЕ про security-scoped URL — про per-attempt-close + continue-past-failure. Фото из пикера иммунны: `LazyPhotoFileSource` ре-материализуется из `NSItemProvider` на каждом `openReadChannel`, у расшаренного файла ре-импортировать неоткуда — staged-копия единственная. (2) Хук повесили на объект-обёртку (`ConfirmableFileSource` + `(src as? ConfirmableFileSource)?` в `BatchSender`), а не в контракт `FileSource` (метод несли бы 4 реализации впустую; single-impl marker-интерфейс запрещён DI rule 5) и не по имени/пути файла — это закрывает by construction два класса багов: (a) контаминация — одноимённый файл в ЧУЖОЙ передаче удалил бы staged-батч; (b) тайминг — на момент drain'а ещё нет ни пира, ни transfer'а, сопоставлять не с чем; object-identity снимает оба. (3) «Ровно один раз на доставке» = два слоя: (a) гейтинг — `confirmDelivered()` зовётся только на Done-переходе `BatchSender`, недостижимом на любой ветке fail/cancel/drop (все `continue`/`return` раньше) → HTTP-200 only; (b) идемпотентность — `AtomicBoolean.compareAndSet(false,true)` гарантирует один вызов колбэка, даже если Done-точку дёрнут повторно. На retry не ломается: упавшая попытка до Done не доходит, а доставленный файл исключён из `onRetryOutbound` (`failedNames` only) и всё равно защищён CAS. Удаление каталога — per-batch `AtomicInt remaining`: декремент на каждый confirm, `deleteStagedBatch` при нуле.
