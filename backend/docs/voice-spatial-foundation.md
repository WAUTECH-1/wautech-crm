# Voice-ready and spatial foundation — Phase 5

Status: technical design / roadmap, not shipped microphone or immersive support. Reviewed 2026-10-14.

## Voice boundary

Browser speech recognition has limited availability and some browsers send audio to server recognition. It is not automatically a private local transcriber. [MDN SpeechRecognition](https://developer.mozilla.org/en-US/docs/Web/API/SpeechRecognition). No approved speech adapter/key or reliable microphone fixture exists in this workspace; API presence alone would not verify recognition accuracy, permission behavior or retention.

A future isolated opt-in speech experiment must:

1. Disclose the actual recognition provider/location/retention before requesting microphone permission.
2. Start only from a deliberate user click. Show visible recording/timer state, accessible announcements and prominent Stop/Cancel controls.
3. Abort and release audio capture on cancel, navigation, hidden page, logout, tenant change or session failure. No automatic restart, background listening or wake word.
4. Keep a draft transcript separate from chat/business records. Allow review, correction and discarding. Submit text only after explicit confirmation; operational tasks still require the server preview/token confirmation.
5. Preserve the existing full text interface. Unsupported/denied/offline/error conditions show honest unavailable states.
6. Enforce bounded capture duration/bytes/concurrency, no raw-audio persistence by default, authenticated tenant endpoints if server processing is later approved, and consent/retention tests. Do not reuse the CRM AI key in a browser speech SDK.

Acceptance requires physical browser/device permission tests, denied/revoked permission, capture interruption, empty/inaccurate transcript, offline/provider failure, assistive technology use, and confirmation/no-hidden-capture evidence. Mock transcripts are not production voice verification.

## Spatial contracts and dashboard concept

Consume the existing authenticated /api/reports/summary and permitted business goal/profile APIs. Phase 1 prototype uses **aggregate CRM only**, avoiding private preferences and free-form profile text until an explicit privacy review. No separate spatial database or privileged XR API is introduced. Source metadata distinguishes SQL facts, declarations and AI hypotheses; currency groups remain separate, truncation remains visible, and every panel carries scope/time.

Concepts:

- A read-only overview panel with verified CRM counts and independent currency buckets.
- A pipeline panel with current compatibility stages and bounded record cards, with complete-versus-partial labels.
- A goal board with declared milestones and current linked task states separated.
- A briefing panel that labels model recommendations and supports explicit source navigation in the existing web application.

Voice/gesture boundaries: select/inspect/navigation only in the first prototype. No gesture or gaze autonomously executes a CRM mutation. Any future write leaves spatial mode for a readable web preview/confirmation using the same server authorization and CSRF. Never treat gaze as consent or identity.

## Privacy, authentication and accessibility

Reuse same-origin, HttpOnly session cookies and persisted membership enforcement; never put bearer/provider tokens in device assets, URLs or geometry. Tenant/session changes cancel requests and destroy old panels/textures/cached data. No gaze, hand, room map, audio or biometric logging. Private headset/shared-device visibility needs a product decision; default display only minimal aggregates, and hide/exit on lost authentication or application visibility.

The ordinary keyboard/screen-reader web dashboard is the functional alternative. All information and navigation must remain available as semantic text/table controls. Provide adjustable text size, non-color stage labels, seated/reduced-motion modes, pointer alternatives, no forced locomotion, no mandatory headset or voice interaction.

## Compatibility and performance assumptions

No headset is available for this implementation's acceptance. Apple documents immersive web experiences for visionOS; this does not prove WAUTECH compatibility. [Apple immersive website environments](https://developer.apple.com/videos/play/wwdc2026/320/). Feature-detect supported XR session modes on the actual device/browser and request immersive permission only after user action; do not promise immersive AR or cross-device support from a desktop demo.

Proposed prototype budgets (targets, not measured guarantees): preserve device-native refresh rate; keep XR rendering work within the device frame budget; bound visible cards to one existing page and use aggregate endpoints; load no full organization collection; paginate off-screen details. Measure CPU/GPU/memory, text clarity, motion comfort and API p95 latency on representative devices before selecting a renderer or committing production SLOs.

## Prototype milestones

| Milestone | Deliverable                                                              | Acceptance gate                                                                              |
| --------- | ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- |
| S0        | Approved user benefit/personas, device/browser matrix and privacy design | Owner approval and accessibility alternative identified                                      |
| S1        | Isolated opt-in aggregate-only consumer using current contracts          | Same-session tenant denial and logout/data disposal tests; absent from production navigation |
| S2        | Hardware-rendered read-only overview/pipeline                            | Currency/count fidelity, native session permission, measured frame/memory budget             |
| S3        | Goal board and source inspection                                         | Declared-versus-verified labels and keyboard/text parity                                     |
| S4        | Optional reviewed voice/gesture navigation                               | Speech provider approval, transcript/cancel tests, no automatic writes                       |
| S5        | Pilot release decision                                                   | Device/accessibility/privacy/performance acceptance and operational support                  |

No renderer/3D engine is selected merely to demonstrate a concept. Additional adapters/platform assets need review against the existing backend architecture and this compatibility matrix.
