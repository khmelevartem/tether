# Чеклист подготовки к собеседованию
### Senior Android / KMP Developer · 2026

---

## Блок 1 — Kotlin

### Система типов
- [ ] `Nothing` vs `Unit` vs `Void` — чем отличаются, где применяются
- [ ] Платформенные типы (`String!`) — откуда берутся, чем опасны
- [ ] `@Nullable` / `@NotNull` в Java и как Kotlin их интерпретирует
- [ ] `as` vs `as?` — когда бросает исключение

### Классы и объекты
- [ ] `data class` — что генерирует компилятор: `equals`, `hashCode`, `toString`, `copy`, `componentN`
- [ ] `copy` — поверхностная копия, что с этим делать
- [ ] `value class` (`@JvmInline`) — боксинг/анбоксинг, когда vs `data class`
- [ ] `data object` (Kotlin 1.9+) — чем отличается от `object` и `data class`
- [ ] `sealed class` vs `sealed interface` — когда что, exhaustive `when`
- [ ] `object` — singleton, companion object, thread-safety, порядок инициализации
- [ ] `inner class` vs вложенный класс — захват ссылки, утечки памяти
- [ ] Делегирование через `by` — `lazy`, `observable`, `vetoable`, кастомные делегаты

### Функции
- [ ] `inline` — что делает компилятор, когда применять, оверхед лямбд
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
- [ ] `suspend` под капотом — CPS, state machine, что компилятор делает с функцией
- [ ] `Continuation<T>` — интерфейс, как реализуется возобновление
- [ ] Корутины vs потоки — почему «легковесные», как маппируются на потоки
- [ ] `CoroutineContext` — `Job`, `Dispatcher`, `CoroutineName`, `CoroutineExceptionHandler`, наследование
- [ ] `suspend` vs `blocking` — чем `delay` отличается от `Thread.sleep`

### Structured concurrency
- [ ] Что такое structured concurrency — scope, parent-child, когда parent завершается
- [ ] `Job` vs `SupervisorJob` — поведение при ошибке дочернего корутина
- [ ] `coroutineScope` vs `supervisorScope` — разница в обработке исключений
- [ ] Отмена: `cancel()`, `CancellationException`, `isActive`, `ensureActive`, `yield`
- [ ] `NonCancellable` — зачем, как использовать в `finally`
- [ ] Исключение в `async` без `await` — куда девается, когда выбрасывается

### Dispatchers
- [ ] `Main` / `IO` / `Default` / `Unconfined` — когда что, размеры пулов
- [ ] `IO` vs `Default` — IO расширяется до 64, Default = кол-во CPU
- [ ] `withContext` — создаёт ли новую корутину? (нет — switch контекста)
- [ ] `MainCoroutineDispatcher.immediate` — что делает, зачем
- [ ] Кастомные диспетчеры через `Executors.asCoroutineDispatcher()`

### Exception handling
- [ ] `CoroutineExceptionHandler` — только для `launch` в root scope, не работает с `async`
- [ ] `launch` vs `async` — первый роняет сразу, второй хранит в Deferred
- [ ] Propagation — как исключение идёт вверх по иерархии
- [ ] `try-catch` внутри корутины — работает только в том же scope

### Flow
- [ ] Cold flow vs Hot flow — новый поток на каждого collector vs один общий
- [ ] `StateFlow` vs `SharedFlow` — replay, conflation, начальное значение
- [ ] `StateFlow` под нагрузкой — механизм conflation, 100 collectors + 120 updates/sec
- [ ] `flatMapLatest` vs `flatMapMerge` vs `flatMapConcat` — параллелизм
- [ ] `combine` vs `zip` — эмит при любом изменении vs ждёт пару
- [ ] `debounce`, `throttleFirst` — поиск с задержкой
- [ ] `buffer` / `conflate` / `collectLatest` — backpressure
- [ ] `callbackFlow` — интеграция callback API
- [ ] `channelFlow` — отличие от `flow { }`
- [ ] Отмена Flow — автоматически при отмене корутины-collector

### Channels
- [ ] `Channel` vs `Flow` — когда что
- [ ] `produce` builder
- [ ] Почему `BroadcastChannel` deprecated и что вместо (SharedFlow)

---

## Блок 3 — Android Core

### Lifecycle
- [ ] Activity / Fragment lifecycle — rotation, process death, multi-window
- [ ] `onSaveInstanceState` vs `ViewModel` — что переживает что
- [ ] `savedStateHandle` в ViewModel — как работает при process death
- [ ] Разница `onStop` vs `onPause` в multi-window

### Process death & State
- [ ] Что такое process death — когда, что теряется, что остаётся
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
- [ ] `WorkManager` vs `ForegroundService` vs `AlarmManager` — когда что
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
- [ ] `LaunchedEffect` / `SideEffect` / `DisposableEffect` — когда что
- [ ] `rememberCoroutineScope` — зачем, когда vs `LaunchedEffect`
- [ ] `produceState` — bridge от non-Compose к State

### Архитектура
- [ ] State hoisting — паттерн, зачем
- [ ] `CompositionLocal` — когда применять, чем опасен
- [ ] Navigation в Compose — `NavController`, `NavHost`, back stack, deep links

### Performance
- [ ] Как профилировать recomposition в Android Studio
- [ ] Overdraw в Compose — как избежать
- [ ] `key()` — когда нужен в списках
- [ ] Baseline Profiles для Compose — как ускоряет cold start

---

## Блок 5 — Архитектура

### Паттерны
- [ ] Clean Architecture — слои, направление зависимостей, зачем UseCase
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
- [ ] Koin — отличие от Hilt, service locator vs DI
- [ ] Без фреймворка (твой опыт) — manual DI, когда оправдан в KMP

---

## Блок 6 — KMP

### Механизм
- [ ] `expect` / `actual` — как работает, ограничения
- [ ] Alternatives to expect/actual — interfaces + DI, когда что
- [ ] Структура модулей: `commonMain` / `androidMain` / `iosMain` / `desktopMain`

### Kotlin/Native
- [ ] Старая memory model (заморозка, `@Frozen`) vs новая (1.7.20+)
- [ ] Как работает GC в Kotlin/Native
- [ ] `suspend` функции в iOS — Swift Export, `async/await` bridge

### Библиотеки
- [ ] Ktor Client — multiplatform HTTP
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
- [ ] `Fake` vs `Mock` — когда что, архитектурные последствия
- [ ] `Turbine` — тестирование Flow, задержки, cancellation
- [ ] `Compose Testing` — семантическое дерево, `assertIsDisplayed`, `performClick`
- [ ] `TestCoroutineDispatcher` / `UnconfinedTestDispatcher` — контроль времени в тестах
- [ ] Integration tests vs Unit tests — что где тестировать

---

## Блок 9 — Java (JVM-контекст)

- [ ] GC — как работает, поколения, когда срабатывает
- [ ] Heap vs Stack — что хранится где
- [ ] ClassLoader — как загружается класс, parent delegation
- [ ] `synchronized` — monitor, reentrant, оверхед
- [ ] `volatile` — видимость, не атомарность
- [ ] `happens-before` — гарантии JMM
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

- [ ] Offline-first приложение — sync стратегия, conflict resolution
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
