"""Tests for saadi_center.py. Run from this folder: python -m unittest test_saadi_center"""

from __future__ import annotations

import unittest

import saadi_center as sc


class NoteKinds(unittest.TestCase):
    def test_hadith_sources_and_corrections_are_shown(self):
        self.assertEqual("hadith_source", sc.note_kind("أخرجه مسلم (810) من حديث أبي بن كعب رضي الله عنه."))
        self.assertEqual("correction", sc.note_kind("كذا في النسختين، وقد حكى الإجماع على مكيتها ابن الجوزي والقرطبي."))
        self.assertEqual("correction", sc.note_kind("كذا في (ب). وفي (أ): «يقتسمون». والصواب ما أثبت."))
        self.assertIn("hadith_source", sc.SHOWN_NOTE_KINDS)
        self.assertIn("correction", sc.SHOWN_NOTE_KINDS)

    def test_manuscript_variants_stay_out(self):
        for note in ("في (ب): «لهم».", "كذا في (ب)، وفي (أ): «والمكذبين».", "كذا في النسختين.",
                     "في (ب): «والله سبحانه وتعالى أعلم بالصواب».",
                     "كذا في النسختين. وعدلت في (أ) إلى «فحرَّم» بخطٍّ مغاير."):
            self.assertEqual("manuscript", sc.note_kind(note), note)
            self.assertNotIn(sc.note_kind(note), sc.SHOWN_NOTE_KINDS)

    def test_a_pointer_to_a_print_page_stays_out(self):
        self.assertEqual("print_page", sc.note_kind("تقدم تخريجه ص (116)."))
        self.assertEqual("print_page", sc.note_kind("سبق تخريجه."))
        self.assertEqual("hadith_source", sc.note_kind("تقدم تخريجه، وهو في «صحيح مسلم» (8)."))


class ParseRow(unittest.TestCase):
    def parse(self, aya, raw, sura=2):
        notes = []
        return sc.parse_row(sura, aya, raw, notes), notes

    def roles(self, parsed):
        return [(u.role, u.text) for u in parsed.units]

    def test_heading_block_and_explanation_are_told_apart(self):
        raw = "تفسير سورة البقرة\nوهي مدنية\n{الم (1)}.\nتقدم الكلام على البسملة.\n{1} وأما الحروف."
        parsed, _ = self.parse(1, raw)
        self.assertEqual(
            [("title", "تفسير سورة البقرة"), ("place", "وهي مدنية"),
             ("lead_after", "تقدم الكلام على البسملة."), ("body", "{1} وأما الحروف.")],
            self.roles(parsed),
        )

    def test_a_lead_in_on_the_blocks_own_line_stays_and_the_block_goes(self):
        raw = "فقال:{وَإِذَا مَسَّ النَّاسَ ضُرٌّ دَعَوْا رَبَّهُمْ (33)}.\n{33} شرح."
        parsed, _ = self.parse(33, raw)
        self.assertEqual([("lead", "فقال:"), ("body", "{33} شرح.")], self.roles(parsed))

    def test_a_block_without_a_full_stop_leaves_the_next_line_intact(self):
        # 2:255 ends its block with no full stop; the number mark must still start its own line.
        parsed, _ = self.parse(255, "{اللَّهُ لَا إِلَهَ إِلَّا هُوَ (255)}\n{255} أخبر.")
        self.assertEqual([("body", "{255} أخبر.")], self.roles(parsed))

    def test_a_lead_in_after_the_block_moves_before_it_when_it_ends_in_a_colon(self):
        raw = "{وَمِنَ النَّاسِ مَنْ يَقُولُ (8)}.\nثم قال تعالى:\n{8} شرح."
        parsed, _ = self.parse(8, raw)
        self.assertEqual([("lead", "ثم قال تعالى:"), ("body", "{8} شرح.")], self.roles(parsed))

    def test_the_sentence_saying_he_did_not_explain_it_is_dropped_and_remembered(self):
        for wording in ("لم يُفسِّرها الشيخ رحمه الله.", "{50} لم يُفسِّر الشيخ رحمه الله هذه الآية."):
            parsed, _ = self.parse(50, "{وَإِذْ فَرَقْنَا بِكُمُ الْبَحْرَ (50)}.\n" + wording)
            self.assertTrue(parsed.placeholder, wording)
            self.assertEqual([], parsed.units)

    def test_what_follows_a_closing_line_is_part_of_the_closing(self):
        raw = "{165} شرح.\nآخر تفسير سورة الأنعام.\nفلله الحمد والثناء.\n* * *"
        parsed, _ = self.parse(165, raw, sura=6)
        self.assertEqual(
            [("body", "{165} شرح."), ("closing", "آخر تفسير سورة الأنعام."),
             ("closing", "فلله الحمد والثناء."), ("divider", "* * *")],
            self.roles(parsed),
        )

    def test_a_note_running_over_lines_is_one_note(self):
        raw = "{8} شرح ¬أخرجه البخاري.\nوأما الثانية فأخرجها مسلم.¥. تتمة."
        parsed, notes = self.parse(8, raw)
        self.assertEqual(1, len(notes))
        self.assertIn("وأما الثانية", notes[0][2])
        self.assertEqual(1, len(parsed.units))
        self.assertIn("تتمة", parsed.units[0].text)

    def test_a_block_cut_by_a_note_over_two_lines_still_goes(self):
        raw = "{يَاأَيُّهَا الَّذِينَ آمَنُوا إِذَا نُودِيَ ¬في (ب): ذكر الآيات.¥ وَذَرُوا\nالْبَيْعَ ذَلِكُمْ خَيْرٌ لَكُمْ (9)}.\n{9} يأمر تعالى."
        parsed, _ = self.parse(9, raw, sura=62)
        self.assertEqual(["{9} يأمر تعالى."], [u.text for u in parsed.units])


class Corrections(unittest.TestCase):
    def test_a_correction_must_match_exactly_once(self):
        keys = {key for key, *_ in sc.CORRECTIONS}
        with self.assertRaises(ValueError):
            sc.apply_corrections({key: "نص لا يطابق" for key in keys})


class Repeats(unittest.TestCase):
    def run_of(self, words, i1, i2, starts=()):
        return sc._repeated_runs(words, i1, i2, set(starts))

    def test_a_sentence_said_again_nearby_is_found(self):
        sentence = "فهذا هو وحده المنفرد بذلك فيجب أن يفرد".split()
        words = "قال".split() + sentence + "ثم".split() + sentence + "وانتهى".split()
        start = 1 + len(sentence) + 1
        self.assertEqual([(start, start + len(sentence))], self.run_of(words, start, start + len(sentence)))

    def test_a_common_phrase_inside_moved_text_is_not_a_repeat(self):
        phrase = "محمد صلى الله عليه وسلم".split()
        words = phrase + "كلام اخر طويل هنا".split() + phrase + "ثم نص منقول من مكان اخر".split()
        self.assertEqual([], self.run_of(words, 9, len(words)))

    def test_a_short_phrase_next_to_itself_is_a_repeat(self):
        words = "من عظيم امره من عظيم امره انه تكاد".split()
        self.assertEqual([(3, 6)], self.run_of(words, 3, 6))


class DeleteTokens(unittest.TestCase):
    def cut(self, text, indexes):
        unit = sc.Unit(1, 1, "body", text)
        unit.tokenize()
        sc._delete_tokens(unit, set(indexes))
        return unit.text

    def test_a_cut_keeps_braces_balanced(self):
        self.assertEqual("أ ج", self.cut("أ {ب} ج", [1]))
        # Cutting from inside a quote to past its end must leave the quote's end in place.
        self.assertEqual("أ {ب} د", self.cut("أ {ب ج}: د", [2]))

    def test_note_anchors_outside_the_cut_stay(self):
        anchor = chr(0xF0000)
        self.assertIn(anchor, self.cut(f"أ{anchor} ب ج", [2]))


class Blocks(unittest.TestCase):
    def test_the_quran_row_is_the_approved_text_with_arabic_numbers(self):
        approved = {"1:1": " نص١", "1:2": "نص٢"}
        self.assertEqual("نص١ (١) نص٢ (٢)", sc.quran_block(1, [1, 2], approved))


if __name__ == "__main__":
    unittest.main()
