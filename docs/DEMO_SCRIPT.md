# TapLink demonstration guide

Use a fresh **additional demo card** for the scripted money examples, so the existing seeded cards and history remain available for exploration. All values below are simulated HKD. Rehearse on the APK that will actually be shared.

## Suggested 12–15 minute presentation

| Time | What to show | What to explain |
| --- | --- | --- |
| 0:00–1:00 | Home and the prototype label | The user need: one place for demo cards, everyday payments, spending and service discovery. |
| 1:00–3:00 | Switch cards; create and rename a card; freeze and unfreeze | Card changes persist locally. A frozen card cannot make purchases. |
| 3:00–6:00 | Top up, review a purchase, show its receipt | Balance and ledger change together. Invalid or unaffordable operations make no changes. |
| 6:00–8:00 | Automatic reload and history | Reload occurs at most once after an affordable payment; top-ups are excluded from spending. |
| 8:00–10:00 | Search, category/date filters and spending chart | Charts and totals derive from stored transactions. Explain one figure against its contributing records. |
| 10:00–12:00 | Nearby service points, inbox and preferences | Demo service data; manual district fallback; notifications are optional. |
| 12:00–13:30 | Selected optional capabilities | Show only features verified on the presentation device: pass, offers, widget or shake visibility. |
| 13:30–15:00 | Restart/rotation, architecture and conclusions | Room persistence, test evidence, known limits, AI acknowledgement and future work. |

The official video limit is 20 minutes. Leave time for each member's actual explanation and avoid padding the video with long build or download waits.

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

## Presentation preparation

- Record with the final tested APK and readable emulator/device dimensions.
- Use the team's actual names and assigned group number. Do not fabricate work allocation.
- Explain the code paths and trade-offs in your own words; review the AI-assisted implementation first.
- Mention that there is no real-money, Octopus, FPS or real-ticket integration.
- Include the concise acknowledgement from `AI_USE.md`, adjusted for any additional tools the team uses.
- A script and screenshots are preparation materials; they are not the required recorded video.
