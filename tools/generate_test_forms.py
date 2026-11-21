#!/usr/bin/env python3
"""Ustvari testne natipkane prijavnice (PNG + en PDF) in JSON z vrsticami besedila.

Uporaba:  python3 tools/generate_test_forms.py [stevilo] [izhodna_mapa]

PDF lahko odpreš na računalniku/natisneš in prijavnice slikaš z aplikacijo.
JSON (forms.json) uporabljajo enotski testi za preverjanje branja programa in imena.
"""
import json
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont

PROGRAMS = [
    "Logistični tehnik", "Kozmetični tehnik", "Frizer", "Ekonomski tehnik", "Trgovec",
    "Administrator", "Gastronomija in turizem", "Predšolska vzgoja", "Zdravstvena nega",
    "Računalnikar",
]
FIRST = ["Ana", "Žiga", "Luka", "Špela", "Nika", "Čarna", "Jan", "Tjaša", "Maja", "Žan", "Eva",
         "Matic", "Urška", "Nejc", "Lara", "Tim", "Zala", "Gašper", "Klara", "Ožbej", "Ema", "Črt",
         "Neža", "Aljaž", "Sara", "Jaka", "Pia", "Blaž", "Lana", "Rok"]
LAST = ["Novak", "Horvat", "Kranjc", "Zupančič", "Kovačič", "Potočnik", "Mlakar", "Kos", "Vidmar",
        "Golob", "Turk", "Božič", "Kralj", "Zupan", "Bizjak", "Hribar", "Korošec", "Rozman",
        "Žagar", "Čeh", "Šinkovec", "Erjavec", "Babič", "Jerman", "Šuštar", "Čuk", "Ambrožič",
        "Žnidaršič", "Dolenc", "Pirc"]
TEACHERS = ["mag. Mojca Leban", "Janez Petek", "dr. Irena Kos", "Marko Vovk", "Tina Gorenc"]
DESCS = [
    "Učenec je opravil praktično nalogo v skladu z navodili in pokazal dobro razumevanje snovi.",
    "Projektna naloga obsega pripravo izdelka, predstavitev in zagovor pred komisijo.",
    "Prijava na zaključni izpit iz strokovnega modula. Opombe mentorja so priložene.",
    "Delo je bilo opravljeno samostojno, z manjšimi popravki po posvetu z mentorjem.",
]

FONT_DIR = "/usr/share/fonts/truetype"
FONTS = {
    "sans": f"{FONT_DIR}/dejavu/DejaVuSans.ttf",
    "sansb": f"{FONT_DIR}/dejavu/DejaVuSans-Bold.ttf",
    "serif": f"{FONT_DIR}/liberation/LiberationSerif-Regular.ttf",
    "serifb": f"{FONT_DIR}/liberation/LiberationSerif-Bold.ttf",
    "lib": f"{FONT_DIR}/liberation/LiberationSans-Regular.ttf",
    "libb": f"{FONT_DIR}/liberation/LiberationSans-Bold.ttf",
}
W, H = 1240, 1754  # A4 pri 150 dpi


def font(key, size):
    return ImageFont.truetype(FONTS[key], size)


class Page:
    def __init__(self):
        self.img = Image.new("RGB", (W, H), "white")
        self.d = ImageDraw.Draw(self.img)
        self.lines = []

    def text(self, x, y, s, f, center=False):
        if center:
            w = self.d.textlength(s, font=f)
            x = int((W - w) / 2)
        bbox = self.d.textbbox((x, y), s, font=f)
        self.d.text((x, y), s, font=f, fill="black")
        self.lines.append({"text": s, "left": bbox[0], "top": bbox[1], "right": bbox[2], "bottom": bbox[3]})
        return bbox

    def wrap(self, x, y, s, f, width):
        words, line = s.split(), ""
        for w in words:
            t = (line + " " + w).strip()
            if self.d.textlength(t, font=f) > width:
                y = self.text(x, y, line, f)[3] + 10
                line = w
            else:
                line = t
        if line:
            y = self.text(x, y, line, f)[3] + 10
        return y

    def hline(self, x1, y, x2):
        self.d.line((x1, y, x2, y), fill="black", width=2)


def layout_a(p, f):
    """Naslov programa na sredini, polja z dvopičjem."""
    p.text(0, 90, f["program"], font("sansb", 64), center=True)
    p.hline(100, 190, W - 100)
    y = 250
    for lab, val in [("Učenec:", f["name"]), ("Učitelj:", f["teacher"]), ("Datum:", f["date"]),
                     ("Ocena:", f["grade"])]:
        p.text(120, y, lab, font("sansb", 34))
        p.text(380, y, val, font("sans", 34))
        y += 80
    p.text(120, y + 20, "Opis:", font("sansb", 34))
    p.wrap(120, y + 80, f["desc"], font("sans", 30), W - 240)


def layout_b(p, f):
    """'PROGRAM: XY' levo zgoraj, ime pod oznako, dva stolpca."""
    p.text(90, 80, "PROGRAM: " + f["program"].upper(), font("libb", 52))
    p.text(90, 220, "Ime in priimek učenca", font("lib", 28))
    p.text(90, 260, f["name"], font("libb", 40))
    p.hline(90, 315, 600)
    p.text(700, 220, "Datum prijave", font("lib", 28))
    p.text(700, 260, f["date"], font("lib", 40))
    p.text(90, 380, "Mentor / učitelj", font("lib", 28))
    p.text(90, 420, f["teacher"], font("lib", 36))
    p.text(700, 380, "Ocena", font("lib", 28))
    p.text(700, 420, f["grade"], font("lib", 36))
    p.text(90, 540, "Opis naloge", font("lib", 28))
    p.wrap(90, 590, f["desc"], font("lib", 30), W - 180)


def layout_c(p, f):
    """Serifni, program levo, datum desno v isti višini, Dijak/inja z vrednostjo spodaj."""
    p.text(100, 100, f["program"], font("serifb", 58))
    p.text(900, 230, "Datum: " + f["date"], font("serif", 30))
    y = p.wrap(100, 230, f["desc"], font("serif", 32), 720)
    y += 60
    p.text(100, y, "Dijak/inja:", font("serifb", 34))
    p.text(100, y + 50, f["name"], font("serif", 40))
    y += 150
    p.text(100, y, "Učitelj/ica: " + f["teacher"], font("serif", 34))
    p.text(700, y, "Ocena: " + f["grade"], font("serif", 34))
    p.text(100, y + 300, "Podpis učenca: ____________________", font("serif", 28))


def layout_d(p, f):
    """Program v okvirju, tabela; ime učenca v drugi vrstici tabele."""
    p.d.rectangle((80, 60, W - 80, 200), outline="black", width=3)
    p.text(0, 95, f["program"].upper(), font("sansb", 56), center=True)
    rows = [("Šolsko leto", "2025/2026"), ("Ime in priimek", f["name"]), ("Učitelj", f["teacher"]),
            ("Datum", f["date"]), ("Ocena", f["grade"])]
    y = 260
    for lab, val in rows:
        p.d.rectangle((80, y, W - 80, y + 80), outline="black", width=2)
        p.text(100, y + 20, lab, font("sans", 32))
        p.text(520, y + 20, val, font("sans", 32))
        y += 80
    p.text(100, y + 40, "Opis:", font("sansb", 32))
    p.wrap(100, y + 100, f["desc"], font("sans", 28), W - 200)
    p.text(100, H - 200, "Opombe: Učenec prinese izdelek na zagovor.", font("sans", 26))


LAYOUTS = [layout_a, layout_b, layout_c, layout_d]


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 60
    out = sys.argv[2] if len(sys.argv) > 2 else "test-data"
    os.makedirs(os.path.join(out, "png"), exist_ok=True)
    rnd = random.Random(42)
    programs = PROGRAMS[:8]
    forms, pages = [], []
    used = set()
    for i in range(n):
        while True:
            name = f"{rnd.choice(FIRST)} {rnd.choice(LAST)}"
            if name not in used:
                used.add(name)
                break
        f = {
            "program": rnd.choice(programs),
            "name": name,
            "teacher": rnd.choice(TEACHERS),
            "date": f"{rnd.randint(1, 28)}. {rnd.randint(1, 12)}. 2026",
            "grade": str(rnd.randint(2, 5)),
            "desc": rnd.choice(DESCS),
        }
        p = Page()
        layout = LAYOUTS[i % len(LAYOUTS)]
        layout(p, f)
        fn = f"prijavnica_{i + 1:03d}.png"
        p.img.save(os.path.join(out, "png", fn), optimize=True)
        pages.append(p.img)
        forms.append({**f, "file": fn, "layout": layout.__name__, "lines": p.lines})
    pages[0].save(os.path.join(out, "testne_prijavnice.pdf"), save_all=True, append_images=pages[1:],
                  resolution=150)
    with open(os.path.join(out, "forms.json"), "w", encoding="utf-8") as fh:
        json.dump(forms, fh, ensure_ascii=False, indent=1)
    print(f"Ustvarjenih {n} prijavnic v {out}")


if __name__ == "__main__":
    main()
