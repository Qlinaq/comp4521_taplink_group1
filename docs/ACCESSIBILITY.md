# Accessibility, notifications and support (F21–F25)

TapLink is coursework written to the shape of a **Hong Kong Government public-service app**: a
stored-value transport wallet that a ninety-year-old should be able to use, and that a resident
should be able to contact a human about. This document records what was built for that, what was
verified on a device, and what is deliberately not claimed.

The wallet itself (F01–F12), accounts (F19) and interface language (F20) are documented in
[FEATURE_STATUS.md](FEATURE_STATUS.md), [AUTH_DESIGN.md](AUTH_DESIGN.md) and
[DEVELOPMENT_DECISIONS.md](DEVELOPMENT_DECISIONS.md).

## 1. What the round added

| Area | Screen | What it does |
| --- | --- | --- |
| Notifications | More → Notifications | Master switch, live Android permission status, shortcut to the system screen, test notification, five independent category switches |
| Inbox records | More → Messages | Every committed top-up, payment and pass purchase stores a row with its amount; rows render in the current interface language |
| Contact | More → Contact us | WhatsApp 2520 1314, hotline, AI text support, sign-language support, service hours, reply times, prototype disclaimer |
| Accessibility | More → Accessibility & elderly mode | Master switch plus large text, high contrast, read-aloud and simplified flow |
| Home cards | Home | Swipe left or right (or use the arrows) to change card, with dots and "Card n of m" |
| AI support | Contact us → Chat | DeepSeek-backed assistant with a daily quota and an offline fallback |

## 2. Accessibility statement

**Target.** The Hong Kong Government's own web and mobile accessibility baseline is WCAG 2.1
**AA**; the elderly-friendly switches push the visual side to AAA contrast ratios.

**What is implemented.**

- **Large text.** The app multiplies the device font scale by 1.5 (capped at 2.6) through
  `LocalDensity`, so every screen grows, not just headings. At that scale the transaction row moves
  its amount under the merchant and date instead of squeezing three columns into one line, and the
  support chat's page heading was replaced by a status row so the conversation keeps its room.
- **High contrast.** A separate light and dark palette with darker text, a white (or near-black)
  background and explicit outlines on panels and chat bubbles. Contrast is not a colour-only cue:
  the frozen badge, the "elderly-friendly mode is on" banner and the unread dots all carry text.
- **Read-aloud.** `VoiceReader` wraps `TextToSpeech` with a `zh-HK` → `zh-TW` fallback. Each screen
  declares a curated summary through `LocalVoiceSummary` and the speaker action in the app bar
  speaks it — the balance and the next action, not every label on screen.
- **Simplified flow.** The Home page becomes: balance, top up, pay, three recent records and a
  contact row. Card management moves to Settings, and the sixteen-control panel disappears.
- **Sign-language support.** Four chapters (top up, pay, pass, password) each with a written
  transcript and a video slot wired to `file:///android_asset/sign_language/<chapter>.mp4`.
- **Touch and language.** Every control is a Material 3 component with a semantics label; the app
  ships in English, 繁體中文 and 简体中文, and read-aloud follows the chosen language.

**What is not claimed.**

- This is **not a certified audit**. No third-party accessibility assessment was performed.
- TalkBack was not driven end-to-end through every flow. Semantics exist on the controls that were
  built by hand (switches, the read-aloud action, the card pager), but an exhaustive screen-reader
  pass is still outstanding work.
- Read-aloud depends on a Chinese speech engine being installed. When none is present the button
  reports that instead of pretending to speak.
- **No sign-language footage ships.** The player is real and enables itself the moment a clip is
  added to `assets/sign_language/`, but with no clip present the chapter shows "No video attached
  yet" and relies on the written transcript, which is complete on its own.
- No right-to-left language is shipped, so mirroring is untested.

## 3. Notification policy

Three switches must agree before Android shows anything: the **master switch**, the **category**
switch for that kind of alert, and the **Android permission** for the app. The in-app inbox is
independent of all three — a resident who denies notifications still finds every message in
More → Messages, which is the reason alerts are stored rather than only posted.

Each category is deliberately separate so a user can keep low-balance alerts and silence
promotions:

| Category | Fires for |
| --- | --- |
| Transactions | Payments, top-ups and pass purchases |
| Low balance | The final balance after a payment falling below the user's threshold |
| Pass expiry | Three days before a demo pass ends |
| Offers and promotions | Saved demo offers about to expire |
| Service announcements | Informational messages from the app |

Delivery is deferrable WorkManager work, not an alarm: there is no promise of an exact time, and a
device that restricts background work will delay or skip it. Batches carry at most five of the
newest eligible messages, skip anything older than 24 hours, and deduplicate posting best-effort
rather than atomically.

## 4. Support channels and the assistant

The contact number for the brief is **2520 1314**, used for both WhatsApp and the hotline.

- WhatsApp opens `wa.me/85225201314` with a pre-written message. The user still has to press send.
- The hotline uses `ACTION_DIAL`, so the dialer opens with the number filled in and **no call is
  placed** by the app.
- The AI assistant answers in the language chosen in Settings, and is told in its system prompt
  that all money is simulated, that it must never ask for a password or verification code, and that
  anything about a specific account belongs with a human on WhatsApp.

### Cost control

The assistant is deliberately cheap, because a student budget is a real constraint:

| Control | Value | Why |
| --- | --- | --- |
| Model | `deepseek-flash` (configurable via `DEEPSEEK_MODEL`) | Cheapest tier that still answers support questions well |
| `reasoning_effort` | `low` | Long chains of thought are wasted on "how do I top up" |
| `max_tokens` | 480 | Answers are two or three short paragraphs |
| `temperature` | 0.3 | Support copy should be repeatable, not creative |
| Streaming | Off | One request, one billable response |
| Daily quota | 20 questions per Hong Kong day, per installation | Hard ceiling; resets at HKT midnight |
| History sent | 8 turns, 600 characters each, 3000 total | Keeps the cached prefix small |

A typical answer costs roughly 200–260 tokens. The quota is checked **before** the billable call,
and the counter is shown on screen ("19 questions left today") so the limit is never a surprise.

When the key is absent, the device is offline, the request fails or the quota is spent, a local FAQ
matcher answers from the same canned copy the app already ships, and the reply says which fallback
was used. The chat therefore never dead-ends.

### Key handling — read this before shipping

`DEEPSEEK_API_KEY` is read from `local.properties` (git-ignored) at build time and compiled into
`BuildConfig`. **The key is therefore inside the APK and must be treated as public.** That is
acceptable for a marked demo on one laptop; it is not acceptable for distribution. A real
deployment needs the request proxied through a server that holds the key, with per-user rate
limiting and abuse monitoring. `local.properties` is excluded from Git and the key does not appear
in any document in this repository except as a placeholder.

## 5. Verified on device

On the API 35 `Small_Phone` emulator, with the release-candidate build installed:

- Swiping Home changed Campus (1 of 3) → Commute (2 of 3) → Weekend (3 of 3, FROZEN) with the dots
  and the position line following, and the "Current card" dropdown staying in sync.
- The Notifications screen reported "System notifications are not allowed", then "allowed" after
  `pm grant … POST_NOTIFICATIONS` and reopening the screen; "Send a test notification" produced a
  real `taplink_test` notification; a real HK$100 top-up produced
  "Top-up completed — HK$100.00 was added to your card. This is simulated money."
- Contact us listed WhatsApp 2520 1314 · Mon–Fri 09:00–18:00 with **Open**, the hotline with
  **Call**, the AI assistant, sign-language support and the prototype disclaimer.
- The assistant answered "How do I top up at a store with no network" through `deepseek-flash`,
  said the prototype has no real store top-up, and handed account matters to WhatsApp 2520 1314.
- At 150% text with high contrast: the elderly banner, the settings switches, the activity list
  (amount stacked under the merchant), the Traditional Chinese inbox and the chat all stayed
  readable, and the master switch turned all four options off together — confirmed by reading the
  stored preferences out of the database, not just the screen.
- Traditional Chinese: 設定 / 外觀與卡 / 語言 / 訊息中心 / 增值完成 / 此為模擬金額.

## 6. Source map

| File | Role |
| --- | --- |
| `ui/settings/NotificationSettingsScreen.kt` | Notification switches, permission status, test notification |
| `ui/settings/AccessibilityScreen.kt` | Elderly master switch and the four options |
| `ui/settings/ContactScreen.kt` | Contact channels, hours, disclaimer |
| `ui/support/SupportChatScreen.kt` | Chat UI, suggestions, footer handoff |
| `ui/support/SupportChatViewModel.kt` | Sending, fallback, secret refusal |
| `ui/support/SignLanguageScreen.kt` | Chapters, transcripts, video slot |
| `ui/HomeCards.kt` | `HorizontalPager`, arrows, dots, position line |
| `data/support/SupportAssistant.kt` | Quota, prompt rules, history trimming, FAQ matcher |
| `data/support/DeepSeekSupport.kt` | HTTP client, error mapping, quota gate |
| `platform/VoiceReader.kt` | Text-to-speech wrapper |
| `platform/MessageCopy.kt` | Localised rendering of stored messages |
| `platform/ExternalIntents.kt` | WhatsApp, dialer, e-mail, URL intents |
| `domain/SupportContacts.kt` | Numbers, hours, sign-language chapter ids |
