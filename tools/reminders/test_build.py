"""Tests for the selection rules in build.py. Offline: they use made-up verses, not the sources."""
import unittest

from build import (
    Collection, Verse, numbers_from_reference, rotation, select_verses, self_contained,
    starts_a_sentence, usable_text, verse_reminder,
)


def verses(*english: str, surah: int = 2) -> list[Verse]:
    return [Verse(surah, i + 1, f"آية {i + 1}", e) for i, e in enumerate(english)]


class VerseRules(unittest.TestCase):
    def test_continuations_do_not_open_a_reminder(self):
        for text in ["And establish prayer.", "Those who believe will be rewarded.", "they will be questioned.",
                     "Lord of the heavens and earth.", "[Remember] when your Lord said...", "On that Day they will know."]:
            self.assertFalse(starts_a_sentence(text), text)
        for text in ["Indeed, Allah is with the patient.", "O you who believe, seek help in patience.",
                     "[O Prophet], say: He is Allah.", "Say, “He is Allah, the One.”"]:
            self.assertTrue(starts_a_sentence(text), text)

    def test_speech_inside_a_story_is_not_self_contained(self):
        self.assertFalse(self_contained("What is wrong with you that you do not speak?”"))
        self.assertFalse(self_contained("O my people, worship Allah."))
        self.assertTrue(self_contained("Say, “My Lord knows best, and He is with me.”"))

    def test_pronouns_need_something_to_refer_to(self):
        self.assertFalse(self_contained("The decree will befall them because of their wrongdoing."))
        self.assertTrue(self_contained("Those who believe, their reward is with their Lord."))
        # A capital He, Him, His is Allah in this translation.
        self.assertTrue(self_contained("He knows what is in the hearts."))

    def test_footnotes_stories_and_rulings_are_left_out(self):
        self.assertFalse(usable_text("Therefore remember Me; I will remember you[66]."))
        self.assertFalse(usable_text("Moses said to his people, “Seek help from Allah.”"))
        self.assertFalse(usable_text("Divorced women shall wait for three periods."))
        self.assertTrue(usable_text("Allah does not burden any soul beyond what it can bear."))


class Selection(unittest.TestCase):
    def test_a_standalone_verse_is_taken_alone(self):
        chosen = select_verses(verses("Indeed, Allah is with those who are patient and do good.", "Allah is Most Merciful to all the believers."), set())
        self.assertEqual([[1], [2]], [[v.ayah for v in g] for g in chosen])

    def test_a_sentence_over_two_verses_becomes_a_passage(self):
        chosen = select_verses(verses("Indeed, with hardship comes ease for the believer,", "indeed, with hardship comes ease."), set())
        self.assertEqual([[1, 2]], [[v.ayah for v in g] for g in chosen])
        item = verse_reminder(chosen[0])
        self.assertEqual("q2_1_2", item["id"])
        self.assertEqual("Surah al-Baqarah 2:1–2 · QuranEnc.com", item["reference"])
        self.assertEqual("آية 1 ﴿١﴾ آية 2", item["arabic"])

    def test_a_sentence_that_never_ends_is_dropped(self):
        long = ["Indeed, the believers are those who remember Allah often,"] + ["and who pray at night,"] * 4
        self.assertEqual([], select_verses(verses(*long), set()))

    def test_excluded_ids_are_left_out(self):
        self.assertEqual([], select_verses(verses("Indeed, Allah is with those who are patient and do good."), {"q2_1"}))


class HadithNumbers(unittest.TestCase):
    def test_reading_hadeethenc_references(self):
        reference = "صحيح البخاري (9/ 2) (6864).\nصحيح مسلم (3/ 1304) (1678)."
        self.assertEqual(6864, numbers_from_reference(reference, "bukhari"))
        self.assertEqual(1678, numbers_from_reference(reference, "muslim"))
        one_line = "صحيح البخاري (1/ 11) (8)، صحيح مسلم (1/ 45) (16)، و(1/ 88) (82)"
        self.assertEqual(8, numbers_from_reference(one_line, "bukhari"))
        self.assertEqual(16, numbers_from_reference(one_line, "muslim"))
        # A commentary's volume and page is not a hadith number.
        self.assertIsNone(numbers_from_reference("شرح صحيح مسلم (140/7).", "muslim"))

    def test_matching_ignores_vowels_and_the_chain(self):
        book = Collection([
            (1, "حَدَّثَنَا فُلَانٌ قَالَ ‏\"‏ إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى ‏\"‏"),
            (2, "حَدَّثَنَا فُلَانٌ قَالَ الدِّينُ النَّصِيحَةُ لِلَّهِ وَلِكِتَابِهِ"),
        ])
        number, share = book.find("عن عمر قال: «إنما الأعمال بالنيات، وإنما لكل امرئ ما نوى»")
        self.assertEqual((1, 1.0), (number, share))
        self.assertFalse(book.contradicts("«إنما الأعمال بالنيات وإنما لكل امرئ ما نوى»", 1, 1))


class Rotation(unittest.TestCase):
    def test_kinds_take_turns_and_order_is_stable(self):
        items = [{"id": f"q1_{i}", "kind": "verse"} for i in range(30)] + [{"id": f"h{i}", "kind": "hadith"} for i in range(20)]
        order = rotation(items)
        self.assertEqual(order, rotation(list(reversed(items))))
        kinds = [r["kind"] for r in order]
        self.assertFalse(any(len(set(kinds[i:i + 3])) == 1 for i in range(len(kinds) - 2)))


if __name__ == "__main__":
    unittest.main()
