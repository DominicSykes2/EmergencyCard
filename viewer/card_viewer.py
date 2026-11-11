#!/usr/bin/env python3
"""Emergency Card viewer for PC.

(c) 2026 Dominic Sykes. All rights reserved.

Opens an EmergencyCard.json file saved by the Android app ("Save file" button)
and shows it as a clean, printable page in your web browser. Standard library only.

    python card_viewer.py EmergencyCard.json
    python card_viewer.py                      (asks you to pick or type the file)
"""
import html
import json
import os
import sys
import tempfile
import webbrowser

FIELDS = [
    ("Date of birth", "dob"),
    ("Blood type", "bloodType"),
    ("Medical conditions", "conditions"),
    ("Allergies", "allergies"),
    ("Medications and doses", "medications"),
    ("Doctor", "doctor"),
    ("Insurance", "insurance"),
    ("Accounts and important info", "accounts"),
    ("Notes", "notes"),
]

CSS = """
body{font:16px/1.5 system-ui,Segoe UI,Arial,sans-serif;background:#f4f4f4;color:#1c1c1c;margin:0;padding:24px}
.card{max-width:720px;margin:auto;background:#fff;border:3px solid #c62828;border-radius:16px;padding:24px 28px}
h1{margin:0 0 4px;color:#c62828;font-size:1.6rem;letter-spacing:.04em}
h2{margin:0 0 16px;font-size:1.3rem}
dl{margin:0} dt{font-size:.8rem;text-transform:uppercase;letter-spacing:.06em;color:#666;margin-top:14px}
dd{margin:2px 0 0;white-space:pre-wrap}
table{border-collapse:collapse;width:100%;margin-top:6px} td,th{text-align:left;padding:6px 8px;border-bottom:1px solid #ddd}
footer{text-align:center;color:#777;font-size:.85rem;margin-top:18px}
@media print{body{background:#fff;padding:0}.card{border-width:2px}}
"""


def render(card):
    e = lambda v: html.escape(str(v or ""))
    parts = ["<!doctype html><html lang='en'><head><meta charset='utf-8'>",
             "<meta name='viewport' content='width=device-width,initial-scale=1'>",
             "<title>Emergency Card</title><style>", CSS, "</style></head><body><div class='card'>",
             "<h1>EMERGENCY CARD</h1>", f"<h2>{e(card.get('fullName')) or 'Name not set'}</h2><dl>"]
    for label, key in FIELDS:
        val = str(card.get(key) or "").strip()
        if val:
            parts.append(f"<dt>{html.escape(label)}</dt><dd>{html.escape(val)}</dd>")
    contacts = [c for c in card.get("contacts", []) if (c.get("name") or c.get("phone"))]
    if contacts:
        parts.append("<dt>Emergency contacts</dt><dd><table><tr><th>Name</th><th>Relationship</th><th>Phone</th></tr>")
        for c in contacts:
            parts.append(f"<tr><td>{e(c.get('name'))}</td><td>{e(c.get('relation'))}</td><td>{e(c.get('phone'))}</td></tr>")
        parts.append("</table></dd>")
    parts.append("</dl></div><footer>Emergency Card viewer &copy; 2026 Dominic Sykes. All rights reserved.</footer></body></html>")
    return "".join(parts)


def pick_file():
    try:
        import tkinter
        from tkinter import filedialog
        root = tkinter.Tk()
        root.withdraw()
        path = filedialog.askopenfilename(title="Open EmergencyCard.json",
                                          filetypes=[("Emergency Card", "*.json"), ("All files", "*.*")])
        root.destroy()
        if path:
            return path
    except Exception:  # noqa: BLE001
        pass
    return input("Path to EmergencyCard.json: ").strip().strip('"')


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    no_open = "--no-open" in sys.argv
    path = args[0] if args else pick_file()
    if not path or not os.path.isfile(path):
        sys.exit(f"Could not find the file: {path!r}")
    try:
        with open(path, encoding="utf-8-sig") as f:
            card = json.load(f)
    except (OSError, ValueError) as e:
        sys.exit(f"That doesn't look like an Emergency Card file: {e}")
    out = os.path.splitext(path)[0] + ".html"
    try:
        with open(out, "w", encoding="utf-8") as f:
            f.write(render(card))
    except OSError:
        out = os.path.join(tempfile.gettempdir(), "EmergencyCard.html")
        with open(out, "w", encoding="utf-8") as f:
            f.write(render(card))
    print(f"Card page saved to {out}")
    if not no_open:
        webbrowser.open("file:///" + out.replace("\\", "/"))


if __name__ == "__main__":
    main()
