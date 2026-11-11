# Emergency Card

A simple Android app that keeps your key emergency information in one place and lets someone read it by tapping phones together (NFC).

- Name, date of birth, blood type
- Medical conditions, allergies, medications and doses, doctor, insurance
- Emergency contacts (tap to call)
- Where important accounts and papers are (no passwords)

No passcode, no account, no internet. Everything stays on your phone. Sharing by tap is OFF until you switch it on.

## Get the app
1. Put this folder in a GitHub repo and push to `main`.
2. GitHub Actions builds the app. Download `app-debug.apk` from the repo's Releases page and install it on your phone.

## Use it
- **Edit** tab: fill in your details and Save.
- **My card** tab: switch on "Let other phones read my card" (keep the screen on). Another phone held against the back of yours receives the card as text. If they also have this app, the **Read** tab shows it neatly.
- **Save file** puts `EmergencyCard.json` in Downloads/EmergencyCard. On a PC, run `viewer/card_viewer.py` on that file to see a clean, printable page.

## Please note
Anyone who can unlock your phone can see the card, and anyone holding an NFC phone to yours can read it while sharing is on. Don't store passwords, full bank or card numbers.

---
© 2026 Dominic Sykes. All rights reserved. See LICENSE.
