import unittest

from blobs_corpus.places import place_indicator, read_selection


class SelectionTest(unittest.TestCase):
    def test_amenity_only_selection_names_the_matching_tag(self):
        selection = read_selection({"amenities": ["pharmacy", "hospital"]})
        self.assertEqual(selection["amenities"], ["hospital", "pharmacy"])
        self.assertEqual(
            place_indicator({"amenity": "pharmacy", "name": "Wellcare"}, selection), "amenity=pharmacy"
        )
        self.assertIsNone(place_indicator({"amenity": "restaurant", "name": "Warung"}, selection))

    def test_tag_rule_is_required_when_declared(self):
        selection = read_selection(
            {
                "amenities": ["restaurant", "cafe"],
                "tag_any": [{"key": "diet:vegan", "values": ["yes", "only"]}],
            }
        )
        self.assertEqual(
            place_indicator({"amenity": "cafe", "diet:vegan": "only"}, selection),
            "diet:vegan includes only",
        )
        self.assertIsNone(place_indicator({"amenity": "cafe", "diet:vegetarian": "yes"}, selection))
        self.assertEqual(
            place_indicator({"amenity": "restaurant", "cuisine": "italian;vegan"},
                            read_selection({"amenities": ["restaurant"],
                                            "tag_any": [{"key": "cuisine", "values": ["vegan"]}]})),
            "cuisine includes vegan",
        )

    def test_selection_rejects_unreviewable_rules(self):
        for value in (
            None,
            {"amenities": []},
            {"amenities": ["Pharmacy"]},
            {"amenities": ["pharmacy", "pharmacy", "cafe", "hospital", "clinic", "dentist", "bank",
                           "atm", "fuel", "water_point", "shelter", "toilets", "kindergarten", "school"]},
            {"amenities": ["pharmacy"], "tag_any": [{"key": "diet:vegan"}]},
            {"amenities": ["pharmacy"], "tag_any": [{"key": "diet:vegan", "values": []}]},
            {"amenities": ["pharmacy"], "tag_any": [{"key": "bad key", "values": ["yes"]}]},
        ):
            with self.assertRaises(ValueError):
                read_selection(value)

    def test_tags_are_normalized_before_matching(self):
        selection = read_selection({"amenities": ["pharmacy"]})
        self.assertIsNone(place_indicator({}, selection))
        self.assertEqual(
            place_indicator({"amenity": " Pharmacy ", "name": "  "}, selection), "amenity=pharmacy"
        )


if __name__ == "__main__":
    unittest.main()
