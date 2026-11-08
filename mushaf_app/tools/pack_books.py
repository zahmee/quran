"""The four downloadable packs: where each book comes from and what is done to it.

Every pack is built from one book of the Tafsir Center's dataset (CC BY 4.0, https://tafsir.net),
copied verbatim by tafsir_center_work/scripts/build_center_slim.py into a gitignored folder at the repo
root and pinned here by the SHA-256 of its rows. pack_common.py does the cleaning all books share;
this file lists what is particular to each book: its CORRECTIONS, its rows cut in the source, and, for
Ibn Kathir, the ayahs the Center marks as unexplained although a neighbouring row explains them.

A correction is (ayah, wrong, right, reason) and must match exactly once. Most of them close an editor's
note the source left open, which would otherwise swallow the main text after it.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

import pack_common as pc

ROOT = Path(__file__).resolve().parents[2]
SNAPSHOT_DATE = "2026-11-08"
LICENSED = (
    "من قاعدة بيانات مركز تفسير للدراسات القرآنية (https://tafsir.net)، "
    "برخصة المشاع الإبداعي CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)"
)

OPEN_NOTE = "حاشية للمحقق لم تُغلق في المصدر فابتلعت ما بعدها"
NOTE_FROM_PREVIOUS_ROW = "بقية حاشية بدأت في الصف السابق"


@dataclass
class Book:
    pack_id: str
    snapshot: str  # the build_center_slim.py book name
    rows_sha256: str
    expected_rows: int
    kind: str  # "tafsir" or "irab"
    title: str
    work: str
    author: str
    credit: str
    changes: str
    version: int = 1
    corrections: list[pc.Correction] = field(default_factory=list)
    cuts: set[pc.Key] = field(default_factory=set)
    whole_despite_length: set[pc.Key] = field(default_factory=set)
    expected_merges: int = 0
    pre_clean: object = None
    placeholder: re.Pattern | None = None  # a row saying the author did not explain the ayah
    expected_placeholders: int = 0
    redirects: dict[pc.Key, pc.Key] = field(default_factory=dict)

    @property
    def slim_path(self) -> Path:
        return ROOT / f"tafsircenter_{self.snapshot}_{SNAPSHOT_DATE}" / f"{self.snapshot}_slim.db"


# ---------------------------------------------------------------------------------------------
# Ibn Kathir

KATHIR_CORRECTIONS: list[pc.Correction] = [
    ("1:1", "(¬ الرازي) (2)", "(الرازي)", "علامة حاشية بلا نص، ورقم حاشية من الطبعة"),
    ("2:58", "أنها بيت المقدس] (¬).", "أنها بيت المقدس].", "علامة حاشية بلا نص"),
    ("2:59", "أنها بيت المقدس] (¬).", "أنها بيت المقدس].", "علامة حاشية بلا نص"),
    ("3:1", "رقم 8673).", "رقم 8673).¥", "حاشية لم تُغلق في آخر الصف"),
    ("3:90", "وهو إسناد الطبري المتقدم عن ابن عباس.", "وهو إسناد الطبري المتقدم عن ابن عباس.¥", "حاشية لم تُغلق في آخر الصف"),
    ("19:56", "سورة الإسراء، وقال العوفي", "سورة الإسراء.¥، وقال العوفي", OPEN_NOTE),
    ("19:57", "سورة الإسراء، وقال العوفي", "سورة الإسراء.¥، وقال العوفي", OPEN_NOTE),
    ("21:87", "يقوّي بعضها بعضًا، وتتقوى بقول ابوقال سالم", "يقوّي بعضها بعضًا.¥ وقال سالم",
     OPEN_NOTE + "؛ وآخر الحاشية مقطوع في المصدر فحُذف"),
    ("39:36", "ووافقه الذهبي الترمذي والنسائي", "ووافقه الذهبي.¥ الترمذي والنسائي", OPEN_NOTE),
    ("58:15", "عَنْ سَبِيلِ اللَّهِ}>", "عَنْ سَبِيلِ اللَّهِ}", "حرف زائد بعد الاقتباس"),
]

KATHIR_PLACEHOLDER = re.compile(r"^(?:.*\n)?لم يفسر ابن كثير هذه الآية\.?$", re.DOTALL)
KATHIR_PLACEHOLDERS = 32

# Ayahs the Center marks «لم يفسر ابن كثير هذه الآية» whose words a nearby row quotes in the passage
# it explains: each is shown that row instead. Found by `find_redirects`, pinned here.
KATHIR_REDIRECTS: dict[pc.Key, pc.Key] = {
    (26, 138): (26, 137),
    (37, 155): (37, 156),
    (38, 75): (38, 70),
    (38, 78): (38, 70),
    (38, 81): (38, 70),
    (44, 2): (44, 1),
    (45, 2): (45, 1),
    (74, 32): (74, 33),
    (74, 46): (74, 47),
    (84, 7): (84, 8),
}


def find_redirects(cleaned: dict[pc.Key, tuple[str, list[str]]], candidates: set[pc.Key]) -> dict[pc.Key, pc.Key]:
    """For each candidate ayah (one with no explanation of its own), the nearest row of the same
    surah that quotes it in a Quran block with its number: (n) inside braces."""
    found: dict[pc.Key, pc.Key] = {}
    for sura, aya in sorted(candidates):
        number = re.compile(rf"\{{[^{{}}]*\(\s*{aya}\s*\)[^{{}}]*\}}")
        for distance in range(1, 16):
            hits = [
                (sura, other)
                for other in (aya - distance, aya + distance)
                if (sura, other) in cleaned and (sura, other) not in candidates
                and number.search(cleaned[(sura, other)][0])
            ]
            if hits:
                found[(sura, aya)] = hits[0]
                break
    return found


# ---------------------------------------------------------------------------------------------
# Tabari

TABARI_CORRECTIONS: list[pc.Correction] = [
    ("2:232", "جديدٍ.[as1]", "جديدٍ.", "علامة تحرير"),
    ("2:255", "[as2]", "", "علامة تحرير"),
    ("2:286", " [as1]أسانيد بدون هوامش [as2]هنا", "", "ملاحظة تحرير في آخر الصف"),
    ("2:282", "كَاتِبٌ وَلَا <", "كَاتِبٌ وَلَا شَهِيدٌ}", "اقتباس الآية مقطوع، أُكمل من نص المصحف"),
    ("6:54", "وقد مضت ممم 1 لروايةُ بذلك", "وقد مضت الروايةُ بذلك", "علامة طباعة دخلت في كلمة"),
    ("74:9", "ت 2 ت 3.<br>", "ت 2 ت 3.¥<br>", "حاشية لم تُغلق في آخر الصف"),
    ("74:10", "غير يسير\".¥ (2).", "غير يسير\".",
     "علامة إغلاق حاشية ورقمها بلا افتتاح؛ والنص قبلها زيادة من بعض النسخ بقيت في المتن"),
]

# Rows that hold nothing but a line break: the ayah is explained with its neighbour.
TABARI_REDIRECTS: dict[pc.Key, pc.Key] = {
    (4, 168): (4, 169),
    (26, 16): (26, 15),
    (26, 205): (26, 206),
    (37, 25): (37, 24),
    (37, 62): (37, 63),
    (37, 103): (37, 104),
    (50, 13): (50, 12),
}

# Editor's remarks the print set apart with (*): where a manuscript or a print volume begins or ends.
_TABARI_REMARK = re.compile(r"^\(\*\)\s*(?:بعده في|إلى هنا|من هنا)")


def tabari_pre_clean(_key: pc.Key, text: str) -> str:
    lines = []
    for line in text.split("\n"):
        stripped = line.strip()
        if re.fullmatch(r"_{3,}", stripped) or _TABARI_REMARK.match(stripped):
            continue
        if stripped.startswith("(*)"):
            line = stripped[3:]
        lines.append(line)
    return "\n".join(lines)


# ---------------------------------------------------------------------------------------------
# Baghawi

BAGHAWI_CORRECTIONS: list[pc.Correction] = [
    ("12:51", "ولا سمع كلامه ولا رآه.", "ولا سمع كلامه ولا رآه.¥", "حاشية تمتد إلى الصف التالي"),
    ("12:52", "ولكن ظهرت براءته في غيبته", "¬ولكن ظهرت براءته في غيبته", NOTE_FROM_PREVIOUS_ROW),
    ("13:30", " (¬انظر: تفسير القرطبي: 9/ 318، 13/ 64، البحر المحيط: 5/ 390.)}",
     "} ¬انظر: تفسير القرطبي: 9/ 318، 13/ 64، البحر المحيط: 5/ 390.¥", "حاشية داخل اقتباس الآية لم تُغلق"),
    ("20:1", "في فاتحة مريم في قوله تعالى: \"كهيعص\".", "في فاتحة مريم في قوله تعالى: \"كهيعص\".¥",
     "حاشية تمتد إلى الصف التالي"),
    ("20:2", ". . وخير ما يفسر به القرآن القرآن\".¥", "¬. . وخير ما يفسر به القرآن القرآن\".¥", NOTE_FROM_PREVIOUS_ROW),
    ("30:52", "وهم مع ذلك مدبرون عنك،", "وهم مع ذلك مدبرون عنك،¥", "حاشية تمتد إلى الصف التالي"),
    ("30:53", "كذلك لا تقدر على هداية العميان", "¬كذلك لا تقدر على هداية العميان", NOTE_FROM_PREVIOUS_ROW),
]


BOOKS: dict[str, Book] = {
    "ibn_kathir": Book(
        pack_id="ibn_kathir",
        snapshot="katheer",
        rows_sha256="d5b2c52b7c3c7cf1cb089d29819b755c6e62711cc1346d1a1547921bd5fa73cc",
        expected_rows=6236,
        kind="tafsir",
        title="تفسير ابن كثير",
        work="تفسير القرآن العظيم",
        author="ابن كثير",
        credit=f"المصدر: تفسير القرآن العظيم لابن كثير، {LICENSED}، مع حذف علامات العمل وبعض حواشي المحقق.",
        changes=(
            "حُذفت علامات العمل وأكثر حواشي المحقق، وأُبقيت التصحيحات وتخريج الأحاديث. "
            "وجُمعت الآيات التي كررت القاعدة شرحها الواحد تحت كل منها، وأُغلقت حواشٍ لم تُغلق في المصدر."
        ),
        corrections=KATHIR_CORRECTIONS,
        whole_despite_length={(28, 81)},
        expected_merges=146,  # 140 identical in the source, 6 more differ only in spacing
        placeholder=KATHIR_PLACEHOLDER,
        expected_placeholders=KATHIR_PLACEHOLDERS,
        redirects=KATHIR_REDIRECTS,
    ),
    "tabari": Book(
        pack_id="tabari",
        snapshot="tabary",
        rows_sha256="88e25ea323c086caca22ade2bf0ca29b2717730c193ee860b406ba20769bf6a3",
        expected_rows=6236,
        kind="tafsir",
        title="تفسير الطبري",
        work="جامع البيان عن تأويل آي القرآن",
        author="الطبري",
        credit=f"المصدر: جامع البيان عن تأويل آي القرآن للطبري، {LICENSED}، مع حذف علامات العمل وبعض حواشي المحقق.",
        changes=(
            "حُذفت وسوم التنسيق وعلامات الطباعة وأكثر حواشي المحقق (فروق النسخ)، وأُبقيت التصحيحات "
            "وتخريج الأحاديث، وقُسمت الفقرات الطويلة جداً."
        ),
        corrections=TABARI_CORRECTIONS,
        whole_despite_length={(50, 37)},
        pre_clean=tabari_pre_clean,
        redirects=TABARI_REDIRECTS,
    ),
    "baghawi": Book(
        pack_id="baghawi",
        snapshot="baghawy",
        rows_sha256="57ab3b88ba947477f556524a7af2e40c5b2340676ed6580a5c0cf26ede1c34f8",
        expected_rows=6236,
        kind="tafsir",
        title="تفسير البغوي",
        work="معالم التنزيل",
        author="البغوي",
        credit=f"المصدر: معالم التنزيل للبغوي، {LICENSED}، مع حذف علامات العمل وبعض حواشي المحقق.",
        changes=(
            "حُذفت علامات العمل وأكثر حواشي المحقق، وأُبقيت التصحيحات وتخريج الأحاديث. "
            "وفي أربعة مواضع قُطع النص في المصدر، فوُضعت في آخرها عبارة تبيّن ذلك."
        ),
        corrections=BAGHAWI_CORRECTIONS,
        cuts={(4, 43), (17, 4), (18, 10), (33, 9)},
        expected_merges=4,
    ),
    "irab": Book(
        pack_id="irab",
        snapshot="irab",
        rows_sha256="8614eeaf6efff8d443c431804c1e8aa4d8e93bd3d6c7fdc84a12929b1203bc95",
        expected_rows=6236,
        kind="irab",
        title="إعراب القرآن",
        work="إعراب القرآن الكريم",
        author="",
        credit=f"المصدر: إعراب القرآن الكريم، {LICENSED}، مع حذف وسوم التنسيق.",
        changes=(
            "حُذفت وسوم التنسيق. وفي أربعة مواضع من سورة البقرة قُطع النص في المصدر، "
            "فوُضعت في آخرها عبارة تبيّن ذلك."
        ),
        cuts={(2, 102), (2, 187), (2, 233), (2, 282)},
    ),
}
