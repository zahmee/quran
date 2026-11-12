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


if __name__ == "__main__":
    unittest.main()
