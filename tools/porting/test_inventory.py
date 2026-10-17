"""Guard the source-linked recipe audit against losing the central catalog."""

import unittest

from build_inventory import inventory


class InventoryTests(unittest.TestCase):
    def test_central_crafting_manager_is_indexed(self):
        rows = inventory()
        keys = {(row["kind"], row["key"]) for row in rows}
        for key in (
            "CraftingManager:108",  # String, wool, string -> Insulator.
            "CraftingManager:109",  # Brick -> Insulator.
            "CraftingManager:110",  # Nether brick -> Insulator.
            "CraftingManager:256",  # Battery socket.
            "CraftingManager:258",  # Redstone battery pack.
        ):
            with self.subTest(key=key):
                self.assertIn(("recipe_site", key), keys)
        self.assertIn(("recipe_source", "CraftingManager"), keys)

    def test_recipe_site_keys_are_unique(self):
        keys = [row["key"] for row in inventory() if row["kind"] == "recipe_site"]
        self.assertEqual(len(keys), len(set(keys)))


if __name__ == "__main__":
    unittest.main()
