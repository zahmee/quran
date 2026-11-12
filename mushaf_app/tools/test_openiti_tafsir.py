"""Tests for openiti_tafsir.py. Run from this folder: python -m unittest test_openiti_tafsir"""

from __future__ import annotations

import unittest

import openiti_tafsir as ot


def surahs() -> ot.Surahs:
    return ot.Surahs(
        by_name={ot._fold("الكهف"): 18, ot._fold("آل عمران"): 3, ot._fold("القصص"): 28},
        skeletons={
            18: ot.skeleton("والباقيات الصالحات خير عند ربك ثوابا وخير أملا"),
            3: ot.skeleton("وإن من أهل الكتاب لمن يؤمن بالله وما أنزل إليكم وما أنزل إليهم"),
            28: ot.skeleton("الذين آتيناهم الكتاب من قبله هم به يؤمنون"),
        },
    )


class ParagraphTest(unittest.TestCase):
    def test_continuations_join_and_milestones_go(self):
        lines = ["# قال أهل اللغة: أعضل الأمر: اشتد", "~~واستغلق ms0001 والمعضلات", "", "# فقرة ثانية p>>"]
        self.assertEqual(ot.paragraphs(lines), ["قال أهل اللغة: أعضل الأمر: اشتد واستغلق والمعضلات", "فقرة ثانية"])

    def test_stray_text_fails(self):
        with self.assertRaises(ValueError):
            ot.paragraphs(["نص بلا علامة"])


class QuoteTest(unittest.TestCase):
    def test_bracketed_citation_braces_the_quote_and_joins_the_sentence(self):
        paras = ["قال الله تعالى:", "والباقيات الصالحات خير عند ربك ثوابا وخير أملا", "[الكهف: 46]. وقيل غير ذلك"]
        out, marked = ot.mark_quotes(paras, surahs())
        self.assertEqual(marked, 1)
        self.assertEqual(out, ["قال الله تعالى: {والباقيات الصالحات خير عند ربك ثوابا وخير أملا} [الكهف: 46]. وقيل غير ذلك"])

    def test_bare_citation_of_a_run_and_a_two_word_surah(self):
        paras = ["واستشهد بقوله تعالى", "وإن من أهل الكتب لمن يؤمن بالله", "آل عمران 119 وبقوله تعالى",
                 "الذين ءاتينهم الكتب من قبله", "الآية القصص 52 54. والآية الأولى"]
        out, marked = ot.mark_quotes(paras, surahs())
        self.assertEqual(marked, 2)
        self.assertEqual(out, [
            "واستشهد بقوله تعالى {وإن من أهل الكتب لمن يؤمن بالله} [آل عمران: 119] وبقوله تعالى "
            "{الذين ءاتينهم الكتب من قبله} [القصص: 52-54]. والآية الأولى"
        ])

    def test_commentary_before_a_citation_is_not_taken_for_a_quote(self):
        paras = ["وهذا قول جمهور المفسرين في معنى الآية.", "[الكهف: 46]"]
        out, marked = ot.mark_quotes(paras, surahs())
        self.assertEqual(marked, 0)
        self.assertEqual(out, paras)

    def test_a_surah_name_that_is_not_one_is_no_citation(self):
        self.assertIsNone(ot._citation("الناس 5 يقولون", surahs()))


class BooksTest(unittest.TestCase):
    def test_every_book_is_pinned(self):
        for book in ot.BOOKS.values():
            self.assertEqual(len(book.file_sha256), 64)
            self.assertEqual(len(book.commit), 40)
            self.assertIn(book.file, book.source_url)
            self.assertIn("CC BY-NC-SA 4.0", book.credit)


if __name__ == "__main__":
    unittest.main()
