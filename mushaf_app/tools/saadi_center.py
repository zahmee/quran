"""Turn Tafsir Center's per-ayah `tafsir_saadi` rows into the passages the app shows.

The Center's dataset (CC BY 4.0, https://tafsir.net) gives one row per ayah, and each row mixes the
surah heading, a lead-in, the ayah in Imlaei script, the explanation and the editor's notes. Splitting
the book by ayah also left two side effects: "the Sheikh did not explain this ayah" on ayahs he
explains together with their neighbours, and sentences repeated or moved across ayah boundaries.

What this module does to a row, in order:
  1. applies the explicit CORRECTIONS below, each of which must match exactly as often as it says;
  2. takes the notes out, keeping an anchor for the ones readers are shown (see `note_kind`);
  3. drops the ayah text, because the passage shows the app's approved mushaf text instead;
  4. splits what is left into heading, lead-in, explanation and closing units.

Then, surah by surah, it aligns the units with the QuranEnc copy of the same book. That copy is used
for its structure only: which ayahs are explained together, and which text a repeat is. Words are never
taken from it, apart from the four SUPPLEMENTS the Center's copy lacks, which are shown and credited as
such. Anything the alignment says is only in the Center's copy and also occurs nearby in the Center's
copy is a repeat and goes; text that merely sits one ayah away stays where the Center put it.

`build_rows` fails on anything it does not recognise rather than shipping it. Needs rapidfuzz.
"""

from __future__ import annotations

import re
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field

from rapidfuzz.distance import Indel

_MARK_CLASS = "ً-ٰٟۖ-ۭ"
MARKS = re.compile(f"[{_MARK_CLASS}ـ]")
TOKEN = re.compile("[ء-غـ-ٟٮ-ۓە-ۭ]+|\\d+")
NUMBER_MARK = re.compile(r"\{\s*\d+(?:\s*[-–ـ]\s*\d+)*\s*\}")
# A note may run over several lines, and one source note is left unclosed.
NOTE = re.compile(r"¬[^¥¬]*¥|¬[^\n¥¬]*")
AYAH_MARK = re.compile(r"\(\s*\d+\s*\)")
BRACED = re.compile(r"\{[^{}]*\}")
PLACEHOLDER = re.compile(r"لم\s+يفسر(?:ها)?\s+الشيخ")
FOLD = str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي", "ئ": "ي", "ؤ": "و", "ة": "ه", "ء": None,
                      "ک": "ك", "ی": "ي", "ھ": "ه"})

# Private-use code points stand in for notes while the text is edited; they are not tokens, so they
# survive alignment, and they leave with the text around them if that text turns out to be a repeat.
_NOTE_BASE = 0xF0000
_NOTE_ANCHOR = re.compile("[\U000F0000-\U000FFFFD]")
_BLOCK_LINE = "￹"

# How the app finds a kept note's place in the text: ⟦1⟧ is the row's first note.
ANCHOR_OPEN, ANCHOR_CLOSE = "⟦", "⟧"

KIND_TITLE = "اسم السورة"
KIND_PLACE = "مكان نزول السورة"
KIND_BASMALA = "البسملة"
KIND_QURAN = "المقطع"
KIND_EXPLANATION = "تفسير آية"
KIND_LEAD = "تمهيد للمقطع"
KIND_CLOSING = "خاتمة السورة"
KIND_DIVIDER = "نجوم"
KIND_SUPPLEMENT = "تكملة من الموسوعة"

OWN_KINDS = {"تفسير آية", "تكملة تفسير الآية"}

_TITLE = re.compile(r"^(?:تفسير\s+)?سورة\s")
_PLACE = re.compile(r"^\[?(?:وهي)?\]?\s*(?:مكية|مدنية|مدينة)")
_BASMALA_BARE = "بسم الله الرحمن الرحيم"
_DIVIDER = re.compile(r"^\*[\s*]*$")
_CLOSING = re.compile(r"^(?:تم|تمت|آخر)\s+(?:تفسير|سورة)|^تم\s+تفسيرها|^تمت\b|^انتهى\b")

# (ayah, wrong text in the Center's row, right text, why). A row may carry several; each must occur
# exactly once in its row, or the build stops. Hand-checked against the Quran or two other copies of
# the book; the list is `reports/saadi_full/saadi_confirmed_differences.csv` outside the repository.
CORRECTIONS: list[tuple[str, str, str, str]] = [
    # Working marks the dataset left in the text.
    ("5:26", " (ككك 2)", "", "علامة عمل"),
    ("25:2", " (ككك 1)", "", "علامة عمل"),
    ("27:6", " (ككك 2)", "", "علامة عمل"),
    ("47:19", " (ككك 1)", "", "علامة عمل"),
    ("58:4", " (ككك 4)", "", "علامة عمل"),
    ("36:30", " [ممم تحتاج مزيد بحث]", "", "علامة عمل"),
    # A footnote that was left in the body rather than marked as a note.
    ("27:19", "التبسُّمُ(1)؛", "التبسُّمُ¬أخرجه أحمد (4/ 190)، والترمذي (3645)، والحديث صححه الألباني في \"مختصر الشمائل\" (194).¥؛", "حاشية داخل المتن"),
    ("27:19", "\n__________\n(1) أخرجه أحمد (4/ 190)، والترمذي (3645)، والحديث صححه الألباني في \"مختصر الشمائل\" (194).", "", "حاشية داخل المتن"),
    # Footnote numbers whose footnotes the dataset does not carry.
    ("4:25", " [(1)]", "", "رقم حاشية بلا حاشية"),
    ("25:9", " (1)", "", "رقم حاشية بلا حاشية"),
    ("25:16", " (1)", "", "رقم حاشية بلا حاشية"),
    ("49:9", " (1)", "", "رقم حاشية بلا حاشية"),
    ("49:10", " (2)", "", "رقم حاشية بلا حاشية"),
    ("49:10", " (3)", "", "رقم حاشية بلا حاشية"),
    # Quran blocks cut or bracketed badly. Their text is replaced anyway; they only have to parse.
    ("2:133", "أَمْ كُنْتُمْ شُهَدَاءَ إِذْ", "{أَمْ كُنْتُمْ شُهَدَاءَ إِذْ", "قوس مفقود"),
    ("2:280", "وَإِنْ كَانَ ذُو عُسْرَةٍ", "{وَإِنْ كَانَ ذُو عُسْرَةٍ", "قوس مفقود"),
    ("4:61", "عَنْكَ {صُدُودًا", "عَنْكَ صُدُودًا", "قوس زائد"),
    ("40:33", "مِنْ {هَادٍ", "مِنْ هَادٍ", "قوس زائد"),
    ("55:52", "زَوْجَانِ {(52)}", "زَوْجَانِ (52)}", "قوس زائد"),
    ("72:17", "(17)}](2).", "(17)}.", "رقم حاشية بلا حاشية"),
    ("89:7", "رَبَّكَ لَبِالْمِرْصَادِ}. وفي (ب): ذكر الآيات.¥ (7)}.\n", "", "بقية حاشية من صف آخر"),
    # Copying slips outside Quran quotes.
    ("2:165", "الموصولة إل ", "الموصلة إلى ", "تصحيح نسخ"),
    ("2:165", " ذر هنا", " ذكر هنا", "تصحيح نسخ"),
    ("2:245", "العلي الكريم", "الملي الكريم", "تصحيح نسخ"),
    ("18:21", "وعاقتبه", "وعاقبته", "تصحيح نسخ"),
    ("19:88", "عزيز ابن الله", "عزير ابن الله", "تصحيح نسخ"),
    ("25:12", "ووصولها إليها", "ووصولها إليهم", "تصحيح نسخ"),
    ("52:1", "الصلاة السلام", "الصلاة والسلام", "تصحيح نسخ"),
    ("24:1", "وهي مدينة", "وهي مدنية", "تصحيح نسخ"),
    # Quran quotes inside the explanation that do not read as the Quran does.
    ("4:18", "فأولئك", "أولئك", "اقتباس قرآني"),
    ("10:98", "فلمَّا جاءهم بأسُنا", "فلمَّا رَأَوْا بأسُنا", "اقتباس قرآني"),
    ("11:108", "مجذودٍ", "مجذوذٍ", "اقتباس قرآني"),
    ("13:14", "كفروا وكذَّبوا بآياتنا", "كذَّبوا بآياتنا واستكبروا عنها", "اقتباس قرآني"),
    ("19:18", "ابنةَ عمرانَ", "ابنتَ عمرانَ", "اقتباس قرآني"),
    ("19:18", "فَنَفَخْنا فيها", "فَنَفَخْنا فيه", "اقتباس قرآني"),
    ("19:18", "فنَفَخْنا فيه", "فنَفَخْنا فيها", "اقتباس قرآني"),
    ("20:123", "فَمَن اتَّبَع هُدايَ", "فَمَن تَبِعَ هُدايَ", "اقتباس قرآني"),
    ("21:34", "تربَّصوا به", "نَتَرَبَّصُ به", "اقتباس قرآني"),
    ("41:51", "وإن مسَّه", "وإذا مسَّه", "اقتباس قرآني"),
    ("43:45", "رسِلنا", "رسُلنا", "اقتباس قرآني"),
    ("43:57", "يَصُدُّون", "يَصِدُّون", "اقتباس قرآني"),
    ("57:25", "ولقد أرْسَلْنا", "لقد أرْسَلْنا", "اقتباس قرآني"),
    ("60:7", "فعسى الله", "عسى الله", "اقتباس قرآني"),
    # This one quote alone is typed in Uthmani glyphs; the rest of the book quotes in Imlaei.
    ("57:23", "فَإِذَا مَسَّ ٱلۡإِنسَٰنَ ضُرّٞ دَعَانَا ثُمَّ إِذَا خَوَّلۡنَٰهُ نِعۡمَةٗ مِّنَّا قَالَ إِنَّمَآ أُوتِيتُهُۥ عَلَىٰ عِلۡمِۭۚ بَلۡ هِيَ فِتۡنَةٞ وَلَٰكِنَّ أَكۡثَرَهُمۡ لَا يَعۡلَمُونَ",
     "فَإِذَا مَسَّ الْإِنْسَانَ ضُرٌّ دَعَانَا ثُمَّ إِذَا خَوَّلْنَاهُ نِعْمَةً مِنَّا قَالَ إِنَّمَا أُوتِيتُهُ عَلَى عِلْمٍ بَلْ هِيَ فِتْنَةٌ وَلَكِنَّ أَكْثَرَهُمْ لَا يَعْلَمُونَ", "اقتباس قرآني"),
]


@dataclass(frozen=True)
class Supplement:
    """Text from the QuranEnc copy that the Center's copy lacks, placed and credited as such.

    It runs from `starts_with` to the end of the QuranEnc row that holds it.
    """

    verse_key: str
    starts_with: str
    # "after_body" follows the ayah's explanation; "lead" stands before its Quran text; "inside"
    # splits the explanation right after `after_text`.
    place: str
    words: int
    after_text: str = ""


SUPPLEMENTS = [
    Supplement("2:101", "ولما كان من العوائد القدرية", "after_body", 63),
    Supplement("2:143", "فإن قيل كيف يقبل حكمهم", "inside", 166, after_text="وما شهدت له بالردِّ فهو مردود."),
    Supplement("2:181", "ولما كان الموصي قد يمتنع", "lead", 19),
    Supplement("4:117", "ولهذا بيَّن الله قبح ضلال المشركين", "lead", 7),
]


def bare(text: str) -> str:
    return MARKS.sub("", unicodedata.normalize("NFC", text))


def fold(text: str) -> str:
    return bare(text).translate(FOLD)


def skeleton(text: str) -> str:
    """Letters only, without the alef: quotes write السماوات and السموات alike."""
    return "".join(fold(t) for t in TOKEN.findall(text) if not t.isdigit()).replace("ا", "")


def vowel_ratio(text: str) -> float:
    letters = sum(1 for c in text if "ء" <= c <= "ي" and c != "ـ")
    marks = sum(1 for c in text if "ً" <= c <= "ْ")
    return marks / letters if letters else 0.0


def note_kind(note: str) -> str:
    """What a note is to a reader: only corrections and sources are shown, variants stay out."""
    plain = bare(note).strip()
    # A pointer back to a page of the print edition means nothing here.
    if re.fullmatch(r"(?:تقدم|سبق) تخريجه(?:\s*\(?ص?\s*\(?\d+\)?\)?)?\.?", plain):
        return "print_page"
    # "So in both manuscripts", with nothing after it, leaves a reader none the wiser.
    if re.fullmatch(r"كذا في النسختين\.?", plain):
        return "manuscript"
    if re.search(r"(?<!ب)الصواب|صوابه|خطأ|سبق قلم|مكيتها|والمثبت|سها المؤلف", plain):
        return "correction"
    if plain.startswith("كذا في النسختين") and "بخط" not in plain:
        return "correction"
    if re.match(r"(?:كذا\s+)?في\s+\(|كذا\s+(?:في|بالأصل)|زيادة|ما بين|سقط|ساقط", plain) or re.search(r"\((?:أ|ب)\)|النسختين|المخطوط", plain):
        return "manuscript"
    if re.search(r"أخرجه|رواه|صححه|ضعفه|حسنه|متفق عليه|البخاري|مسلم|سنن|مسند|الترمذي|أبو داود|صحيح|حديث", plain):
        return "hadith_source"
    if re.search(r"انظر|ينظر|تقدم تخريجه|سبق تخريجه", plain):
        return "see_also"
    return "other"


SHOWN_NOTE_KINDS = {"correction", "hadith_source", "see_also"}


@dataclass
class Unit:
    """One line of a row, with the role it plays in the passage."""

    sura: int
    aya: int
    role: str  # title, place, basmala, lead, lead_after, body, closing, divider
    text: str
    spans: list[tuple[int, int, str]] = field(default_factory=list)

    def tokenize(self) -> None:
        skipped = [m.span() for m in NUMBER_MARK.finditer(self.text)]
        self.spans = [
            (m.start(), m.end(), bare(m.group()))
            for m in TOKEN.finditer(self.text)
            if not any(a <= m.start() < b for a, b in skipped)
        ]


@dataclass
class ParsedRow:
    sura: int
    aya: int
    units: list[Unit]
    placeholder: bool


def apply_corrections(rows: dict[str, str]) -> dict[str, str]:
    # NFC settles the order of a letter's vowel and shadda, which the source does not keep uniform.
    fixed = {key: unicodedata.normalize("NFC", text) for key, text in rows.items()}
    for key, wrong, right, _ in CORRECTIONS:
        wrong_nfc = unicodedata.normalize("NFC", wrong)
        count = fixed[key].count(wrong_nfc)
        if count != 1:
            raise ValueError(f"Correction for {key} matched {count} times, expected once: {wrong!r}")
        fixed[key] = fixed[key].replace(wrong_nfc, unicodedata.normalize("NFC", right))
    return fixed


def _remove_ayah_blocks(text: str) -> str:
    """Replace each Quran block with a line of its own.

    A block carries (n) marks and full vowels; the opening letters of a surah, such as {حم (1)},
    carry the mark alone, since they have no vowels to write.
    """

    def swap(match: re.Match[str]) -> str:
        body = match.group()
        words = [t for t in TOKEN.findall(body) if not t.isdigit()]
        if AYAH_MARK.search(body) and (vowel_ratio(body) >= 0.45 or len(words) <= 2):
            return f"\n{_BLOCK_LINE}\n"
        return body

    # Blocks reach over a line break where a note used to sit, so this runs on the whole row. The
    # full stop some blocks end with goes with them; it must not swallow the line break that follows.
    return re.sub(r"\{[^{}]*\}[ \t]*\.?", swap, text)


def parse_row(sura: int, aya: int, raw: str, notes: list[tuple[int, int, str]]) -> ParsedRow:
    """Split one corrected row into units. Notes go into [notes]; their index is the anchor."""

    def take_note(match: re.Match[str]) -> str:
        notes.append((sura, aya, " ".join(match.group().strip("¬¥ \n").split())))
        return chr(_NOTE_BASE + len(notes) - 1)

    text = _remove_ayah_blocks(NOTE.sub(take_note, raw))
    lines = [line.strip() for line in text.split("\n") if line.strip()]

    def is_number_line(line: str) -> bool:
        return NUMBER_MARK.match(_NOTE_ANCHOR.sub("", line)) is not None

    first_number = next((i for i, line in enumerate(lines) if is_number_line(line)), None)
    placeholder = False
    units: list[Unit] = []
    after_block = False
    closed = False
    for index, line in enumerate(lines):
        if line == _BLOCK_LINE:
            after_block = True
            continue
        plain = bare(_NOTE_ANCHOR.sub("", line)).strip()
        if PLACEHOLDER.search(plain):
            placeholder = True
            continue
        if not TOKEN.search(plain) and not _DIVIDER.match(plain):
            continue
        heading_zone = first_number is None or index < first_number
        if _DIVIDER.match(plain):
            role = "divider"
        elif aya == 1 and heading_zone and _TITLE.match(plain):
            role = "title"
        elif aya == 1 and heading_zone and _PLACE.match(plain):
            role = "place"
        elif aya == 1 and heading_zone and plain == _BASMALA_BARE:
            role = "basmala"
        elif closed or _CLOSING.match(plain):
            # What follows "the end of this surah's tafsir" is the closing too, not more explanation.
            role = "closing"
            closed = True
        elif first_number is not None and index < first_number:
            role = "lead" if not after_block or plain.rstrip().endswith(":") else "lead_after"
        elif first_number is None and plain.rstrip().endswith(":") and len(plain.split()) <= 30:
            role = "lead"
        else:
            role = "body"
        units.append(Unit(sura, aya, role, line))
    return ParsedRow(sura, aya, units, placeholder)


def hunks_of(a: list[str], b: list[str]) -> list[list]:
    """Stretches where two word lists differ, with short matches between two sizeable ones merged."""
    merged: list[list] = []
    for op in Indel.opcodes(a, b):
        kind = "equal" if op.tag == "equal" else "diff"
        if merged and merged[-1][0] == kind:
            merged[-1][2], merged[-1][4] = op.src_end, op.dest_end
        else:
            merged.append([kind, op.src_start, op.src_end, op.dest_start, op.dest_end])

    def size(hunk: list) -> int:
        return (hunk[2] - hunk[1]) + (hunk[4] - hunk[3])

    changed = True
    while changed:
        changed = False
        for k in range(1, len(merged) - 1):
            current = merged[k]
            if current[0] != "equal" or current[2] - current[1] > 3:
                continue
            left, right = merged[k - 1], merged[k + 1]
            if size(left) >= 4 and size(right) >= 4:
                merged[k - 1:k + 2] = [["diff", left[1], right[2], left[3], right[4]]]
                changed = True
                break
    return merged


# A row the Center wrote for its own ayah out of pieces of its neighbours: cutting the repeated pieces
# would leave half a sentence, so it reads better whole.
KEEP_WHOLE = {"29:63"}

_REPEAT_WINDOW = 1500
# A short run counts only as the whole of a difference: common phrases recur all through the book.
_REPEAT_MIN_WORDS = 4
_REPEAT_SAFE_WORDS = 6
_HONORIFIC = ("صلى", "الله", "عليه", "وسلم")


def _repeated_runs(words: list[str], i1: int, i2: int, line_starts: set[int]) -> list[tuple[int, int]]:
    """Runs inside words[i1:i2] that also occur nearby: earlier, later, or earlier in the hunk."""
    context_before = words[max(0, i1 - _REPEAT_WINDOW):i1]
    context_after = words[i2:i2 + _REPEAT_WINDOW]
    n = _REPEAT_MIN_WORDS
    seen: dict[tuple[str, ...], list[tuple[list[str], int]]] = defaultdict(list)

    def index(seq: list[str], start: int = 0) -> None:
        for p in range(start, len(seq) - n + 1):
            seen[tuple(seq[p:p + n])].append((seq, p))

    index(context_before)
    index(context_after)
    kept: list[str] = []
    runs: list[tuple[int, int]] = []
    cursor = i1
    while cursor < i2:
        head = tuple(words[cursor:cursor + n])
        best = 0
        # Short phrases count only when they stand right next to themselves.
        for length in range(min(n - 1, i2 - cursor), 1, -1):
            if words[cursor:cursor + length] == words[cursor - length:cursor]:
                best = length
                break
        if not best and cursor + n <= i2:
            for seq, position in seen.get(head, ()):
                length = n
                # It may run a word or two past the difference, where alignment took the sentence's
                # last word for the first word of what follows.
                while (cursor + length < min(i2 + 2, len(words)) and position + length < len(seq)
                       and seq[position + length] == words[cursor + length]):
                    length += 1
                best = max(best, length)
            whole = cursor == i1 and cursor + best >= i2
            honorific = any(
                tuple(words[k:k + 4]) == _HONORIFIC for k in range(cursor, cursor + best)
            )
            if best < _REPEAT_SAFE_WORDS and (not whole or honorific):
                best = 0
        if best:
            # Alignment can place a repeat a word late; if sliding it back lands on the start of a
            # line, that is where it really begins.
            first, past = cursor, cursor + best
            slid_first, slid_past = first, past
            while slid_first > 0 and words[slid_first - 1] == words[slid_past - 1]:
                slid_first -= 1
                slid_past -= 1
                if slid_first in line_starts:
                    first, past = slid_first, slid_past
                    break
            runs.append((first, past))
            cursor += best
        else:
            kept.append(words[cursor])
            if len(kept) >= n:
                seen[tuple(kept[-n:])].append((kept, len(kept) - n))
            cursor += 1
    return runs


def _delete_tokens(unit: Unit, indexes: set[int]) -> None:
    """Cut the given tokens out of a unit's text, leaving braces balanced."""
    spans = unit.spans
    text = unit.text
    ordered = sorted(indexes)
    runs: list[tuple[int, int]] = []
    for position in ordered:
        if runs and runs[-1][1] == position:
            runs[-1] = (runs[-1][0], position + 1)
        else:
            runs.append((position, position + 1))
    for first, past in reversed(runs):
        start = spans[first][0]
        end = spans[past][0] if past < len(spans) else len(text)
        cut = text[start:end]
        depth = 0
        unmatched_close = False
        last_open = -1
        for offset, char in enumerate(cut):
            if char == "{":
                if depth == 0:
                    last_open = offset
                depth += 1
            elif char == "}":
                if depth == 0:
                    unmatched_close = True
                else:
                    depth -= 1
        if depth > 0:
            end = start + last_open  # an opening brace that belongs to what follows stays
        if unmatched_close:
            before = text[:start].rstrip()
            if before.endswith("{"):
                start = len(before) - 1  # its opening brace goes with it
            else:
                # The quote started earlier and goes on, so it keeps its closing brace.
                text = before + "} " + text[end:]
                continue
        text = text[:start] + text[end:]
    unit.text = _tidy(text)


def _tidy(text: str) -> str:
    text = re.sub(r"\{\s*\}\s*[.؛،:]?", " ", text)
    text = re.sub(r"\s+([.،؛:])", r"\1", text)
    text = re.sub(r"([.،؛:])(?:\s*\1)+", r"\1", text)
    text = re.sub(r"[ \t]+", " ", text).strip()
    return re.sub(r"^[.،؛:\s]+", "", text)


def _has_words(unit: Unit) -> bool:
    without_marks = NUMBER_MARK.sub(" ", unit.text)
    return TOKEN.search(_NOTE_ANCHOR.sub("", without_marks)) is not None


def _quran_remnants(unit: Unit, quran: str) -> list[set[int]]:
    """The tokens of each fully vowelled brace that is ayah text the Quran block already gave."""
    found: list[set[int]] = []
    for match in BRACED.finditer(unit.text):
        body = match.group()
        words = TOKEN.findall(body)
        if len(words) < 3 or vowel_ratio(body) < 0.75 or skeleton(body) not in quran:
            continue
        found.append({
            index for index, (start, _, _) in enumerate(unit.spans)
            if match.start() <= start < match.end()
        })
    return found


@dataclass
class CleanStats:
    repeated_lines: int = 0
    repeated_words: int = 0
    remnant_words: int = 0


def remove_repeats(
    parsed: list[ParsedRow],
    reference_words: list[str],
    quran: str,
    stats: CleanStats,
    log: list[tuple[str, str]] | None = None,
) -> None:
    """Drop, from one surah, what the per-ayah split repeated, and ayah text left inside lines."""

    def line_key(unit: Unit) -> str:
        return fold(NUMBER_MARK.sub(" ", _NOTE_ANCHOR.sub("", unit.text))).replace(" ", "")

    # A line numbered for another ayah, when that ayah has the line too (13:25 carries 13:26's).
    keys_by_ayah = {row.aya: {line_key(unit) for unit in row.units} for row in parsed}
    for row in parsed:
        kept = []
        for unit in row.units:
            mark = NUMBER_MARK.match(_NOTE_ANCHOR.sub("", unit.text))
            number = int(re.sub(r"\D", "", mark.group())) if mark and "-" not in mark.group() else row.aya
            if number != row.aya and line_key(unit) in keys_by_ayah.get(number, ()):
                stats.repeated_lines += 1
                continue
            kept.append(unit)
        row.units = kept

    # Whole lines next: the split copied long paragraphs onto every ayah they explain.
    seen_lines: set[str] = set()
    for row in parsed:
        kept = []
        for unit in row.units:
            key = line_key(unit)
            if unit.role in ("body", "lead", "lead_after") and len(TOKEN.findall(unit.text)) >= 6 and key in seen_lines:
                stats.repeated_lines += 1
                continue
            seen_lines.add(key)
            kept.append(unit)
        row.units = kept

    units = [unit for row in parsed for unit in row.units if unit.role != "divider"]
    for unit in units:
        unit.tokenize()
    owner: list[tuple[Unit, int]] = [(unit, i) for unit in units for i in range(len(unit.spans))]
    words = [fold(unit.spans[i][2]) for unit, i in owner]
    line_starts = {position for position, (_, token) in enumerate(owner) if token == 0}
    doomed: dict[int, set[int]] = defaultdict(set)

    for kind, i1, i2, _, _ in hunks_of(words, [fold(w) for w in reference_words]):
        if kind == "equal" or i2 <= i1:
            continue
        for first, past in _repeated_runs(words, i1, i2, line_starts):
            stats.repeated_words += past - first
            for position in range(first, past):
                unit, token = owner[position]
                doomed[id(unit)].add(token)
        in_hunk: dict[int, set[int]] = defaultdict(set)
        units_in_hunk: dict[int, Unit] = {}
        for position in range(i1, i2):
            unit, token = owner[position]
            in_hunk[id(unit)].add(token)
            units_in_hunk[id(unit)] = unit
        for unit_id, tokens in in_hunk.items():
            for brace in _quran_remnants(units_in_hunk[unit_id], quran):
                if brace <= tokens and not brace <= doomed[unit_id]:
                    stats.remnant_words += len(brace)
                    doomed[unit_id] |= brace

    for row in parsed:
        kept = []
        for unit in row.units:
            if id(unit) in doomed and f"{row.sura}:{row.aya}" not in KEEP_WHOLE:
                before = unit.text
                _delete_tokens(unit, doomed[id(unit)])
                remaining = len(TOKEN.findall(NUMBER_MARK.sub(" ", _NOTE_ANCHOR.sub("", unit.text))))
                # Two or three words left over from a cut sentence read as nothing at all.
                if remaining <= 3 and unit.role == "body":
                    unit.text = ""
                if log is not None:
                    log.append((f"{row.sura}:{row.aya}", f"{before}\n=> {unit.text}"))
                if not _has_words(unit):
                    continue
            kept.append(unit)
        row.units = kept


@dataclass
class OutRow:
    group_id: int
    sura: int
    from_ayah: int
    to_ayah: int
    kind: str
    text: str
    notes: list[str] = field(default_factory=list)


def _finish_text(raw: str, all_notes: list[tuple[int, int, str]], stats: Counter) -> tuple[str, list[str]]:
    """Turn note anchors into ⟦n⟧ for the notes readers see; the rest simply disappear."""
    shown: list[str] = []

    def swap(match: re.Match[str]) -> str:
        note = all_notes[ord(match.group()) - _NOTE_BASE][2]
        kind = note_kind(note)
        stats[f"note_{kind}"] += 1
        if kind not in SHOWN_NOTE_KINDS:
            return ""
        shown.append(note)
        return f"{ANCHOR_OPEN}{len(shown)}{ANCHOR_CLOSE}"

    text = _NOTE_ANCHOR.sub(swap, raw)
    # Taking a note out can leave a gap where it stood.
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r" +([.،؛:؟!\u27e6])", r"\1", text)
    return text.strip(), shown


def quran_block(sura: int, ayahs: list[int], approved: dict[str, str]) -> str:
    """The app's approved text, unchanged, with each ayah's number after it."""
    parts = []
    for ayah in ayahs:
        digits = str(ayah).translate(str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩"))
        parts.append(f"{approved[f'{sura}:{ayah}'].strip()} ({digits})")
    return " ".join(parts)


def _supplement_text(spec: Supplement, quranenc_rows: list[dict]) -> str:
    """The supplement, word for word from the QuranEnc row holding it."""
    sura = int(spec.verse_key.split(":")[0])
    starts_with = unicodedata.normalize("NFC", spec.starts_with)
    holders = [
        text for text in (unicodedata.normalize("NFC", r["text"]) for r in quranenc_rows if int(r["sura"]) == sura)
        if starts_with in text
    ]
    if len(holders) != 1:
        raise ValueError(f"Supplement {spec.verse_key} found in {len(holders)} QuranEnc rows")
    passage = holders[0][holders[0].index(starts_with):].strip()
    count = len(TOKEN.findall(passage))
    if count != spec.words:
        raise ValueError(f"Supplement {spec.verse_key} has {count} words, expected {spec.words}")
    return passage


def build_rows(
    center: dict[str, str],
    quranenc_rows: list[dict],
    assigned: list[tuple[dict, int]],
    approved: dict[str, str],
    approved_imlaei: dict[str, str],
    log: list[tuple[str, str]] | None = None,
) -> tuple[list[OutRow], dict[str, int], dict]:
    """Rows for `tafsir_passage` and the verse-to-passage map, from the Center's rows.

    [assigned] is the QuranEnc copy cut into passages. It supplies which ayahs are read together.
    """
    corrected = apply_corrections(center)
    keys = sorted(corrected, key=lambda k: tuple(map(int, k.split(":"))))
    if set(keys) != set(approved):
        raise ValueError("The Center's rows and the approved text cover different ayahs")

    all_notes: list[tuple[int, int, str]] = []
    parsed: dict[int, list[ParsedRow]] = defaultdict(list)
    for key in keys:
        sura, aya = map(int, key.split(":"))
        parsed[sura].append(parse_row(sura, aya, corrected[key], all_notes))

    # Line-level repeats are judged per surah against the QuranEnc words, minus its Quran blocks.
    reference: dict[int, list[str]] = defaultdict(list)
    for row in quranenc_rows:
        if row["type"] != KIND_QURAN:
            clean = NUMBER_MARK.sub(" ", row["text"])
            reference[int(row["sura"])].extend(bare(t) for t in TOKEN.findall(clean))
    quran = skeleton(" ".join(
        approved_imlaei[k] for k in sorted(approved_imlaei, key=lambda k: tuple(map(int, k.split(":"))))
    ))
    clean = CleanStats()
    for sura in sorted(parsed):
        remove_repeats(parsed[sura], reference[sura], quran, clean, log)

    # Passages, as cut in the QuranEnc copy.
    blocks: dict[int, list[tuple[int, int, int]]] = defaultdict(list)
    group_of_ayah: dict[str, int] = {}
    for row, group in assigned:
        start, end = int(row["from_aya"]), int(row["to_aya"])
        if start > 0:
            for ayah in range(start, end + 1):
                group_of_ayah[f"{row['sura']}:{ayah}"] = group
        if row["type"] == KIND_QURAN:
            blocks[group].append((int(row["sura"]), start, end))
    if set(group_of_ayah) != set(keys):
        raise ValueError("QuranEnc passages do not cover every ayah")

    ranges_of_ayah: dict[str, list[tuple[int, int]]] = defaultdict(list)
    for row in quranenc_rows:
        start, end = int(row["from_aya"]), int(row["to_aya"])
        if row["type"] in OWN_KINDS and start > 0:
            for ayah in range(start, end + 1):
                ranges_of_ayah[f"{row['sura']}:{ayah}"].append((start, end))

    parsed_by_key = {f"{p.sura}:{p.aya}": p for rows in parsed.values() for p in rows}
    supplements: dict[str, list[tuple[Supplement, str]]] = defaultdict(list)
    for spec in SUPPLEMENTS:
        supplements[spec.verse_key].append((spec, _supplement_text(spec, quranenc_rows)))

    stats: Counter = Counter()
    out: list[OutRow] = []
    emitted: set[str] = set()

    def emit(group: int, sura: int, first: int, last: int, kind: str, raw: str) -> None:
        text, notes = _finish_text(raw, all_notes, stats)
        out.append(OutRow(group, sura, first, last, kind, text, notes))

    role_kinds = {"title": KIND_TITLE, "place": KIND_PLACE, "basmala": KIND_BASMALA,
                  "lead": KIND_LEAD, "lead_after": KIND_LEAD, "closing": KIND_CLOSING,
                  "divider": KIND_DIVIDER}

    def emit_body(group: int, sura: int, aya: int, units: list[Unit]) -> None:
        """Consecutive explanation lines read as one row; a closing line or "* * *" stands alone."""
        key = f"{sura}:{aya}"
        pending: list[str] = []

        def flush() -> None:
            if pending:
                emit(group, sura, aya, aya, KIND_EXPLANATION, "\n".join(pending))
                pending.clear()

        for spec, text in supplements.get(key, ()):
            if spec.place != "inside":
                continue
            target = next((u for u in units if u.role == "body" and spec.after_text in _NOTE_ANCHOR.sub("", u.text)), None)
            if target is None:
                raise ValueError(f"Supplement {key}: anchor not found")
            cut = _NOTE_ANCHOR.sub("", target.text).index(spec.after_text) + len(spec.after_text)
            # The anchor was found with notes removed, so find the same place in the real text.
            seen = 0
            position = 0
            for position, char in enumerate(target.text):
                if not _NOTE_ANCHOR.match(char):
                    seen += 1
                    if seen == cut:
                        position += 1
                        break
            head, tail = target.text[:position].strip(), target.text[position:].strip()
            at = units.index(target)
            pieces = [Unit(sura, aya, "body", head), Unit(sura, aya, "supplement", text)]
            if tail:
                pieces.append(Unit(sura, aya, "body", tail))
            units = units[:at] + pieces + units[at + 1:]

        for unit in units:
            if unit.role == "body":
                pending.append(unit.text)
            elif unit.role == "supplement":
                flush()
                out.append(OutRow(group, sura, aya, aya, KIND_SUPPLEMENT, unit.text, []))
            else:
                flush()
                emit(group, sura, 0, 0, role_kinds[unit.role], unit.text)
        flush()
        for spec, text in supplements.get(key, ()):
            if spec.place == "after_body":
                out.append(OutRow(group, sura, aya, aya, KIND_SUPPLEMENT, text, []))

    for group in sorted(blocks):
        for sura, start, end in sorted(blocks[group], key=lambda b: (b[0], b[1])):
            ayahs = [a for a in range(start, end + 1) if f"{sura}:{a}" not in emitted]
            if not ayahs:
                continue
            for position, aya in enumerate(ayahs):
                key = f"{sura}:{aya}"
                emitted.add(key)
                units = parsed_by_key[key].units
                before = [u for u in units if u.role in ("title", "place", "basmala", "lead")]
                after = [u for u in units if u.role == "lead_after"]
                rest = [u for u in units if u.role in ("body", "closing", "divider")]
                for unit in before:
                    emit(group, sura, 0, 0, role_kinds[unit.role], unit.text)
                for spec, text in supplements.get(key, ()):
                    if spec.place == "lead":
                        out.append(OutRow(group, sura, 0, 0, KIND_SUPPLEMENT, text, []))
                if position == 0:
                    out.append(OutRow(group, sura, ayahs[0], ayahs[-1], KIND_QURAN,
                                      quran_block(sura, ayahs, approved), []))
                for unit in after:
                    emit(group, sura, 0, 0, role_kinds[unit.role], unit.text)
                emit_body(group, sura, aya, rest)
    if emitted != set(keys):
        raise ValueError(f"Ayahs left out of every passage: {sorted(set(keys) - emitted)[:5]}")

    # An ayah the Sheikh explains together with its neighbours may have no row of its own here.
    # Point it at the nearest explanation in that same range, so the reader lands on the right text.
    own_rows: dict[tuple[int, int], OutRow] = {}
    for row in out:
        if row.kind == KIND_EXPLANATION:
            own_rows.setdefault((row.sura, row.from_ayah), row)
    extended = 0
    without_row = []
    for key in keys:
        sura, aya = map(int, key.split(":"))
        if (sura, aya) in own_rows:
            continue
        candidates = [(s, e) for s, e in ranges_of_ayah.get(key, ()) if e > s]
        target = None
        for start, end in candidates:
            for other in range(aya - 1, start - 1, -1):
                if (sura, other) in own_rows:
                    target = own_rows[(sura, other)]
                    break
            if target is None:
                for other in range(aya + 1, end + 1):
                    if (sura, other) in own_rows:
                        target = own_rows[(sura, other)]
                        break
            if target is not None:
                break
        if target is None or group_of_ayah[key] != target.group_id:
            without_row.append(key)
            continue
        target.from_ayah = min(target.from_ayah, aya)
        target.to_ayah = max(target.to_ayah, aya)
        extended += 1

    report = {
        "rows": len(out),
        "repeated_lines_removed": clean.repeated_lines,
        "repeated_words_removed": clean.repeated_words,
        "remnant_words_removed": clean.remnant_words,
        "ayahs_pointed_at_neighbour": extended,
        "ayahs_without_own_row": without_row,
        "placeholder_rows": sum(1 for p in parsed_by_key.values() if p.placeholder),
        "notes": {k: v for k, v in sorted(stats.items())},
    }
    return out, group_of_ayah, report


def verify_rows(
    rows: list[OutRow],
    center: dict[str, str],
    approved: dict[str, str],
    supplements_words: int,
) -> None:
    """Fail the build unless the rows are what this module promises they are."""
    corrected = apply_corrections(center)

    def words(text: str) -> list[str]:
        cleaned = NUMBER_MARK.sub(" ", re.sub(f"{ANCHOR_OPEN}\\d+{ANCHOR_CLOSE}", " ", text))
        return [fold(t) for t in TOKEN.findall(cleaned)]

    # Every word shown as the Center's is the Center's, after the listed corrections.
    available: dict[int, Counter] = defaultdict(Counter)
    for key, text in corrected.items():
        available[int(key.split(":")[0])].update(words(NOTE.sub(" ", text)))
    shown: dict[int, Counter] = defaultdict(Counter)
    for row in rows:
        if row.kind not in (KIND_QURAN, KIND_SUPPLEMENT):
            shown[row.sura].update(words(row.text))
    for sura, counter in shown.items():
        extra = counter - available[sura]
        if extra:
            raise ValueError(f"Surah {sura} shows words the Center's text lacks: {extra.most_common(5)}")

    # Each ayah's approved text appears once, unchanged, in its surah's Quran rows.
    for key, text in approved.items():
        sura, aya = map(int, key.split(":"))
        holders = [r for r in rows if r.kind == KIND_QURAN and r.sura == sura and r.from_ayah <= aya <= r.to_ayah]
        if len(holders) != 1 or text.strip() not in holders[0].text:
            raise ValueError(f"Approved text of {key} is not in exactly one Quran row")

    supplement_count = sum(len(TOKEN.findall(r.text)) for r in rows if r.kind == KIND_SUPPLEMENT)
    if supplement_count != supplements_words:
        raise ValueError(f"Supplements hold {supplement_count} words, expected {supplements_words}")

    # Nothing the source uses for its own bookkeeping reaches a reader.
    for row in rows:
        place = f"{row.sura}:{row.from_ayah} ({row.kind})"
        if row.kind == KIND_QURAN:
            continue
        for mark in ("¬", "¥", "ككك", "ممم", "___", "ضضض", _BLOCK_LINE):
            if mark in row.text:
                raise ValueError(f"{place} still holds {mark!r}")
        if _NOTE_ANCHOR.search(row.text):
            raise ValueError(f"{place} still holds an unresolved note anchor")
        if row.text.count("{") != row.text.count("}"):
            raise ValueError(f"{place} has unbalanced braces: {row.text[:120]}")
        if PLACEHOLDER.search(bare(row.text)):
            raise ValueError(f"{place} still says the Sheikh did not explain it")
        anchors = [int(n) for n in re.findall(f"{ANCHOR_OPEN}(\\d+){ANCHOR_CLOSE}", row.text)]
        if anchors != list(range(1, len(row.notes) + 1)):
            raise ValueError(f"{place} has anchors {anchors} for {len(row.notes)} notes")
