"""Turn an OpenITI tafsir text into the rows of a downloadable pack.

OpenITI (https://openiti.org, release 2025.1.9, CC BY-NC-SA 4.0) publishes, for many tafsirs, a version
taken from altafsir.com that is already split by the ayahs it explains, its introductions and footnotes
removed. Its markup is simple:

    ### | [2 - سورة البقرة]            a surah begins
    ### || [2.255] or [2.1-5]          a passage on that ayah or run of ayahs
    # text                             a paragraph begins
    ~~text                             the paragraph goes on (the print's line break)
    ms0123                             a milestone: where a chunk of the corpus ends

Each passage becomes one row of the pack. The paragraph breaks are kept, the print's line breaks and the
milestones are not. A Quran quotation the text sets as a paragraph of its own, followed by a citation
paragraph («[الكهف: 46]» or «الأعراف 43»), is put in braces — the way the other tafsirs mark the Quran —
and joined to the sentence that introduces it and to its citation. Nothing is reworded.

Two things the source does need: an ayah no passage explains gets a row saying so (it is never handed a
neighbour's explanation), and a passage that starts inside the one before it starts after it instead.
Both are pinned per book, so a change in the source stops the build.
"""

from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path

import pack_common as pc

ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = ROOT / "openiti_2025-1-9"
AYAH_REGIONS = ROOT / "mushaf_app" / "app" / "src" / "main" / "assets" / "data" / "ayah_regions.json"

LICENSE = "CC BY-NC-SA 4.0"
LICENSE_URL = "https://creativecommons.org/licenses/by-nc-sa/4.0/"
CORPUS_URL = "https://doi.org/10.5281/zenodo.3082463"
ATTRIBUTION = "OpenITI (openiti.org)، نقلًا عن موقع التفاسير altafsir.com"
NOT_EXPLAINED = "[لم يرد شرح هذه الآية في المصدر الذي نُقل عنه هذا التفسير.]"

_SURAH = re.compile(r"^### \| \[(\d+) - [^\]]+\]")
_PASSAGE = re.compile(r"^### \|\| \[(\d+)\.(\d+)(?:-(\d+))?\]")
_MILESTONE = re.compile(r"\s*\bms\d+\b\s*")
# Leftovers of the site's HTML and of the corpus's own work, none of which is text.
_DEBRIS = re.compile(r"p>>|¤")
_BRACKET_CITE = re.compile(r"^\[([^\]\d:]{2,30}):\s*(\d+)(?:\s*-\s*\d+)?\]")
_BARE_CITE = re.compile(r"^([ء-ي ]{2,25}?)\s+(\d+)\s*$")


@dataclass
class OpenItiBook:
    """One tafsir taken from an OpenITI file. See pack_books.Book for the pack fields."""

    pack_id: str
    file: str  # the version file's name in SOURCE_DIR
    repository: str  # the OpenITI repository and commit it was taken at, for the record
    commit: str
    file_sha256: str
    title: str
    work: str
    author: str
    credit: str
    changes: str
    expected_passages: int
    # Ayahs no passage explains: each gets a row with NOT_EXPLAINED.
    not_explained: set[pc.Key] = field(default_factory=set)
    # Passages that start inside the one before: (where they start in the source, where they start here).
    trimmed_starts: dict[pc.Key, pc.Key] = field(default_factory=dict)
    expected_cited_quotes: int = 0
    # (where the passage starts, as "s:a", wrong, right, why): each must match exactly once in that passage.
    corrections: list[pc.Correction] = field(default_factory=list)
    version: int = 1
    kind: str = "tafsir"

    @property
    def path(self) -> Path:
        return SOURCE_DIR / self.file

    @property
    def source_url(self) -> str:
        author = self.file.split(".")[0]
        book = ".".join(self.file.split(".")[:2])
        return f"https://github.com/OpenITI/{self.repository}/blob/{self.commit}/data/{author}/{book}/{self.file}"


def _fold(name: str) -> str:
    name = re.sub(r"[ً-ٰٕـۖ-ۭ]", "", unicodedata.normalize("NFC", name))
    name = name.translate(str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي", "ة": "ه"}))
    return re.sub(r"^سوره\s+", "", name).strip()


_SKELETON_DROP = str.maketrans("", "", "اويءئؤ *")


def skeleton(text: str) -> str:
    """The letters two spellings of the Quran share: «الكتب» and «الكتاب» meet here."""
    return _fold(text).translate(_SKELETON_DROP)


@dataclass
class Surahs:
    """Surah names as the citations write them, and each surah's text to check a quotation against."""
    by_name: dict[str, int]
    skeletons: dict[int, str]

    def __contains__(self, folded_name: str) -> bool:
        return folded_name in self.by_name


def surah_names(path: Path = AYAH_REGIONS) -> Surahs:
    records = json.loads(path.read_text(encoding="utf-8"))["records"]
    by_name = {_fold(r["surah_name_ar"]): r["surah_number"] for r in records}
    texts: dict[int, list[str]] = {}
    for r in records:
        texts.setdefault(r["surah_number"], []).append(r["text_imlaei"])
    return Surahs(by_name, {n: skeleton(" ".join(t)) for n, t in texts.items()})


def read_source(book: OpenItiBook) -> str:
    data = book.path.read_bytes()
    found = hashlib.sha256(data).hexdigest()
    if found != book.file_sha256:
        raise ValueError(f"{book.file}: sha256 {found} differs from the pinned {book.file_sha256}")
    text = data.decode("utf-8")
    if "#META#Header#End#" not in text:
        raise ValueError(f"{book.file}: no OpenITI header")
    return text.split("#META#Header#End#", 1)[1]


def paragraphs(lines: list[str]) -> list[str]:
    """A passage's lines as paragraphs: «# » opens one, «~~» continues it."""
    out: list[str] = []
    for line in lines:
        if line.startswith("# "):
            out.append(line[2:])
        elif line.startswith("~~"):
            if not out:
                out.append("")
            out[-1] += " " + line[2:]
        elif line.strip():
            raise ValueError(f"a line that is neither a paragraph nor its continuation: {line[:60]!r}")
    cleaned = []
    for p in out:
        p = _DEBRIS.sub("", _MILESTONE.sub(" ", p))
        p = re.sub(r"\s+", " ", p).strip()
        if p:
            cleaned.append(p)
    return cleaned


def _citation(paragraph: str, names: Surahs) -> tuple[str, str, int] | None:
    """(the citation as «[السورة: رقم]», the rest of the paragraph, the surah) when [paragraph] opens with one.

    Two styles: «[الكهف: 46]» and, bare, «الآية القصص 52 54» or «آل عمران 119 وبقوله تعالى» — a surah's
    name, of one to three words, then the ayah number and maybe the last ayah of the run."""
    m = _BRACKET_CITE.match(paragraph)
    if m and _fold(m.group(1)) in names:
        return f"[{m.group(1).strip()}: {m.group(2)}]", paragraph[m.end():], names.by_name[_fold(m.group(1))]
    text = re.sub(r"^الآية\s+", "", paragraph)
    words = text.split(" ")
    for n in (3, 2, 1):
        if len(words) <= n or _fold(" ".join(words[:n])) not in names:
            continue
        m = re.match(r"^(\d+)(?:\s+(\d+))?(?=[\s.،؛:]|$)", " ".join(words[n:]))
        if not m:
            continue
        span = m.group(1) + (f"-{m.group(2)}" if m.group(2) else "")
        rest = " ".join(words[n:])[m.end():]
        return f"[{' '.join(words[:n])}: {span}]", rest, names.by_name[_fold(" ".join(words[:n]))]
    return None


def mark_quotes(paras: list[str], names: Surahs) -> tuple[list[str], int]:
    """Brace a quotation set as its own paragraph before a citation, and join it to its neighbours.

    Only a paragraph whose letters are found in the surah it cites is taken for a quotation: a paragraph
    of commentary that happens to precede a citation is left as it is."""
    out: list[str] = []
    marked = 0
    for para in paras:
        cite = _citation(para, names)
        if cite is None or not out or "{" in out[-1] or len(out[-1]) > 800:
            out.append(para)
            continue
        citation, rest, surah = cite
        quoted = skeleton(out[-1])
        if len(quoted) < 4 or quoted not in names.skeletons[surah]:
            out.append(para)
            continue
        quote = "{" + out.pop() + "}"
        joined = f"{quote} {citation}{rest}"
        # The sentence that introduces the quotation has not ended («قال تعالى:», «بقوله تعالى»): the
        # quotation is part of it, as is what follows the citation.
        if out and not re.search(r"[.!؟»\"]$", out[-1]):
            joined = out.pop() + " " + joined
        out.append(joined)
        marked += 1
    return out, marked


def passages(book: OpenItiBook, names: Surahs | None = None) -> tuple[list[pc.Row], dict]:
    """The pack's rows in Quran order, and a report of what was done."""
    names = names if names is not None else surah_names()
    body = read_source(book)
    raw: list[tuple[int, int, int, list[str]]] = []
    current: list[str] | None = None
    for line in body.split("\n"):
        line = line.rstrip()
        # A milestone can fall inside a heading too: «### | [90 ms8831 - سورة البلد]».
        heading = re.sub(r"\s*\bms\d+\b", "", line) if line.startswith("###") else line
        if _SURAH.match(heading):
            current = None
            continue
        m = _PASSAGE.match(heading)
        if m:
            s, a, b = int(m.group(1)), int(m.group(2)), int(m.group(3) or m.group(2))
            current = []
            raw.append((s, a, b, current))
            continue
        if current is not None:
            current.append(line)
        elif line.strip():
            raise ValueError(f"{book.pack_id}: text outside any passage: {line[:60]!r}")
    if len(raw) != book.expected_passages:
        raise ValueError(f"{book.pack_id}: {len(raw)} passages, expected {book.expected_passages}")

    corrections: dict[pc.Key, list[pc.Correction]] = {}
    for c in book.corrections:
        corrections.setdefault(pc.key_of(c[0]), []).append(c)
    rows: list[pc.Row] = []
    trimmed: dict[pc.Key, pc.Key] = {}
    quotes = 0
    for s, a, b, lines in raw:
        if (s, a) in corrections:
            fixed = pc.apply_corrections({(s, a): "\n".join(lines)}, corrections.pop((s, a)))
            lines = fixed[(s, a)].split("\n")
        if rows and rows[-1].sura == s and a <= rows[-1].to_ayah:
            start = rows[-1].to_ayah + 1
            trimmed[(s, a)] = (s, start)
            a = start
        if not 1 <= a <= b <= pc.AYAH_COUNTS[s - 1]:
            raise ValueError(f"{book.pack_id}: passage {s}:{a}-{b} is outside the surah")
        paras, marked = mark_quotes(paragraphs(lines), names)
        quotes += marked
        text = pc.split_long_paragraphs(pc.tidy("\n".join(paras)))
        pc.check_text(f"{book.pack_id} {s}:{a}-{b}", text, [])
        rows.append(pc.Row(s, a, b, text, []))
    if corrections:
        raise ValueError(f"{book.pack_id}: corrections for passages that do not exist: {sorted(corrections)}")
    if trimmed != book.trimmed_starts:
        raise ValueError(f"{book.pack_id}: passages starting inside the one before differ: {sorted(trimmed.items())}")
    if quotes != book.expected_cited_quotes:
        raise ValueError(f"{book.pack_id}: {quotes} cited quotations marked, expected {book.expected_cited_quotes}")

    covered = {(r.sura, x) for r in rows for x in range(r.from_ayah, r.to_ayah + 1)}
    missing = {key for key in pc.ALL_AYAHS if key not in covered}
    if missing != book.not_explained:
        raise ValueError(f"{book.pack_id}: ayahs without a passage differ: {sorted(missing)[:12]}")
    rows += [pc.Row(s, a, a, NOT_EXPLAINED, []) for s, a in sorted(missing)]
    rows.sort(key=lambda r: (r.sura, r.from_ayah))
    report = {
        "passages": len(raw),
        "rows": len(rows),
        "trimmed_starts": len(trimmed),
        "not_explained": sorted(f"{s}:{a}" for s, a in missing),
        "cited_quotes_marked": quotes,
    }
    return rows, report


def _credit(work: str, author: str) -> str:
    return (
        f"المصدر: {work} لـ{author}، من مشروع OpenITI نقلًا عن موقع التفاسير altafsir.com، "
        f"برخصة المشاع الإبداعي {LICENSE} ({LICENSE_URL})، مع تعديلات في التنسيق."
    )


_CHANGES = (
    "وُصلت أسطر الطبعة المقطوعة، وحُذفت علامات التقسيم التي يضعها المشروع، ووُضعت الآيات المقتبسة "
    "في فقرة مستقلة بين قوسين مع موضعها. "
)

BOOKS: dict[str, OpenItiBook] = {
    "qurtubi": OpenItiBook(
        pack_id="qurtubi",
        file="0671AbuCabdAllahQurtubi.JamicLiAhkamQuran.Tafsir01005-ara1",
        repository="0675AH",
        commit="d98a982f2be9b26ad8cc8300ca774363aaadb4e4",
        file_sha256="218e8c00b0a8016a50209106561a5a0b31c0dec09093760f1507de0841b0258d",
        title="تفسير القرطبي",
        work="الجامع لأحكام القرآن",
        author="القرطبي",
        credit=_credit("الجامع لأحكام القرآن", "القرطبي"),
        changes=_CHANGES + "ولا يتضمن المصدر شرح الآية ٥٦ من سورة الأحزاب، فوُضعت في موضعها عبارة تبيّن ذلك.",
        expected_passages=3262,
        not_explained={(33, 56)},
        expected_cited_quotes=5517,
    ),
    "shawkani": OpenItiBook(
        pack_id="shawkani",
        file="1255Shawkani.FathQadir.Tafsir01009-ara1",
        repository="1275AH",
        commit="42a9fd4aea99aebd2f85f1bc3e5db737283c7f14",
        file_sha256="eff6725c918442f58dee7c3cc64706482fec96a8ba6da16a69bf6a78fa25ad18",
        title="فتح القدير",
        work="فتح القدير",
        author="الشوكاني",
        credit=_credit("فتح القدير", "الشوكاني"),
        changes=_CHANGES + "ومقطعان في المصدر يشملان الآية ١٢٥ من البقرة، فجُعل الثاني يبدأ بالآية ١٢٦.",
        expected_passages=842,
        trimmed_starts={(2, 125): (2, 126)},
        expected_cited_quotes=2942,
    ),
    "ibn_ashur": OpenItiBook(
        pack_id="ibn_ashur",
        file="1393MuhammadTahirIbnCashurTunisi.TahrirWaTanwir.Tafsir07054-ara1",
        repository="1400AH",
        commit="9ecd69d142e2f165f80a52a958aa2cda09048e03",
        file_sha256="5a79ea1c11ea7d7a8da097d80ebe7aaf6f92a97e6734e4057c2c0ec0febbfe3f",
        title="التحرير والتنوير",
        work="التحرير والتنوير",
        author="محمد الطاهر ابن عاشور",
        credit=_credit("التحرير والتنوير", "محمد الطاهر ابن عاشور"),
        changes=_CHANGES + "وصُحح حرف مكرر في موضع واحد.",
        expected_passages=3839,
        expected_cited_quotes=17893,
        corrections=[("6:123", "الأممم الأخرى", "الأمم الأخرى", "حرف مكرر في المصدر")],
    ),
}
