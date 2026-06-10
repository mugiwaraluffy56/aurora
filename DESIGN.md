# Aurora Design Direction

Aurora uses an Apple glass / soft-futurism direction for premium consumer software: translucent surfaces, calm depth, sparse system tint, and typography that feels native.

## Theme

- Mood: calm, premium, trustworthy.
- Atmosphere: hardware-adjacent, with layered surfaces that imply physical depth.
- Default app environment: dark cinema-first UI with glass-like elevated surfaces.
- Use one system tint per screen or surface for the primary action only.
- Prefer translucent/vibrancy-style surfaces over heavy solid chrome where platform support allows.

## Palette

```css
/* light */
--bg:                  #ffffff;
--bg-elevated:         #ffffff;
--surface-grouped:     #f2f2f7;
--separator:           rgba(60,60,67,0.18);
--text:                #1d1d1f;
--text-secondary:      rgba(60,60,67,0.78);
--text-tertiary:       rgba(60,60,67,0.55);

/* dark */
--bg-dark:             #000000;
--bg-elevated-dark:    #1c1c1e;
--surface-grouped-dark:#2c2c2e;
--separator-dark:      rgba(84,84,88,0.65);

/* system tints */
--system-blue:         #007aff;
--system-green:        #34c759;
--system-red:          #ff3b30;
--system-orange:       #ff9500;
--system-purple:       #af52de;
--system-pink:         #ff2d55;
--system-yellow:       #ffcc00;

/* vibrancy materials */
--material-thin:       rgba(255,255,255,0.6);
--material-regular:    rgba(255,255,255,0.78);
--material-thick:      rgba(255,255,255,0.92);
```

## Typography

- Use platform system fonts everywhere.
- Apple targets: SF Pro Text at 19px and below, SF Pro Display at 20px and above.
- Android implementation: use the system sans family and match Apple HIG scale proportions.
- Scale: 11 / 12 / 13 / 15 / 17 / 22 / 28 / 34 / 44 / 56.
- Keep optical sizing natural; do not force condensed or custom display faces.

## Components

- Primary buttons: system blue fill, white text, 12dp radius, 10/20 padding, 600 weight.
- Bordered buttons: clear fill, blue text, blue outline only where needed.
- Plain buttons: text-only blue.
- Cards and sections: elevated dark surface, 14dp radius, no hard card borders.
- Grouped lists: grouped surface with separator hairlines between rows.
- Inputs: grouped surface, no border, 10dp radius, 11/14 padding.
- Navigation: translucent or thin material feel over content; title weight 600.

## Layout

- Touch targets are at least 44dp.
- Base unit is 8dp.
- Spacing scale: 4 / 8 / 12 / 16 / 20 / 24 / 32 / 44 / 56 / 80.
- Respect safe areas on mobile.
- Edge-to-edge content; chrome floats above where possible.

## Avoid

- Mixing multiple system tints on one surface.
- Heavy shadows on cards, buttons, or inputs.
- Hard borders on cards.
- Dense copy where direct controls will do.
