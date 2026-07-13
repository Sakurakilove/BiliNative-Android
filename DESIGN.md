# Design - BiliNative

This is the locked visual system for the Android app. New screens extend this system instead of inventing a separate theme.

## Direction

- Genre: editorial cinema
- App structure: Ecosystem Index - masthead, lead story, channel index, dense catalogue
- Content is the decoration. Use video stills, typography, rules and negative space instead of ornamental cards.

## Color

- Paper: neutral off-white, not cream
- Ink: charcoal, never pure black
- Accent: restrained oxblood, reserved for selection, playback and primary actions
- Dark mode: charcoal paper with warm-white ink; do not invert mechanically
- Use Material semantic color roles in Compose. Raw colors are allowed only for media overlays.

## Typography

- Display and editorial headlines: Android serif, upright, medium or bold
- Body, labels and controls: Android system sans
- Titles wrap when useful; metadata stays compact
- Avoid uppercase decoration except the BILINATIVE masthead and short index labels

## Shape And Surface

- Images: 2-8 dp radius depending on prominence
- Controls: Material shapes, maximum 12 dp for large containers
- No nested cards, glass, ornamental shadows or floating pill navigation
- Group content with hairline rules, surface bands and spacing

## Layout

- Compact: one lead story followed by a two-column visual catalogue or single-column editorial rows
- Expanded: asymmetric lead area and adaptive catalogue; retain Material navigation rail
- Deep media screens hide root navigation and prioritize the player
- Every scrollable catalogue loads more before its final six items

## Motion

- Directional shared-axis navigation with critically damped springs
- State transitions use opacity and translation only
- Reduced-motion mode uses an instant transition or short crossfade
- No decorative perpetual animation

## Accessibility

- 48 dp minimum touch targets
- Body contrast at least 4.5:1
- Icons always have content descriptions when actionable
- Preserve system Back, system bars, font scaling and dark mode

## Hallmark

Genre: editorial. Macrostructure: Ecosystem Index. Designed as a native app using Material 3 semantics.
