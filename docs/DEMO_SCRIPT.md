# TapLink demonstration guide

Use a fresh **additional demo card** for the scripted money examples, so the existing seeded cards and history remain available for exploration. All values below are simulated HKD. Rehearse on the APK that will actually be shared.

## Suggested 12–15 minute presentation

| Time | What to show | What to explain |
| --- | --- | --- |
| 0:00–1:00 | Welcome screen, then Continue as guest; Home and the prototype label | The user need: one place for demo cards, everyday payments, spending and service discovery. Guests can try everything without an account. |
| 1:00–2:30 | Settings → Language → 繁體中文, then 简体中文, then back to English | The switch is immediate, state is kept, and dates and category names follow the language. Everything is translated, not only the buttons. |
| 2:30–5:00 | Settings → Create an account → code from the demo inbox → verify | Passwords are hashed (PBKDF2), codes are single-use with a five-minute life, and delivery is deliberately simulated for the coursework. |
| 5:00–7:00 | Change password, sign out, show the old password being rejected, sign in again | The session is a stored token; a reset ends the old session; the error never reveals whether an account exists. |
| 7:00–9:00 | Switch cards; create and rename a card; freeze and unfreeze | Card changes persist locally. A frozen card cannot make purchases. Accounts do not partition the wallet — state this limit explicitly. |
| 9:00–12:00 | Top up, review a purchase, show its receipt | Balance and ledger change together. Invalid or unaffordable operations make no changes. |
| 12:00–14:00 | Automatic reload and history | Reload occurs at most once after an affordable payment; top-ups are excluded from spending. |
| 14:00–16:00 | Search, category/date filters and spending chart | Charts and totals derive from stored transactions. Explain one figure against its contributing records. |
| 16:00–17:30 | Nearby service points, inbox and preferences | Demo service data; manual district fallback; notifications are optional. |
| 17:30–18:30 | Selected optional capabilities | Show only features verified on the presentation device: pass, offers, widget or shake visibility. |
| 18:30–20:00 | Restart/rotation, architecture and conclusions | Room persistence, test evidence, known limits, AI acknowledgement and future work. |

The official video limit is 20 minutes. Leave time for each member's actual explanation and avoid padding the video with long build or download waits.

If the group presents the public-service round, fold the walkthrough below into the 14:00–17:30 slot and trim the optional-capability slot: swipe the home cards, turn elderly mode on and off, send a test notification, open Contact us, and ask the assistant one question.

## Reproducible transaction example

1. Create a card named `Demo walkthrough` (initial balance HK$0.00).
2. Top up HK$100.00. Review and confirm once. Expected balance: **HK$100.00** and one matching top-up entry.
3. Enable automatic reload with threshold **HK$80.00** and reload amount **HK$50.00**.
4. Pay **HK$30.00** to Campus Cafe. Expected intermediate debit balance: HK$70.00; final balance: **HK$120.00**. History should contain one payment and one related reload.
5. Attempt to pay HK$999.00. It must be rejected; balance remains HK$120.00 and no transaction is added.
6. Freeze the card and try a payment. Show the clear rejection; unfreeze when finished.
7. Inspect history. Spending from this example is **HK$30.00**, not HK$180.00 or a total including top-ups.
8. Close and reopen the app, then rotate the screen. Verify the stored card, balance and history remain consistent.
9. Restore preferred reload settings after the demonstration if needed.

## Reproducible account walkthrough

1. Settings → **Create an account**. Use `demo.owner@ust.hk` and a password such as `TapLink2026`.
2. The verification screen shows a six-digit code in the panel marked **Simulated**. Type it.
3. Settings now shows the display name, the masked address (`d***@ust.hk`) and **Email verified**.
4. **Change password** to a second value. Sign out. The old password must be rejected with
   "That identifier and password do not match."; the new one must sign in.
5. Open Settings → **Delete account** last, after the money walkthrough, because deletion removes
   the credential row while the wallet keeps its balance by design.
6. If you need a clean start, use **Continue as guest** instead of creating an account.

Do not reset the emulator between the account and wallet sections: the demonstration is meant to show
that an account sits in front of the same local wallet.

## Reproducible accessibility and support walkthrough

Run this after the money walkthrough, so the inbox already holds a real transaction.

1. **Home.** Swipe left and right. Name the card each time and point at the dots and
   "Card n of m". Say that swiping changes the selected card, never money.
2. Settings → **Accessibility & elderly mode.** Turn the master switch on. Wait for the layout to
   grow — this is the moment to point out that *everything* grows, not only headings. Mention the
   banner: an elderly user should not have to remember which switch is on.
3. Scroll to **Display** and **Read aloud**. Press **Try it** and let the speaker read the sample.
   Say what happens if no Chinese voice is installed: the button reports it instead of pretending.
4. Go **Home** and show the simplified flow: balance, top up, pay, three records, one contact row.
   Then turn the master switch off and show that all four options go off together.
5. More → **Notifications.** Point at "System notifications are allowed", press **Send a test
   notification**, then pull down the shade. Open the inbox and show the same event stored.
   Say the line that matters: *denying notifications never loses the message*.
6. More → **Contact us.** Show WhatsApp 2520 1314 and the hotline. Explain that **Call** only opens
   the dialer — the app never places a call — and that the disclaimer is deliberately blunt: this is
   a coursework prototype, not Octopus and not a government service.
7. Tap **Chat.** Ask "How do I top up at a store with no network" (or use a suggested chip). Read the
   answer aloud and point at "N questions left today". Explain the cost controls: a cheap model, a
   low reasoning budget, 20 questions a day, and a local FAQ answer when the network or the quota is
   gone. **State the key limitation yourself:** the key sits in the demo APK and production must
   proxy it through a server.
8. Finish on the **sign-language** screen. Show the honest empty state — the player is disabled
   because no clip ships — and the complete written transcript underneath. Do not present a
   placeholder as if it were sign language.

## Presentation preparation

- Record with the final tested APK and readable emulator/device dimensions.
- Use the team's actual names and assigned group number. Do not fabricate work allocation.
- Explain the code paths and trade-offs in your own words; review the AI-assisted implementation first.
- Mention that there is no real-money, Octopus, FPS or real-ticket integration.
- Say the account limits out loud: one profile per installation, simulated code delivery, no server. Markers reward a stated limit over a discovered one.
- Include the concise acknowledgement from `AI_USE.md`, adjusted for any additional tools the team uses.
- A script and screenshots are preparation materials; they are not the required recorded video.
