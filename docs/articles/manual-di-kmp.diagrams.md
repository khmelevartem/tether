# Diagram specs — Manual DI in KMP article

Four inline figures (1–4) are referenced by both `manual-di-kmp.ru.md` and `manual-di-kmp.en.md`, plus a cover/hook image (5) that the publishing platform overlays with the title (not linked inline). Filenames are language-neutral (the diagrams carry only code identifiers and arrows), so one set serves both articles. Each picture is followed inline by a short `> ...` caption in the article body.

Style guidance, consistent across the inheritance / api-impl / provider-pattern figures (1, 3, 4):

- Dark canvas, rounded boxes, monospace labels.
- Colour roles, used the same way everywhere: **yellow** = the consuming application, **green** = public surface, **purple** = internal (library-visible, not exposed beyond), **blue/teal** = platform-specific or structural-intermediate containers.
- Only the identifiers that appear in the article prose. No real project class names.

Figure 2 (composition) uses colour differently — see its own section.

---

## 1. `manual-di-inheritance.png`

**Referenced in:** "Public and internal containers."

**Shows:** the visibility cascade as **inheritance**, not composition. The inner rectangles are the parents; the outer rectangles are subclasses that add more fields. Same shape on both sides:

- The **config container** has two tiers — public-visible fields and platform-specific handles.
- The **assembled container** has three tiers — public, internal, and platform-specific.

The single composition arrow ties the two together: the assembled container takes the config in its constructor.

**Layout:** two main groups + a small legend, left to right.

- **Left — `AndroidConfigContainer` (teal):** subclass of `ConfigContainer`.
  - Nested `ConfigContainer` (green): parent. Holds `val commonConfig: CommonConfig`.
  - Outside the green region but inside the teal envelope: `val androidConfig: AndroidConfig`, `val androidApplication: Application`.
- **Right — `AndroidContainer` (teal):** subclass of `InternalContainer`, which is itself a subclass of `PublicContainer`.
  - Innermost `PublicContainer` (green): the topmost ancestor. Holds `val someRepository: SomeRepository`.
  - Middle `InternalContainer` (purple): subclass of `PublicContainer`. Adds `val someInternalService: SomeInternalService`.
  - Outermost `AndroidContainer` (teal): subclass of `InternalContainer`. Adds `val androidDependency: AndroidDependency`, `val androidNotifier: AndroidNotifier`.
- **Arrow (red):** `AndroidConfigContainer` → `AndroidContainer`, labelled "into constructor."
- **Legend:** colour → role mapping.

**Inline caption (article body):** "Inner rectangles are the parents. AndroidContainer extends InternalContainer, InternalContainer extends PublicContainer. Config is fed in through the constructor."

---

## 2. `manual-di-composition.png`

**Referenced in:** "Composing the container."

**Shows:** the orthogonal pattern to figure 1 — composition by axes. `AppContainer` is the assembly target; each independent axis (platform, UI, monetization, config) is factored out as its own fragment container and passed in via the constructor. Several interchangeable alternatives per axis demonstrate the "swap one fragment, get a different build" point.

**Colour convention here is local — it groups axes, not roles.** Each slot inside `AppContainer` is coloured by its axis, and the active alternative on the outside shares that colour:

- Monetization axis (purple): `B2BContainer` (active) / `B2CContainer`.
- UI axis (cyan): `OldUiContainer` / `NewUiContainer` (active).
- Platform axis (green): `AndroidPlatformContainer` / `IosPlatformContainer` (active) / `DesktopPlatformContainer`.
- Config axis (yellow): `appConfigContainer` slot in the colour of the outer `AppContainer`.

Inactive alternatives are drawn uncoloured — visually dimmed against the active one.

**Layout:** `AppContainer` group (yellow) in the centre, holding four slot fields (one per axis). Each axis's fragments cluster off to one side of the group with an edge into the slot — platform on the left, UI on the right, monetization above, config slot on the bottom of the assembly with its alternatives implied.

**Inline caption (article body):** "Each axis has several interchangeable alternatives; the one picked for this concrete build is shown in colour (here — iOS, B2B, new UI). A variant's colour matches its slot; colour here marks the axis, not the role."

**Note:** this figure deliberately diverges from the global colour legend. Figures 1, 3, 4 use colour to mark visibility tier / role; figure 2 uses colour to mark axis. The caption flags this.

---

## 3. `manual-di-api-impl.png`

**Referenced in:** "Splitting into Api and Impl."

**Shows:** the module dependency wiring — who depends on Api, who depends on Impl.

**Layout:** two grouped regions, **Application** (yellow) on the left, **Library** (green) on the right.

- Library region contains two stacked boxes: `Lib-Api` (green, top) and `Lib-Impl` (green, bottom). Arrow `Lib-Impl` → `Lib-Api` ("extends" / "depends on").
- Application region contains: `App DI module` (the one module that may see Impl), `app component`, and `any other library` boxes.
- Arrow `App DI module` → `Lib-Impl` (the single allowed dependency on Impl).
- Arrows from `app component` and `any other library` → `Lib-Api` only (green), showing they never touch Impl.

**Inline caption (article body):** "Only the DI module depends on Impl. Everything else depends on Api."

---

## 4. `manual-di-provider-pattern.png`

**Referenced in:** end of "Static access from inside the library." Acts as a structural synthesis for the Provider pattern across both Android and library contexts.

**Shows:** one owner (the `App`) holding containers, exposing multiple typed Provider interfaces. Each consumer reaches the container through exactly the Provider whose type matches what it is allowed to see.

**Layout:** three columns.

- **Left column — the owner.** `App` (yellow), labelled `: AppContainerProvider, LibProvider, LibInternalProvider`.
- **Center column — owned containers.**
  - `AppContainer` (blue/teal) on top.
  - `LibInternalContainer` (purple outer group) below, with a nested `LibPublicContainer` (green).
  - `val someRepository: SomeRepository` (green) inside the public group.
  - `val someInternalService: SomeInternalService` (purple) inside the internal group, outside the public one.
  - Solid arrows from `App` → both containers, labelled "owns."
- **Right column — consumers, top to bottom.**
  1. (purple) `Android Service` — arrow to `AppContainer` labelled `as AppContainerProvider`.
  2. (green) `External library caller` — arrow to `LibPublicContainer` labelled `as LibProvider`.
  3. (purple) `Library-internal class` — arrow to the **whole outer** `LibInternalContainer` labelled `as LibInternalProvider`.

**Inline caption (article body):** "The app sees green. The library's own classes see purple too."

---

## 5. `manual-di-kmp-hook.png` — cover / hook image

**Referenced as:** the article's lead/cover image (Habr cover, Medium hero). Evocative, **not** a diagram — pure visual hook, no semantic load.

**Concept:** "DI you can see — assembled by hand." A pair of hands wiring a **transparent / glass device** whose insides are fully visible: every cable and connector lit and traced cleanly from one block to the next, nothing hidden. The single idea to land in one glance: clarity and craftsmanship, no black box.

**Contrast cue (subtle, background, optional):** a closed black box with a faint glowing "?" where a competing "magic" approach would hide the same wiring — kept dim so it doesn't compete with the lit hands.

**Mood:** craftsmanship, clarity, calm focus. Not corporate, not cartoonish.

**Palette:** dark background; live wires in Kotlin-ish purple/orange; warm key light on the hands. Matches the dark canvas of the four diagrams.

**Format:** wide cover ratio (16:9, or Habr's cover crop). **No text in the image** — the title is overlaid by the platform.

**Style:** clean semi-realistic 3D render or editorial illustration. Avoid stock-photo literalness and avoid flat clip-art.
