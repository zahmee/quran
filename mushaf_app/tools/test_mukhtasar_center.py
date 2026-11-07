"""Tests for mukhtasar_center.py. Run from this folder: python -m unittest test_mukhtasar_center"""

from __future__ import annotations

import unittest

import mukhtasar_center as mc


class Verify(unittest.TestCase):
    keys = {f"1:{n}" for n in range(1, mc.EXPECTED_ROWS + 1)}

    def rows(self, **changed):
        rows = {key: "نص" for key in self.keys}
        rows.update({key.replace("_", ":"): text for key, text in changed.items()})
        return rows

    def test_balanced_marks_pass(self):
        mc.verify(self.rows(**{"1_1": "لا يأخذه _نعاس_ ولا نوم"}), self.keys)

    def test_a_lone_mark_stops_the_build(self):
        with self.assertRaises(ValueError):
            mc.verify(self.rows(**{"1_1": "الطريق سالك_"}), self.keys)

    def test_a_mark_paired_across_the_wrong_words_stops_the_build(self):
        with self.assertRaises(ValueError):
            mc.verify(self.rows(**{"1_1": "_جماعة_ _من علمائهم ثم _يغيرون_"}), self.keys)
        long_text = " ".join(["كلمة"] * (mc.LONGEST_GLOSS_WORDS + 1))
        with self.assertRaises(ValueError):
            mc.verify(self.rows(**{"1_1": f"_{long_text}_"}), self.keys)

    def test_a_missing_ayah_stops_the_build(self):
        rows = self.rows()
        rows.pop("1:1")
        with self.assertRaises(ValueError):
            mc.verify(rows, self.keys)


class Corrections(unittest.TestCase):
    def test_a_correction_must_match_exactly_once(self):
        keys = {key for key, *_ in mc.CORRECTIONS}
        with self.assertRaises(ValueError):
            mc.apply_corrections({key: "نص لا يطابق" for key in keys})


if __name__ == "__main__":
    unittest.main()
