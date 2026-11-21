# Prijavnice – razvrščanje prijavnic

Android aplikacija, ki pomaga razvrstiti kup natipkanih prijavnic najprej po **programih**, nato znotraj
vsakega programa **po abecedi** učencev. Vse teče na telefonu, brez interneta.

## Namestitev

1. Odpri na telefonu [Releases → najnovejša verzija](../../releases/tag/najnovejsa) in prenesi `Prijavnice.apk`
   (ali pa APK iz zavihka **Actions** → zadnja gradnja → *Prijavnice-apk*).
2. Odpri datoteko in dovoli nameščanje iz tega vira. Nove verzije se namestijo čez staro, podatki ostanejo.

## Uporaba

**1. Slikanje.** Slikaj prijavnice eno za drugo. Aplikacija prebere program (vedno na vrhu prijavnice) in ime
učenca (poišče oznako *Učenec*, *Ime in priimek*, *Dijak/inja* …). Preveri/popravi in tapni *Shrani*.
Prijavnico po slikanju položi **z obrazom navzgor na vrh kupa**.

**2. Razdeli po programih.** Kup pred tabo ima na vrhu zadnjo slikano. Aplikacija za vsako vrhnjo prijavnico
pove (glas + velik napis), na kateri kupček programa gre. Pokaže tudi ime, da preveriš, ali imaš pravo.

**3. Razvrsti po abecedi.** Izberi kupček programa. Aplikacija ve, v kakšnem vrstnem redu je, in vodi korak za korakom:

- vrhnjo prijavnico položi na pomožni kupček *1, 2, 3 …* ali takoj na **končni kup**,
- s pomožnega kupčka vrhnjo prenesi na končni kup,
- včasih cel pomožni kupček položi na drugega (brez obračanja).

Vse je ves čas z obrazom navzgor. Na koncu je končni kup urejen **od A (zgoraj) do Ž (spodaj)** po slovenski abecedi
(č, š, ž). Aplikacija sproti odlaga na končni kup, kar je le mogoče, zato je pomožnih kupčkov čim manj
(nikoli več kot dolžina najdaljšega »padajočega« zaporedja imen, kar je teoretični minimum).

**Naprej / nazaj:** veliki gumbi, tap na sredino zaslona, **tipka za glasnost gor = naprej, dol = nazaj**
(deluje tudi z gumbom na slušalkah), ali glasovno »naprej«, »nazaj«, »ponovi« (ikona 🎤).

Napredek se sproti shranjuje – aplikacijo lahko zapreš in nadaljuješ kasneje.

### Brez interneta

- Branje besedila: Google ML Kit, model je vključen v APK.
- Govor: Android »pretvorba besedila v govor«. Če slovenski glas ni nameščen, ga dodaj v nastavitvah telefona.
- Glasovni ukazi: uporabi prepoznavo na napravi, če jo telefon za slovenščino ima; sicer jo Android morda pošlje
  na splet. Tipke za glasnost in gumbi delujejo vedno brez interneta.

## Testni podatki

`test-data/testne_prijavnice.pdf` vsebuje 60 izmišljenih natipkanih prijavnic (4 različne postavitve, 8 programov).
Odpri jih na računalniku ali natisni in jih slikaj z aplikacijo. Nove ustvariš z:

```
python3 tools/generate_test_forms.py 60 test-data
```

## Razvoj

- `core/` – čista Kotlin logika (slovenska abeceda, načrt razvrščanja, branje polj iz OCR) z enotskimi testi:
  `./gradlew :core:test`
- `app/` – Android aplikacija (Jetpack Compose, CameraX, ML Kit). Gradnja: `./gradlew :app:assembleDebug`
- GitHub Actions ob vsakem pushu zgradi APK; ob pushu na `main` ga objavi pod *Releases → najnovejsa*.
