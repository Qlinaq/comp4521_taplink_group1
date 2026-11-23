# Generative AI acknowledgement

OpenAI Codex was used to assist with architecture and implementation guidance, generate and refine Kotlin/Jetpack Compose code and documentation, suggest and implement tests, and review the application for defects. AI-assisted review findings were used to guide revisions. The earlier proposal also used AI assistance for diagram generation and refinement.

This acknowledgement covers substantial implementation assistance, not only language polishing. Build output and test evidence should be checked alongside any generated claims. Team members should review and understand the final code, verify the documented results, and update this statement to include any further tools they use.

## Activity record

| Date (Hong Kong) | Assistance | Deliverable / verification |
| --- | --- | --- |
| 2026-11-22 | Project structure, Room persistence and domain rules | Source files; unit and device tests where recorded |
| 2026-11-22 | Compose screens and Android platform integration | Source files; installed-app checks where recorded |
| 2026-11-22 | Review, debugging and documentation | Review findings, changes and build/test logs |
| 2026-11-23 | Account layer (sign-up, sign-in, reset, change, deletion, session gate) and PBKDF2/OTP policies | `domain/auth/`, `data/auth/`, `ui/auth/`; 29 new JVM tests and 20 new instrumented tests |
| 2026-11-23 | Traditional and Simplified Chinese interface, resource extraction and parity guard | `values-zh-rTW/`, `values-zh-rCN/`, 611 keys each; `LocalizationParityTest` |
| 2026-11-23 | Emulator verification of the account flow and both Chinese variants | Installed-app checks recorded in VERIFICATION.md |
| 2026-11-23 | Notification settings, inbox message kinds and localised message rendering | `ui/settings/NotificationSettingsScreen.kt`, `platform/MessageCopy.kt`, DB v4 migration; policy tests extended |
| 2026-11-23 | Accessibility and elderly-friendly mode, read-aloud, high-contrast theme, simplified flow | `ui/settings/AccessibilityScreen.kt`, `platform/VoiceReader.kt`, `ui/theme/Theme.kt` |
| 2026-11-23 | Contact channels, sign-language chapters and the AI text-support client | `ui/settings/ContactScreen.kt`, `ui/support/`, `data/support/`, `domain/SupportContacts.kt`; 8 new JVM tests |
| 2026-11-23 | Third device pass: swiping cards, elderly mode at 150% text, notification and inbox checks, live DeepSeek answer, Chinese inbox | Installed-app checks and defect list recorded in VERIFICATION.md round 3 |

Only claim a test was executed when its result is present in the verification records. This record does not assign human contributions or imply that a member has reviewed code they have not yet examined.
