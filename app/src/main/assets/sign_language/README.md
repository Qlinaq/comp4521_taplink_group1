# Sign-language clips

The sign-language screen plays `file:///android_asset/sign_language/<chapter>.mp4` and enables the
player only when that exact file exists. No footage ships with this repository, so every chapter
currently shows **"No video attached yet"** and relies on its written transcript, which is complete
on its own.

To attach footage, drop one file per chapter here and rebuild — no code change is needed:

| File name | Chapter |
| --- | --- |
| `topup.mp4` | Top up |
| `pay.mp4` | Pay a merchant |
| `pass.mp4` | Buy a monthly pass |
| `password.mp4` | Reset a password |

Requirements:

- **H.264/AAC in an MP4 container**, 720×1280 or 1080×1920, under ~10 MB each so the APK stays small.
- Burned-in **Chinese and English subtitles** — a deaf user who reads English should not depend on
  the audio track, and the clip should stay useful with sound off.
- Recorded by a qualified Hong Kong Sign Language interpreter. A clip in another sign language, or
  one recorded without an interpreter, would mislead users and must not be published.
- The transcript in `SignLanguageScreen.kt` is the source of truth. If a clip changes the steps,
  change the transcript in the same commit.

The department that supplies the clips owns the copyright and the review; this repository only
provides the player slot.
