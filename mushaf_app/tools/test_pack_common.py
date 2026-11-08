"""Tests for pack_common.py and pack_books.py. Run from this folder: python -m unittest test_pack_common"""

from __future__ import annotations

import gzip
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

import pack_books as pb
import pack_common as pc


class Markup(unittest.TestCase):
    def test_tags_and_entities_become_text(self):
        raw = "﻿أ<br>ب&nbsp;ج</p><p>د<div dir=\"rtl\">هـ</div>\r\nو"
        self.assertEqual(pc.strip_markup(raw), "أ\nب ج\n\nد\nهـ\n\nو")

    def test_tidy_keeps_one_paragraph_per_line(self):
        self.assertEqual(pc.tidy("  أ  ب \n\n\n ج .\n"), "أ ب\nج.")


class Notes(unittest.TestCase):
    def test_sources_stay_as_anchors_and_variants_go(self):
        stats = pc.NoteStats()
        text, notes = pc.take_notes("قال ¬في (ن): \"قالوا\".¥ كذا ¬أخرجه البخاري (1).¥ انتهى", stats)
        self.assertEqual(pc.tidy(text), "قال كذا⟦1⟧ انتهى")
        self.assertEqual(notes, ["أخرجه البخاري (1)."])
        self.assertEqual(stats.unclosed, 0)

    def test_an_unclosed_note_is_counted(self):
        stats = pc.NoteStats()
        pc.take_notes("نص ¬في (ن) بلا إغلاق", stats)
        self.assertEqual(stats.unclosed, 1)

    def test_page_carry_marks_leave_a_note(self):
        note = "¬سقط من: م. ممم بقية السابق ممم -[348]-_ (أ) أخرجه سنيد ممم¥"
        self.assertEqual(pc.clean_note(note), "سقط من: م. (أ) أخرجه سنيد")

    def test_a_note_never_spans_lines(self):
        self.assertNotIn("\n", pc.clean_note("¬أخرجه أحمد\nوالترمذي¥"))


class Paragraphs(unittest.TestCase):
    def test_long_paragraph_breaks_at_a_sentence_end(self):
        sentence = "كلمة " * 30 + "انتهت. "
        text = sentence * 60
        out = pc.split_long_paragraphs(text, limit=1000)
        self.assertTrue(all(len(p) <= 1000 for p in out.split("\n")))
        self.assertTrue(all(p.endswith(".") for p in out.split("\n")[:-1]))
        self.assertEqual(out.replace("\n", " ").split(), text.split())

    def test_short_text_is_untouched(self):
        self.assertEqual(pc.split_long_paragraphs("أ\nب"), "أ\nب")


class Rows(unittest.TestCase):
    def test_identical_neighbours_merge_into_one_range(self):
        rows = [pc.Row(2, 17, 17, "س", []), pc.Row(2, 18, 18, "س", []), pc.Row(2, 19, 19, "ص", []), pc.Row(3, 1, 1, "ص", [])]
        merged, joined = pc.merge_identical(rows)
        self.assertEqual(joined, 1)
        self.assertEqual([(r.sura, r.from_ayah, r.to_ayah) for r in merged], [(2, 17, 18), (2, 19, 19), (3, 1, 1)])

    def test_a_cut_row_says_so_and_drops_the_half_word(self):
        out = pc.mark_cut("أنا أبو القاسم، أنا أب")
        self.assertTrue(out.endswith(pc.CUT_NOTICE))
        self.assertIn("أنا أبو القاسم، أنا …", out)
        self.assertNotIn("أب\n", out)

    def test_corrections_must_match_exactly_once(self):
        rows = {(1, 1): "أ ب أ"}
        with self.assertRaises(ValueError):
            pc.apply_corrections(rows, [("1:1", "أ", "ج", "")])
        self.assertEqual(pc.apply_corrections(rows, [("1:1", "ب", "ج", "")])[(1, 1)], "أ ج أ")

    def test_an_adjacent_redirect_joins_the_row_and_a_far_one_stays(self):
        rows = [pc.Row(4, 167, 167, "أ", []), pc.Row(4, 169, 169, "ب", []), pc.Row(4, 175, 175, "ج", [])]
        left = pc.extend_to_neighbours(rows, {(4, 168): (4, 169), (4, 171): (4, 175)})
        self.assertEqual((rows[1].from_ayah, rows[1].to_ayah), (168, 169))
        self.assertEqual(left, {(4, 171): (4, 175)})

    def test_redirects_find_the_row_quoting_the_ayah_number(self):
        cleaned = {
            (4, 167): ("القول في {الآية (167)}", []),
            (4, 168): ("", []),
            (4, 169): ("القول في {طريقا (168) إلا طريق جهنم (169)}", []),
        }
        self.assertEqual(pb.find_redirects(cleaned, {(4, 168)}), {(4, 168): (4, 169)})


class Checks(unittest.TestCase):
    def test_anchors_must_match_notes(self):
        with self.assertRaises(ValueError):
            pc.check_text("x", "نص ⟦1⟧ و⟦2⟧", ["حاشية"])
        pc.check_text("x", "نص ⟦1⟧", ["حاشية"])

    def test_bookkeeping_marks_stop_the_build(self):
        for mark in ("¬", "ممم", "<br>", "[as1]", "___"):
            with self.assertRaises(ValueError):
                pc.check_text("x", f"نص {mark} نص", [])


class Packs(unittest.TestCase):
    def test_a_full_pack_verifies_and_compresses_reproducibly(self):
        rows = [pc.Row(s, a, a, f"تفسير {s}:{a}", []) for s, a in pc.ALL_AYAHS if (s, a) != (1, 2)]
        rows[0] = pc.Row(1, 1, 2, "تفسير الآيتين ⟦1⟧", ["أخرجه مسلم."])
        meta = {k: "1" if k in ("version", "schema") else "س" for k in pc.REQUIRED_META}
        meta["kind"] = "tafsir"
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "p.db"
            pc.write_tafsir_pack(path, meta, rows, pc.assign_groups(rows, {}))
            self.assertEqual(pc.verify_pack(path)["kind"], "tafsir")
            first, second = Path(folder) / "a.gz", Path(folder) / "b.gz"
            pc.gzip_deterministic(path, first)
            pc.gzip_deterministic(path, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(gzip.decompress(first.read_bytes()), path.read_bytes())
            with closing(sqlite3.connect(path)) as db:
                group = db.execute("SELECT group_id FROM ayah_group WHERE ayah_key = 1002").fetchone()[0]
                self.assertEqual(db.execute("SELECT from_ayah, to_ayah FROM passage WHERE group_id = ?", (group,)).fetchone(), (1, 2))

    def test_a_pack_missing_an_ayah_fails(self):
        rows = [pc.Row(s, a, a, "ت", []) for s, a in pc.ALL_AYAHS[:-1]]
        with self.assertRaises(ValueError):
            pc.assign_groups(rows, {})


class BookSpecs(unittest.TestCase):
    def test_every_book_has_what_the_app_shows(self):
        for book in pb.BOOKS.values():
            self.assertTrue(book.title and book.credit and book.changes)
            self.assertIn("CC BY 4.0", book.credit)
            for where, wrong, right, reason in book.corrections:
                self.assertTrue(wrong and reason, where)
                self.assertNotEqual(wrong, right)


if __name__ == "__main__":
    unittest.main()
