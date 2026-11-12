"""Tests for center_extras.py. Run from this folder: python -m unittest test_center_extras"""

from __future__ import annotations

import unittest

import center_extras as ce
import pack_common as pc


class SajdahTest(unittest.TestCase):
    def test_none(self):
        self.assertEqual(ce.parse_sajdah("لا"), [])

    def test_one_and_two(self):
        self.assertEqual(ce.parse_sajdah("نعم، الآية 206"), [206])
        self.assertEqual(ce.parse_sajdah("نعم، الآية 18، والآية 77"), [18, 77])

    def test_unknown_value_fails(self):
        with self.assertRaises(ValueError):
            ce.parse_sajdah("ربما")


class FawaedTest(unittest.TestCase):
    def rows(self, first: str) -> list[tuple[int, str]]:
        return [(1, first)] + [(page, "• فائدة.") for page in range(2, ce.PAGES + 1)]

    def test_bullet_dropped_and_order_kept(self):
        out = ce.clean_fawaed(self.rows("•  افتتح الله كتابه بالبسملة.") + [(1, "• ثانية.")])
        self.assertEqual(out[1], ["افتتح الله كتابه بالبسملة.", "ثانية."])

    def test_missing_bullet_fails(self):
        with self.assertRaises(ValueError):
            ce.clean_fawaed(self.rows("بلا علامة."))

    def test_missing_page_fails(self):
        with self.assertRaises(ValueError):
            ce.clean_fawaed(self.rows("• فائدة.")[:-1])


class SectionTextTest(unittest.TestCase):
    def test_own_heading_dropped(self):
        self.assertEqual(ce.drop_own_heading("فضائل سورة البقرة\nنص"), "نص")
        self.assertEqual(ce.drop_own_heading("نزول السورة\nنص"), "نص")
        self.assertEqual(ce.drop_own_heading("سورة الكوثر\nاسمها التوقيفي:"), "اسمها التوقيفي:")

    def test_other_first_line_kept(self):
        text = "بيان السور السبع الطول\nنص"
        self.assertEqual(ce.drop_own_heading(text), text)

    def test_truncated_mid_word_loses_half_word(self):
        self.assertEqual(ce.mark_truncated("قال: وشذ م"), "قال: وشذ …\n" + pc.CUT_NOTICE)

    def test_truncated_at_full_stop_keeps_everything(self):
        self.assertEqual(ce.mark_truncated("انتهى.¥"), "انتهى.¥\n" + pc.CUT_NOTICE)


class TajweedTest(unittest.TestCase):
    def test_print_line_breaks_join_and_br_breaks_a_paragraph(self):
        raw = "{الْحَمْدُ} لام قمرية. ويحترز من\nفتح الدال.<br>ولام اسم الجلالة مرققة."
        self.assertEqual(
            ce.clean_tajweed_text(raw),
            "{الْحَمْدُ} لام قمرية. ويحترز من فتح الدال.\nولام اسم الجلالة مرققة.",
        )

    def test_stray_opening_punctuation_dropped(self):
        self.assertEqual(ce.clean_tajweed_text("، مد بدل في {آلَاءِ}"), "مد بدل في {آلَاءِ}")
        self.assertEqual(ce.clean_tajweed_text(": في الدال قلقلة"), "في الدال قلقلة")

    def test_lost_list_numbers_become_bullets(self):
        raw = "أحكام:<br>() الإظهار.<br>) الإقلاب."
        self.assertEqual(ce.clean_tajweed_text(raw), "أحكام:\n• الإظهار.\n• الإقلاب.")

    def test_lost_marks_removed(self):
        self.assertEqual(ce.clean_tajweed_text("نهاية الآية ()، فإن"), "نهاية الآية، فإن")
        self.assertEqual(ce.clean_tajweed_text("في {قَدِيرًا () مَنْ}"), "في {قَدِيرًا مَنْ}")

    def test_print_footnotes_dropped(self):
        raw = "مد بدل.<br>_________<br>() ينظر: كتابنا."
        self.assertEqual(ce.clean_tajweed_text(raw), "مد بدل.")

    def test_mixed_brackets_around_a_quote_are_mended(self):
        self.assertEqual(ce.clean_tajweed_text("إدغام (وَمَوْعِظَةُ} بغير"), "إدغام {وَمَوْعِظَةُ} بغير")

    def test_a_note_shared_by_ayahs_becomes_one_range(self):
        rows = [(s, a, None) for s, a in pc.ALL_AYAHS]
        rows[1] = (1, 2, "مد.")
        rows[2] = (1, 3, "مد.")
        rows[3] = (1, 4, "-")
        notes = ce.clean_tajweed(rows, corrections=[])
        self.assertEqual([(n.surah, n.from_ayah, n.to_ayah, n.text) for n in notes], [(1, 2, 3, "مد.")])


class SurahStatsTest(unittest.TestCase):
    def rows(self, first: tuple) -> list[tuple]:
        filler = ("خبر", None, 10, 40, 5, 5, "لا يوجد", "كلمة", "لا يوجد")
        return [first] + [(s, *filler) for s in range(2, ce.SURAHS + 1)]

    def test_fatiha(self):
        row = (1, "ثناء", "القرآن", 29, 139, 7, 7, "لا يوجد", "الْمُسْتَقِيمَ", "ث/ج/خ/ز/ش/ظ/ف/ء/ى/ئ/ؤ/ة")
        stats = ce.surah_stats(self.rows(row))[0]
        self.assertEqual(stats.missing_letters, "ثجخزشظف")  # hamza and its seats are not letters
        self.assertEqual(stats.longest_words, ["الْمُسْتَقِيمَ"])
        self.assertEqual(stats.repeated_ayahs, [])

    def test_repeats_sorted_and_ties_dropped(self):
        row = (1, "أمر", "أعلام - القيامة", 30, 120, 7, 5, "6/2/4", "أ/ب/ج", "لا يوجد")
        stats = ce.surah_stats(self.rows(row))[0]
        self.assertEqual(stats.repeated_ayahs, [2, 4, 6])
        self.assertEqual(stats.longest_words, [])
        self.assertEqual(stats.name_topic, "أعلام، القيامة")

    def test_repeats_that_do_not_add_up_fail(self):
        row = (1, "أمر", None, 30, 120, 7, 7, "2/4", "كلمة", "لا يوجد")
        with self.assertRaises(ValueError):
            ce.surah_stats(self.rows(row))


class RootTest(unittest.TestCase):
    def test_app_words_skip_lone_waqf_signs(self):
        self.assertEqual(ce.app_words("ذَٰلِكَ ٱلْكِتَـٰبُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًى"), ["ذَٰلِكَ", "ٱلْكِتَـٰبُ", "لَا", "رَيْبَ", "فِيهِ", "هُدًى"])

    def test_one_source_word_over_two_mushaf_words(self):
        app = ["لَّوْ", "مَا", "تَأْتِينَا"]
        self.assertEqual(ce.align_to_app(["لوما", "تأتينا"], app), [[0, 1], [2]])

    def test_same_count_maps_one_to_one(self):
        self.assertEqual(ce.align_to_app(["يا/قوم", "اذكروا"], ["يَـٰقَوْمِ", "ٱذْكُرُوا۟"]), [[0], [1]])

    def test_base_form_drops_prefixes_only_onto_attested_forms(self):
        attested = {"رحمة", "ورحمة", "الرحمن", "للرحمن", "والرحمن", "بلد"}
        self.assertEqual(ce.base_form("ورحمة", attested), "رحمة")
        self.assertEqual(ce.base_form("للرحمن", attested), "الرحمن")
        self.assertEqual(ce.base_form("والرحمن", attested), "الرحمن")
        self.assertEqual(ce.base_form("بلد", attested), "بلد")

    def test_root_rows_encode_forms_and_places(self):
        words = [
            ce.RootWord(1, 3, [1], "رحم", "الرحيم"),
            ce.RootWord(1, 3, [0], "رحم", "الرحمن"),
            ce.RootWord(2, 37, [7], "رحم", "الرحيم"),
            ce.RootWord(1, 2, [0], "حمد", "الحمد"),
        ]
        rows = ce.root_rows(words)
        self.assertEqual([r.root for r in rows], ["حمد", "رحم"])
        rahma = rows[1]
        self.assertEqual((rahma.word_count, rahma.ayah_count), (3, 2))
        self.assertEqual(rahma.forms, "الرحيم\t2\nالرحمن\t1")
        self.assertEqual(rahma.occurrences, "1003:0:1 1003:1:0 2037:7:0")


if __name__ == "__main__":
    unittest.main()
