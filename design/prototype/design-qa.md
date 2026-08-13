# Design QA — warm family companion

- Source visual truth: `C:\Dev\Repos\my-dear\docs\design\selected-warm-family-onboarding.png`
- Browser-rendered implementation: `C:\Dev\Repos\my-dear\design\prototype\implementation-mobile-screen.png` and `implementation-mobile-scrolled.png`
- Combined comparison: `C:\Dev\Repos\my-dear\design\prototype\design-qa-comparison.png`
- Source pixels: 853 x 1844, normalized to 426 x 921 for comparison
- Implementation viewport: iPhone app screen 393 x 852 CSS px at deviceScaleFactor 1, normalized to 426 x 923 for comparison
- State: Korean light-theme chat, tutorial step 1, top and scrolled-to-coachmark/voice states

## Full-view comparison evidence

The combined comparison shows the source long-scroll composition beside the implementation's top and scrolled app-screen captures. The implementation preserves the warm ivory/coral palette, large Korean hierarchy, welcome card, three quick actions, user/assistant messages, web provenance, source row, four-step coachmark, large voice control, local-processing cue, persistent composer, and exactly three bottom destinations.

The source is a flattened long screen while the protected mobile runtime uses a real 393 x 852 viewport with fixed composer/navigation. The implementation therefore presents the same vertical content across top and scrolled captures. This is an intentional runtime constraint rather than hidden or missing content.

## Focused-region comparison evidence

- Header/welcome: generated family mascot asset now matches the source's coral family mark and is used consistently in the header and assistant identity.
- Voice/onboarding: scrolled capture confirms the coachmark pointer, `1단계`, `1 / 4`, skip/next actions, coral microphone, and privacy cue.
- Search provenance: source name, update date, web-search label, and external-link affordance remain visually grouped in the answer card.
- Bottom controls: composer and three-item navigation remain fixed, large, labeled, and selected state uses both tint and filled shape.

## Required fidelity surfaces

- Fonts and typography: Korean system sans fallback is optically close to the source; headline/body hierarchy, boldness, wrapping and line height remain readable. No clipped action text is visible at the tested viewport.
- Spacing and layout rhythm: 24px-style outer margins, rounded welcome/answer/coachmark surfaces, separators, and large control rhythm follow the source. Runtime chrome accounts for the vertical pagination difference.
- Colors and tokens: warm ivory, coral, peach, green and blue semantic accents match the selected direction. Dark text retains strong contrast.
- Image quality and asset fidelity: the custom family mark is a generated raster asset derived from the selected direction, not CSS/SVG placeholder art. Standard controls use Radix/Material icon libraries.
- Copy/content: Korean labels, weather example, provenance, tutorial, privacy cue, composer and navigation match the selected product intent.

## Interaction and runtime checks

- Tutorial next and skip actions tested.
- Voice control idle/listening toggle tested.
- `채팅`, `채팅 목록`, and `설정` navigation tested.
- Subscription comparison expand/collapse tested; preview-only price and online-transmission disclosure visible.
- Message field and send affordance exposed with accessible labels.
- Browser console warnings/errors checked: none.
- `npm run check:runtime` and production build passed.

## Comparison history

1. P2 — the initial implementation replaced the source mascot/logo with a generic code icon. Fixed by generating a dedicated warm-family mascot raster asset and using it in the prototype and Android app. Post-fix evidence is visible in the final combined comparison.
2. P2 — the initial voice control used a speaker icon instead of the source microphone. Fixed with the Material microphone from the `react-icons` library. The final scrolled capture shows the correct microphone affordance.
3. P2 — the first comparison included the device frame and mismatched the frameless source. Fixed by cropping the protected runtime to the 393 x 852 app screen and normalizing density before comparison.

## Findings

No actionable P0, P1, or P2 fidelity findings remain for the selected chat/tutorial state.

## Follow-up polish

- P3: tune font metrics against a real Android Korean font capture during native AVD visual regression.
- P3: generate dark-theme visual references once the native theme is finalized.

final result: passed
