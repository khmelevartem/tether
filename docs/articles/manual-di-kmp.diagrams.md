# Diagram specs — Manual DI in KMP article

Five inline figures (1–5) are referenced by both `manual-di-kmp.ru.md` and `manual-di-kmp.en.md`, plus a cover/hook image (6) that the publishing platform overlays with the title (not linked inline). Filenames are language-neutral (the diagrams carry only code identifiers and arrows), so one set serves both articles. Two of the inline figures are direct descendants of the diagrams in the original `di-structure.md` (`containers.png`, `api-impl.png`, `di-structure.png`) and can be redrawn from those.

Style guidance, consistent across all five:

- Dark canvas, rounded boxes, monospace labels — matches the look of the originals.
- Colour roles, used the same way everywhere: **yellow** = the consuming application, **green** = public surface, **purple** = internal / platform-specific, **blue/teal** = neutral structural containers.
- Only the identifiers that appear in the article prose. No real project class names.
- Each box label is `code` font; group headers are plain.

---

## 1. `manual-di-source-set-hierarchy.png`

**Referenced in:** "The container hierarchy mirrors the source-set hierarchy."

**Shows:** the container tree drawn side by side with the source-set tree, so the reader sees the one-to-one mirror. Descendant of the original `containers.png`, but generalised to the full tree rather than a single Android/iOS pair.

**Layout:** two parallel columns, left = source sets, right = containers, with thin horizontal "mirrors" links between the matching rows.

```
  source set            container
  ──────────            ─────────
  commonMain    ←→      AppContainer
  ├ jvmMain     ←→      ├ JvmAppContainer
  │ ├ androidMain ←→    │ ├ AndroidAppContainer
  │ └ desktopMain ←→    │ └ DesktopAppContainer
  └ appleMain   ←→      └ AppleAppContainer
    └ iosMain   ←→        └ IosAppContainer
```

**Annotation (one caption line):** "Each layer adds only what its source set can see."

**Colour:** structural blue/teal boxes throughout; the common row slightly highlighted to mark it as the root.

---

## 2. `manual-di-android-provider.png`

**Referenced in:** "The Provider pattern for Android."

**Shows:** why framework-managed components can't take the container in the constructor, and how they reach it through the `Application`.

**Layout:** three boxes.

- Top-left (yellow): `MyApp : Application, AppContainerProvider` — note inside: "owns the container, built lazily."
- Top-right (blue/teal): `AppContainer` — the owned instance. Solid arrow from `MyApp` → `AppContainer` labelled "constructs & owns."
- Bottom (purple, framework-managed): `MyService : Service` with "empty constructor — required by the framework." Dashed arrow from `MyService` up to `MyApp` labelled `(application as AppContainerProvider).container`.

**Annotation:** "Only framework-managed classes reach the container this way. Plain classes get it through the constructor." Optionally draw a fourth box (green) `PlainClass(dep: SomeDep)` with a solid constructor arrow, to contrast.

---

## 3. `manual-di-api-impl.png`

**Referenced in:** "Splitting into Api and Impl."

**Shows:** the module dependency wiring — who depends on Api, who depends on Impl. Direct descendant of the original `api-impl.png`.

**Layout:** two grouped regions, **Application** (yellow) on the left, **Library** (green) on the right.

- Library region contains two stacked boxes: `Lib-Api` (green, top) and `Lib-Impl` (green, bottom). Arrow `Lib-Impl` → `Lib-Api` ("depends on").
- Application region contains: `App-DI-module` (the one module that may see Impl) and two or more `app component` boxes.
- Arrow `App-DI-module` → `Lib-Impl` (the single allowed dependency on Impl).
- Arrows from each `app component` → `Lib-Api` only (curved, green), showing components never touch Impl.

**Annotation:** "Only the DI module depends on Impl. Everything else depends on Api."

This is essentially the original `api-impl.png` with class names stripped to roles.

---

## 4. `manual-di-public-internal.png`

**Referenced in:** "Public and internal containers."

**Shows:** the container forking by visibility — public façade vs internal extension — and who reads which.

**Layout:** one large container box with two nested regions.

- Outer box: `LibInternalContainer` (purple header).
- Nested inside: `LibPublicContainer` (green header) containing a field `val someRepository: SomeRepository` (green).
- Outside the nested region but inside the outer box: `val someInternalService: SomeInternalService` (purple).
- Two reader arrows on the left margin: a yellow `App` arrow pointing at the **green** region ("reads via public interface"); a purple `library-internal class` arrow pointing at the **whole outer box** ("reads via internal interface").

**Annotation:** "The app sees green. The library's own classes see purple too."

**Colour:** mirrors the legend used in the original `containers.png` (green = public, purple = internal).

---

## 5. `manual-di-init-order.png`

**Referenced in:** "Initialization order."

**Shows:** the five-step assembly sequence of a library container. Direct descendant of the original `di-structure.png`, generalised.

**Layout:** four actor columns, numbered arrows between them, matching the five steps in the prose.

- **Application** (yellow): box `LibConfigContainer impl` and a result slot `val container: LibInternalContainer`, exposing two faces — `lib: LibPublicContainer` (green) and `internalLib: LibInternalContainer` (purple).
- **createLibContainer(config)** (green): the Impl-module factory function — no longer a `LibModule` interface. Shows `build` and `return container`.
- **LibPlatformContainer** (purple): platform-specific internal entity.
- **LibInternalContainer / LibPublicContainer** (purple outer, green inner): the assembled container with `publicDependency` (green) and `internalDependency` (purple) fields.

**Numbered arrows (must match prose steps 1–5):**

1. `Application` → `createLibContainer(config)` — passes the config.
2. `createLibContainer` → builds `LibPlatformContainer`, then → assembles the container.
3. assembled container → returned to `Application`, stored as `val container: LibInternalContainer`.
4. `Application` exposes two faces: `lib` (public) outward via `LibProvider`, `internalLib` (internal) inward via the internal provider.
5. consumed: an external app component reads the **public** face; a library-internal class reads the **internal** face.

**Annotation:** colour legend box in the corner — yellow = application, green = public, purple = internal.

This is the original `di-structure.png` with the colour semantics preserved and class names replaced by roles.

---

## 6. `manual-di-kmp-hook.png` — cover / hook image

**Referenced as:** the article's lead/cover image (Habr cover, Medium hero). Evocative, **not** a diagram — pure visual hook, no semantic load.

**Concept:** "DI you can see — assembled by hand." A pair of hands wiring a **transparent / glass device** whose insides are fully visible: every cable and connector lit and traced cleanly from one block to the next, nothing hidden. The single idea to land in one glance: clarity and craftsmanship, no black box.

**Contrast cue (subtle, background, optional):** a closed black box with a faint glowing "?" where a competing "magic" approach would hide the same wiring — kept dim so it doesn't compete with the lit hands.

**Mood:** craftsmanship, clarity, calm focus. Not corporate, not cartoonish.

**Palette:** dark background; live wires in Kotlin-ish purple/orange; warm key light on the hands. Matches the dark canvas of the five diagrams.

**Format:** wide cover ratio (16:9, or Habr's cover crop). **No text in the image** — the title is overlaid by the platform.

**Style:** clean semi-realistic 3D render or editorial illustration. Avoid stock-photo literalness and avoid flat clip-art.

---

## Reuse note

If redrawing from scratch is too much, figures 3, 4, and 5 can be produced by editing the originals (`api-impl.png`, `containers.png`, `di-structure.png`) — replace each real class name with its role label from the boxes above and keep the existing layout and colours. Figures 1 and 2 are new and have no original to edit from.
