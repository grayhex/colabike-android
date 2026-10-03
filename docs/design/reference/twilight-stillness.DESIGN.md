---
version: 1
name: "Twilight Stillness Mobile"
colors:
  background: "#151413"
  surface: "#1F1E1D"
  surface-alt: "#2A2927"
  on-surface: "#EAE6DB"
  on-surface-variant: "#9A9893"
  primary: "#C9BBA5"
  on-primary: "#151413"
  accent: "#8C9A8D"
  on-accent: "#151413"
  border: "#2A2927"
  destructive: "#7F1D1D"
  illus-mist-warm: "#E8D9C4"
  illus-mist-cool: "#B8C4CC"
  illus-silhouette-deep: "#2C3540"
  illus-silhouette-evergreen: "#5A6B5E"
  illus-glow-peach: "#D9B89A"
typography:
  display-md:
    fontFamily: "Lora"
    fontSize: 30px
    fontWeight: 400
    lineHeight: 36px
    letterSpacing: 0em
  headline-lg:
    fontFamily: "Lora"
    fontSize: 24px
    fontWeight: 400
    lineHeight: 30px
    letterSpacing: 0.01em
  headline-md:
    fontFamily: "Lora"
    fontSize: 20px
    fontWeight: 400
    lineHeight: 26px
    letterSpacing: 0.01em
  headline-sm:
    fontFamily: "Lora"
    fontSize: 18px
    fontWeight: 400
    lineHeight: 24px
    letterSpacing: 0.01em
  numeral-stat:
    fontFamily: "Lora"
    fontSize: 30px
    fontWeight: 400
    lineHeight: 34px
    letterSpacing: 0em
  body-md:
    fontFamily: "Outfit"
    fontSize: 14px
    fontWeight: 300
    lineHeight: 22px
    letterSpacing: 0.01em
  body-sm:
    fontFamily: "Outfit"
    fontSize: 13px
    fontWeight: 300
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: "Outfit"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 18px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: "Outfit"
    fontSize: 12px
    fontWeight: 500
    lineHeight: 16px
    letterSpacing: 0.02em
  caption:
    fontFamily: "Outfit"
    fontSize: 12px
    fontWeight: 300
    lineHeight: 16px
    letterSpacing: 0.01em
  label-uppercase:
    fontFamily: "Outfit"
    fontSize: 10px
    fontWeight: 300
    lineHeight: 14px
    letterSpacing: 0.18em
rounded:
  sm: 16px
  md: 22px
  lg: 24px
  xl: 32px
  full: 9999px
  shape: squircle
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 24px
  card-padding: 24px
  section-gap: 40px
icons:
  set: phosphor
  default_variant: thin
  active_variant: fill
  stroke_weight: 1px
  default_size: 24px
components:
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    borderColor: "{colors.border}"
    borderWidth: 1px
    rounded: "{rounded.lg}"
    padding: "{spacing.card-padding}"
    boxShadow: none
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-md}"
    rounded: "{rounded.full}"
    paddingX: 24px
    paddingY: 10px
    border: none
    boxShadow: none
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-sm}"
    borderColor: "{colors.border}"
    borderWidth: 1px
    rounded: "{rounded.full}"
    paddingX: 24px
    paddingY: 12px
    boxShadow: none
  button-text:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
    paddingX: 0px
    paddingY: 0px
    border: none
    boxShadow: none
  input-text:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-sm}"
    borderColor: "{colors.border}"
    borderWidth: 1px
    rounded: "{rounded.full}"
    paddingX: 24px
    paddingY: 16px
    focusRingColor: "{colors.primary}"
    boxShadow: none
  chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-sm}"
    borderColor: "{colors.border}"
    borderWidth: 1px
    rounded: "{rounded.full}"
    paddingX: 24px
    paddingY: 8px
    boxShadow: none
  chip-active:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    paddingX: 24px
    paddingY: 8px
    border: none
    boxShadow: none
  badge-pill:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-uppercase}"
    borderColor: "{colors.border}"
    borderWidth: 1px
    rounded: "{rounded.full}"
    paddingX: 12px
    paddingY: 6px
    gap: 8px
    iconSize: 16px
    boxShadow: none
  avatar:
    backgroundColor: "{colors.surface-alt}"
    textColor: "{colors.on-surface}"
    size: 40px
    rounded: "{rounded.full}"
    border: none
    boxShadow: none
  list-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    borderColor: "{colors.border}"
    borderWidth: 1px
    rounded: "{rounded.md}"
    padding: 16px
    gap: 16px
    boxShadow: none
  progress-bar:
    backgroundColor: "{colors.surface-alt}"
    rounded: "{rounded.full}"
    height: 4px
    border: none
    boxShadow: none
  top-app-bar:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface}"
    typography: "{typography.headline-lg}"
    paddingX: "{spacing.gutter}"
    paddingY: 0px
    border: none
    boxShadow: none
  tab-bar:
    backgroundColor: "{colors.background}"
    borderColor: "{colors.border}"
    borderWidth: 1px
    rounded: "{rounded.full}"
    paddingX: 24px
    paddingY: 16px
    boxShadow: "0 25px 50px -12px rgba(0,0,0,0.5)"
  tab-item-active:
    textColor: "{colors.primary}"
    iconSize: 24px
    border: none
    boxShadow: none
  tab-item-inactive:
    textColor: "{colors.on-surface-variant}"
    iconSize: 24px
    border: none
    boxShadow: none
---

# Twilight Stillness Mobile

## Overview

**Hushed. Atmospheric. Reverent.** A dark contemplative aesthetic built from warm charcoal surfaces, parchment-toned text, and a single muted sand-beige accent. Every surface is whisper-quiet — translucent cards over a near-black canvas, hairline borders instead of dividers, type set in a calm serif at light weights, and ambient color "auras" bleeding from the edges of the screen at 5% opacity to suggest distance and depth without ever raising its voice.

The mood is contemplative, evening, slow. Type is unhurried — long-form serif headings paired with a thin geometric sans body — and every interactive element wears soft pill or squircle corners. Light here is not bright; it's the warm afterglow of a sun that has already set. Where most dark UIs go cool-blue and screen-glow, this one goes warm-charcoal and parchment, giving the dark mode a candle-lit rather than electronic feel.

## Colors

The palette is a tight monochromatic warm-charcoal range plus one sand-beige primary and one sage-green accent. State the role of each:

- `{colors.background}` — the deepest canvas, near-black with a brown undertone. Owns the entire screen behind everything else.
- `{colors.surface}` — the primary card / popover surface, one shade up from background. Always rendered at 40% opacity over background with `backdrop-blur-md` to feel atmospheric, not flat.
- `{colors.surface-alt}` — secondary surface used for muted progress tracks, hover states, and the "track" half of progress visualizations. Also the border color.
- `{colors.on-surface}` — warm parchment, the workhorse text color. Never pure white.
- `{colors.on-surface-variant}` — muted parchment for metadata, labels, captions, inactive tabs.
- `{colors.primary}` — sand-beige. Reserved for: active tab icon, primary CTA fill, active chip fill, progress-bar fill, focus ring. NEVER used as a card or surface fill — its job is to be the single warm point of focus on a dark field.
- `{colors.accent}` — sage green, used very sparingly for a secondary categorical color (e.g. one icon in a soft category tile, or the second ambient aura). Never for buttons.
- `{colors.border}` — equals `{colors.surface-alt}` — used at 1px hairline weight on every card, chip, input, and the floating tab bar.
- `{colors.destructive}` — deep oxblood, for destructive actions only. Never decorative.

**Atmospheric auras**: two fixed-position decorative blobs are positioned offscreen-edge and rendered as `bg-{primary}` or `bg-{accent}` at 5% opacity with `blur-[100px]`–`blur-[120px]`. They are atmospheric only — they must never sit behind interactive content close enough to tint it.

**Hard rule**: `{colors.primary}` and `{colors.accent}` never become large surface fills. They are point colors. The only large beige region permitted is the active CTA pill.

## Typography

Two voices in dialogue: **Lora** (serif) for every heading and any number that wants to feel considered; **Outfit** (geometric sans) for body, labels, metadata, and buttons. Lora is set at weight 400 (Regular) — never bold — with slightly open tracking (+0.01em) so headings read soft and literary. Outfit body copy is set at weight 300 (Light) with +0.01em tracking for an airy, unhurried feel.

Role assignments:

- `{typography.display-md}` — hero card title (e.g. featured-content overlay).
- `{typography.headline-lg}` — primary screen title at the top of every screen.
- `{typography.headline-md}` / `headline-sm}` — section anchors and subsection titles inside cards.
- `{typography.numeral-stat}` — large data numerals on stat tiles. Set in Lora to keep numbers feeling crafted, not technical.
- `{typography.body-md}` — paragraph copy and longer descriptions.
- `{typography.body-sm}` — list-row primary text, button labels on secondary buttons.
- `{typography.label-md}` — primary CTA button label (Outfit 400).
- `{typography.label-sm}` — chip label (Outfit 500 — the only place we allow medium weight, used to keep small chip text legible).
- `{typography.caption}` — metadata, "see all" link, helper text.
- `{typography.label-uppercase}` — eyebrows, kickers, badge text. Set in Outfit Light with strong +0.18em tracking; the wide tracking is load-bearing — without it, this token loses its meaning.

**Weight rule**: Light (300) is the dominant body weight. Regular (400) is the heaviest weight that appears in body or labels. Bold weights are forbidden across the entire system — visual emphasis comes from color and size, never from heft.

## Spacing & Layout

Generous, contemplative density. `{spacing.gutter}` of 24px on the screen edges; vertical section gaps of `{spacing.section-gap}` (40px) — this is wider than typical, and the airiness is a load-bearing aesthetic property. Cards use `{spacing.card-padding}` of 24px and rarely fewer than 4px of internal `gap`. Top of screen sits at `pt-14` (56px) to clear status-bar area and give the screen title room to breathe.

The bottom tab bar floats with 32px clearance above the bottom edge — never edge-attached. Content scrolls underneath it with bottom padding of `pb-32` so nothing crashes into it.

## Elevation & Depth

Depth comes from **translucency, blur, and ambient color**, not from drop shadows. Three layers exist on any screen:

1. **Background canvas** — `{colors.background}` solid, with one or two atmospheric auras far below the visible content.
2. **Surface layer** — cards rendered as `bg-{surface}/40` with `backdrop-blur-md` and a 1px `{colors.border}` hairline. They feel suspended in mist rather than stacked on paper.
3. **Floating chrome** — the bottom tab bar is the only element with a real shadow (`shadow-2xl`), and it gets `bg-{background}/85` with `backdrop-blur-xl` to feel separate from the scroll content.

Cards in the body never carry shadows. Borders carry the separation work. The active tab icon receives a colored drop-shadow glow (`drop-shadow-[0_0_8px_var(--primary)]`) — the only place light is allowed to "emit" from an element.

## Shapes

A squircle-forward shape language. Every container (card, input, chip, button, tab bar) wears soft, generous corners — never hard right angles, never sharp small radii. The radius scale: `{rounded.sm}` 16px for inset thumbnails and small icon tiles; `{rounded.md}` 22px for list rows; `{rounded.lg}` 24px for primary cards; `{rounded.xl}` 32px for hero / chart cards. Pills (`{rounded.full}`) are used for chips, buttons, the search input, badges, and the floating tab bar — anything containing a single-line label.

**Squircle implementation note**: this aesthetic uses CSS `corner-shape: squircle` with a `--shape-multiplier` of 2.5 in supporting browsers, producing iOS-style superellipse corners that read smoother than standard circular `border-radius`. In supporting browsers the actual rendered radius is the token value × 2.5, delivering a deliberately soft, oversized squircle feel. Non-supporting browsers fall back to standard rounded corners at the token value. Component snippets use Tailwind `rounded-*` arbitrary classes; the squircle treatment is applied via the root CSS, not the snippet.

## Components

Surfaces are translucent and bordered. Buttons split into a single solid-beige primary CTA, a translucent-card secondary, and a plain text variant. Inputs are pill-shaped with hairline borders and an inset leading icon. Chips are pill-shaped, with the active state a solid beige pill against an unbordered field of unfilled hairline pills.

Tonal recipes (use these instead of adding component variants):
- *Soft icon tile* (e.g. for category entries): a `{rounded.lg}` square at `size-[72px]`, `bg-{surface}/40` with `backdrop-blur-md` and a hairline border, holding a single thin-variant icon centered. Icon color is `{colors.primary}` for the lead category, `{colors.accent}` for an alternate, or `{colors.on-surface}` for the rest.
- *Tinted icon halo* (used inside list rows for category leadingicons): a `size-12 rounded-xl` square with `bg-{primary}/20` or `bg-{accent}/20` background and matching `text-{primary}` or `text-{accent}` icon. The 20% alpha tint is the signature here.
- *Stat tile*: `card` styling with `{typography.label-uppercase}` eyebrow on top and `{typography.numeral-stat}` numeral on the next line, with a `{typography.caption}` unit suffix offset by `gap-1` and baseline-aligned via `items-baseline`.
- *Vertical bar chart bar*: a full-height `bg-{primary}/20` `{rounded.full}` track with an inner `bg-{primary}` fill, percent height set inline, sitting above a `{typography.label-uppercase}` axis label.
- *Mood selector tile*: a 5-up row inside a single bordered card; each option is a `p-3 rounded-2xl` button containing one thin-variant face icon. The selected state swaps to `hover:bg-{primary}/20` background and `text-{primary}` icon — no fill, just the warm wash.

### card

```html
<div class="bg-card/40 backdrop-blur-md border border-border rounded-3xl p-6 text-foreground">
  [Card content]
</div>
```

### button-primary

```html
<button class="bg-primary text-primary-foreground px-6 py-2.5 rounded-full text-[13px] font-normal tracking-wide">
  [Action label]
</button>
```

### button-secondary

```html
<button class="bg-card/40 backdrop-blur-md border border-border text-foreground px-6 py-3 rounded-full text-sm font-light tracking-wide flex items-center gap-2 hover:bg-card/60 transition-colors">
  [Action label] <iconify-icon icon="ph:[icon-name]-thin" class="text-lg"></iconify-icon>
</button>
```

### button-text

```html
<a href="#" class="text-xs text-muted-foreground hover:text-primary underline underline-offset-4 font-light">[See all]</a>
```

### input-text

```html
<div class="relative">
  <iconify-icon icon="ph:[icon-name]-thin" class="absolute left-4 top-1/2 -translate-y-1/2 text-muted-foreground text-xl"></iconify-icon>
  <input type="text" placeholder="[Placeholder]" class="w-full bg-card/40 border border-border rounded-full py-4 pl-12 pr-6 text-sm font-light focus:outline-none focus:ring-1 focus:ring-primary transition-all" />
</div>
```

### chip / chip-active

```html
<div class="flex gap-3 overflow-x-auto -mx-6 px-6">
  <button class="bg-primary text-primary-foreground px-6 py-2 rounded-full text-xs font-medium whitespace-nowrap">[Active label]</button>
  <button class="bg-card/40 border border-border text-muted-foreground px-6 py-2 rounded-full text-xs font-light whitespace-nowrap hover:text-foreground">[Inactive label]</button>
</div>
```

### badge-pill

```html
<div class="flex items-center gap-2 bg-card/50 backdrop-blur-md border border-border px-3 py-1.5 rounded-full">
  <iconify-icon icon="ph:[icon-name]-thin" class="text-accent text-lg"></iconify-icon>
  <span class="text-xs font-light tracking-widest uppercase text-muted-foreground">[Badge value]</span>
</div>
```

### avatar

```html
<div class="size-10 rounded-full bg-secondary flex items-center justify-center text-foreground text-sm font-light">
  [I]
</div>
```

### list-row

```html
<div class="bg-card/40 backdrop-blur-md border border-border rounded-2xl p-4 flex items-center justify-between group cursor-pointer hover:bg-secondary/50 transition-colors">
  <div class="flex items-center gap-4">
    <div class="size-12 rounded-xl bg-primary/20 flex items-center justify-center text-primary">
      <iconify-icon icon="ph:[icon-name]-thin" class="text-2xl"></iconify-icon>
    </div>
    <div>
      <h5 class="text-sm font-normal">[Title]</h5>
      <p class="text-xs text-muted-foreground font-light">[Metadata]</p>
    </div>
  </div>
  <iconify-icon icon="ph:[icon-name]-thin" class="text-2xl text-muted-foreground group-hover:text-primary transition-colors"></iconify-icon>
</div>
```

### progress-bar

```html
<div class="w-full h-1 bg-muted rounded-full overflow-hidden">
  <div class="h-full bg-primary rounded-full" style="width: 60%"></div>
</div>
```

### top-app-bar

```html
<header class="flex items-center justify-between">
  <div>
    <h1 class="font-heading text-2xl font-normal tracking-wide">[Screen title]</h1>
    <p class="text-muted-foreground text-sm font-light tracking-wide mt-1">[Subtitle]</p>
  </div>
</header>
```

### tab-bar

```html
<div class="fixed bottom-8 left-6 right-6 z-50">
  <nav class="bg-background/85 backdrop-blur-xl border border-border rounded-full px-6 py-4 flex justify-between items-center shadow-2xl">
    <a href="#" class="flex flex-col items-center gap-1 text-primary">
      <iconify-icon icon="ph:[icon-name]-fill" class="text-2xl drop-shadow-[0_0_8px_var(--primary)]"></iconify-icon>
      <div class="size-1 rounded-full bg-primary mt-0.5"></div>
    </a>
    <a href="#" class="flex flex-col items-center gap-1 text-muted-foreground hover:text-foreground transition-colors">
      <iconify-icon icon="ph:[icon-name]-thin" class="text-2xl"></iconify-icon>
      <div class="size-1 rounded-full bg-transparent mt-0.5"></div>
    </a>
    <!-- repeat for remaining tabs -->
  </nav>
</div>
```

## Iconography

Icon family: **Phosphor** (`ph:`). The default chrome variant is `thin` — Phosphor's lightest stroke weight, which matches the airy weight strategy of the type system. Active / selected icons swap to `fill` (the solid silhouette variant), giving a clean two-state design: hairline outline at rest, full silhouette when selected. The active icon also receives a colored drop-shadow halo (`drop-shadow-[0_0_8px_var(--primary)]`) so the active state reads even at thumbnail size.

**Phosphor variant suffix convention**: every Phosphor variant is suffixed (`ph:user-thin`, `ph:user-fill`, `ph:user-bold`, `ph:user-light`, `ph:user-duotone`). Phosphor's "regular" variant is the BARE name with NO suffix (e.g. `ph:user`, never `ph:user-regular` — that name does not exist and will render as a missing-icon glyph). This system uses `thin` and `fill` exclusively, so the regular bare-name form should not appear.

Icon size is 24px (`text-2xl`) in tab bars and trailing accessory roles, 16–20px (`text-lg` / `text-xl`) in inline contexts (badges, search prefixes), and 32–48px in tinted-halo or category-tile contexts. Never mix variants within a single screen for the same UI role.

## Illustration & Imagery

**Style / Technique**: atmospheric photographic landscapes with strong fog, mist, and haze. Soft-focus, low-contrast naturalism — long-lens compression of layered terrain with significant atmospheric perspective dissolving distant subjects into pale gradients. Read as photography, not illustration; the image-making language is "a photograph taken at dawn or dusk during heavy fog." Rendering choices are restrained and tonal — never punchy, never high-contrast, never saturated.

**Palette**: each image is dominated by ONE atmospheric tint — warm peach-cream (sun glow at horizon), cool slate-blue (twilight haze), soft sage-teal (misty forest), or warm grey-beige. Distant subjects shift toward `{colors.illus-mist-warm}` or `{colors.illus-mist-cool}`; near-foreground silhouettes hold deeper `{colors.illus-silhouette-deep}` or `{colors.illus-silhouette-evergreen}`. No more than 4 tonal values per image; saturation stays low across the entire frame. A subtle warm `{colors.illus-glow-peach}` may light the horizon line as the only "warmth source."

**Edge treatment**: no outlines anywhere; subjects are defined by tonal silhouette against atmospheric mist. Edges are SOFT — feathered, dissolving into haze, never crisp. Photographic, not vector.

**Shading & light**: extremely soft, even, ambient. One implied light source (low sun or hidden moon) backlights silhouettes from behind, creating layered planes of receding tonal value. No hard shadows, no specular highlights, no rim light.

**Composition & framing**: layered horizontal bands of receding mountains, trees, or water — minimum two depth planes, ideally three or four. Subjects sit low in the frame; sky / mist occupies the upper half. Negative space is essential — at least 40% of the canvas is atmospheric haze, not subject. Subjects bleed to the edges of the frame; never centered, never composed like a hero portrait.

**Background within the illustration**: the gradient sky / mist IS the background; there is no separate backdrop. Smooth vertical gradient from a deeper top tone down to a paler, glow-lit horizon.

**Container styling**: hero imagery sits inside a `rounded-[32px]` frame (`{rounded.xl}`) with a 1px `{colors.border}` hairline. Smaller list-row thumbnails use `rounded-[16px]` (`{rounded.sm}`); square soundscape tiles use `rounded-2xl` (`{rounded.md}`) with the same hairline. Inside a hero card, the image is overlaid by `bg-gradient-to-t from-background via-background/40 to-transparent opacity-90` so the bottom 60% of the image fades into solid background, providing readable space for overlaid type. Smaller list-row thumbnails get a `bg-black/20` flat overlay to seat a centered play affordance.

**CSS treatment on the `<img>`**: `object-cover` on every image. `transition-transform duration-1000 group-hover:scale-105` on hero images and `duration-700` / `duration-500` on list-row and tile images for a slow, contemplative parallax on hover. No `mix-blend-mode`, no opacity dimming, no CSS filter. The atmospheric quality lives in the source image, not in CSS treatment.

**Box-shadow on the image or its frame**: none — the image sits flat inside its bordered frame. Depth comes from the 1px hairline and the gradient overlay, never from a drop shadow.

**Aspect ratio rule**: hero cards are tall portrait (5:6 or 4:5 — implemented as fixed `h-[400px]` on a full-width container). Featured-section cards are landscape (`h-[220px]` full-width). Soundscape tiles are 1:1 (`aspect-square`). List-row thumbnails are 1:1 at fixed `size-16`. Always crop with `object-cover`; never letterbox.

**Anti-references**: NOT 3D, NOT claymation, NOT illustrated vector, NOT flat 2D, NOT photoreal-bright, NOT saturated, NOT high-contrast, NOT macro / close-up subject, NOT portraiture / human-centered, NOT product photography, NOT studio-lit.

**Style recipe prompt**:

```
A long-lens atmospheric landscape photograph of [SUBJECT], taken at dawn or dusk during heavy fog. Layered horizontal bands of silhouetted terrain receding into pale, low-saturation mist. Strong atmospheric perspective: distant elements dissolve to a single pale tint (peach-cream, slate-blue, sage-teal, or warm grey-beige). Soft, even ambient backlight from a low hidden sun; no hard shadows. 3–4 tonal values only, soft edges feathering into haze, ~40% of frame is atmospheric negative space, subject sits low in the frame. Contemplative, hushed, evening mood. Not high-contrast, not saturated, not 3D, not illustrated, not portraiture. Photograph.
```

## Hierarchy & Emphasis

Hierarchy is built from **type contrast and color restraint**, not weight or scale alone. Lora at the top of a section anchors visual attention; Outfit Light below it whispers the supporting copy. Saturation is reserved exclusively for active states — the active tab icon, the active chip pill, the focused input ring, the progress fill. Everything else lives in the warm-charcoal-to-parchment monochrome range. Screen titles are the loudest element on any screen; metadata is always uppercase, tracked-out, and `{colors.on-surface-variant}` so it whispers.

## Distinctive Details

1. **Atmospheric ambient auras**: every screen has one or two fixed-position blur blobs (`opacity-5`, `blur-[100px]`+, positioned offscreen) in `{colors.primary}` or `{colors.accent}`. They produce a barely-perceptible color wash at the edges of the screen — the room has weather. This is the signature.
2. **Translucent-over-blur card surfaces**: every card is `bg-{surface}/40` plus `backdrop-blur-md` plus a hairline border. The combination feels suspended in mist rather than placed on a plane.
3. **Active-icon glow halo**: the active tab icon emits a soft colored drop-shadow (`drop-shadow-[0_0_8px_var(--primary)]`). It is the only place in the system where a UI element is allowed to "emit" light — and that scarcity is what makes it work.

## Do's and Don'ts

### Do
- Set every card surface as `bg-{surface}/40` with `backdrop-blur-md` and a 1px `{colors.border}` hairline.
- Reserve `{colors.primary}` for active states, primary CTAs, progress fills, and focus rings only.
- Use Lora for every heading and large numeral; Outfit Light for body and labels.
- Place one or two atmospheric aura blobs (`opacity-5`, `blur-[100px]`+) on every screen to give the canvas weather.
- Pair `ph:[name]-thin` for resting state with `ph:[name]-fill` for active state — and add a colored drop-shadow halo on active.
- Float the bottom tab bar with 32px bottom clearance, hairline border, `backdrop-blur-xl`, and a `shadow-2xl`.
- Use `tracking-widest uppercase` `{typography.label-uppercase}` for every eyebrow / kicker / metadata caption.

### Don't
- Don't use bold weights anywhere — Light and Regular only.
- Don't use `{colors.primary}` as a card or surface fill; it is a point color, not a sheet color.
- Don't add drop shadows to in-body cards — depth comes from translucency and hairline borders, not elevation.
- Don't use pure white text or pure black backgrounds — both warmth-tinted (`#EAE6DB` and `#151413`) only.
- Don't introduce illustrative or vector imagery — this aesthetic uses photographic mist landscapes only.
- Don't write `ph:[name]-regular` for Phosphor — the regular variant is the bare name with no suffix.
- Don't tighten section spacing below 40px; the airy vertical rhythm is part of the aesthetic.
- Don't use sharp small radii — every container is squircle-soft, with no element below 16px corner radius.
