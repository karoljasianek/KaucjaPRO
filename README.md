# KaucjaPRO

Aplikacja na Androida do pilnowania kaucji za butelki i puszki (polski system kaucyjny).

## Co potrafi

| Funkcja | Jak działa |
|---|---|
| **Skan butelek** | Aparat czyta kod kreskowy (EAN/UPC) przez ML Kit. Znany produkt dodaje się od razu (+ przycisk „Cofnij”). Nieznany: aplikacja pyta Open Food Facts o nazwę i typ opakowania, pokazuje podpowiedź do potwierdzenia i **zapamiętuje produkt**. |
| **Skan paragonów** | Zdjęcie (lub plik z galerii) → rozpoznawanie tekstu na telefonie (ML Kit) → aplikacja szuka linii ze słowem „kaucja”, pokazuje wykryte pozycje i kwotę **do sprawdzenia przed zapisem**. Można też wpisać kwotę ręcznie. |
| **Suma kaucji** | Ekran Start: łączna kaucja „do odzyskania” + podział na butelki / paragony / już odzyskane. Przycisk „Oddałem wszystko” oraz oznaczanie pojedynczych wpisów jako zwrócone (Historia). |
| **Mapa kaucjomatów** | Mapa OpenStreetMap (bez klucza API) z Twoją lokalizacją i najbliższymi kaucjomatami z danych OSM (`vending=bottle_return`). Opcja „Pokaż sklepy” dodaje supermarkety (pomarańczowe znaczniki) jako punkty, gdzie warto zapytać o zwrot. |

Stawki domyślne (zmienisz je w ustawieniach, ikona zębatki na Starcie): **0,50 zł** PET do 3 l i puszki do 1 l, **1,00 zł** szkło do 1,5 l. Dla innych opakowań wybierz „Inne opakowanie” i wpisz kwotę.

## Jak zdobyć plik APK

### Opcja A: bez instalowania czegokolwiek (GitHub Actions)

1. Załóż darmowe konto na github.com i utwórz **nowe repozytorium** (np. `KaucjaPRO`).
2. Wgraj całą zawartość tego folderu do repozytorium **jako rozpakowane pliki** (nie jako zip), **razem z ukrytym folderem `.github`**. Najwygodniej przez `git push`.
3. Wejdź w zakładkę **Actions** → *Build KaucjaPRO APK* → **Run workflow** (uruchamia się też samo po każdym pushu i pull requeście).
4. Po ok. 5–10 minutach wejdź w zakończony przebieg i pobierz artefakt **KaucjaPRO-apk** (zip z plikiem `KaucjaPRO.apk`).

### Opcja B: Android Studio

1. Zainstaluj [Android Studio](https://developer.android.com/studio), wybierz *Open* i wskaż ten folder.
2. Poczekaj na synchronizację Gradle (pobierze SDK i biblioteki).
3. *Build → Build Bundle(s) / APK(s) → Build APK(s)*. Plik: `app/build/outputs/apk/debug/app-debug.apk`.

Z linii poleceń (przy zainstalowanym Android SDK i Javie 17): `./gradlew assembleDebug`.

## Instalacja na telefonie

Przenieś `KaucjaPRO.apk` na telefon, otwórz go i zezwól na instalację z tego źródła (Ustawienia → Aplikacje → Specjalny dostęp → Instalowanie nieznanych aplikacji). To APK **debugowy**, podpisany kluczem debugowym: wystarcza do własnego użytku. Do publikacji w Google Play potrzebny jest własny klucz podpisujący i wersja release.

## Stan weryfikacji

Projekt kompiluje się (`./gradlew assembleDebug`), testy jednostkowe parsera paragonów przechodzą (`./gradlew testDebugUnitTest`, 8/8), a lint nie zgłasza błędów (tylko ostrzeżenia, m.in. o nowszych wersjach bibliotek). Każdy push i pull request jest automatycznie budowany przez GitHub Actions (budowa + testy + lint).

**Nie sprawdzono:** działania na prawdziwym urządzeniu ani jakości OCR na prawdziwych paragonach.

## Dane i prywatność

- Historia, produkty i ustawienia są zapisane **tylko na telefonie** (`kaucja_state.json` w pamięci aplikacji). Brak kont i chmury.
- Zdjęcia paragonów są przetwarzane na urządzeniu, a plik tymczasowy usuwany po odczycie.
- Sieć jest używana w dwóch miejscach: Open Food Facts (wysyłany jest **tylko kod kreskowy**) oraz Overpass API/OpenStreetMap (wysyłany jest prostokąt współrzędnych wokół wyszukiwanego miejsca) plus kafelki mapy z serwerów OSM.

## Znane ograniczenia

- Mapa jest tak kompletna, jak dane w OpenStreetMap. W wielu miejscowościach kaucjomaty mogą jeszcze nie być naniesione; wtedy użyj „Pokaż sklepy” i zapytaj na miejscu.
- Kod kreskowy nie zawiera informacji o kaucji, dlatego typ opakowania jest zgadywany z Open Food Facts i **zawsze potwierdzany** przy pierwszym skanie danego produktu.
- Aplikacja nie wykrywa powtórzeń: jeśli kaucja z paragonu dotyczy tych samych butelek, które skanujesz, dodaj ją tylko raz.
- Linie „zwrot kaucji” i kwoty ujemne na paragonach są pomijane.

## Pomysły na rozwój

Własne punkty zwrotu dodawane z mapy, eksport historii do CSV, powiadomienie „masz X zł kaucji do oddania”, kopia zapasowa, podpisany build release.

## Struktura

```
app/src/main/java/pl/kaucjapro/app/
  MainActivity.kt, KaucjaViewModel.kt
  data/   Models.kt, Store.kt             (modele, zapis JSON)
  ocr/    Ocr.kt, ReceiptParser.kt        (ML Kit OCR + wyciąganie kaucji)
  net/    ProductLookup.kt, OverpassClient.kt
  ui/     ekrany Compose (Start, Butelki, Paragony, Mapa, Historia), aparat, dialogi
```
