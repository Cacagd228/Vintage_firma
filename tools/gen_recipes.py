"""Generates Vintage Firma smithing recipes for every metal and every TFC anvil item.

Run with: python tools/gen_recipes.py

Patterns are simple, readable voxel shapes in a 16x6x16 grid, authored as layers from the bottom up.
They are deliberately generic (a head, a blade, a plate, ...) - the goal of this mod is the mechanic,
not a pixel accurate copy of the original game.

Every recipe is generated for every metal: the shape is identical, only the metal (and the anvil tier
that metal needs) changes. The ingredient is always a plain ingot of that metal.
"""

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "vintage_firma", "recipes", "smithing")

# Metal id -> (anvil tier, voxels per ingot, voxels per sheet)
METALS = {
    "copper": (1, 42, 81),
    "bismuth_bronze": (2, 42, 81),
    "black_bronze": (2, 42, 81),
    "bronze": (2, 42, 81),
    "wrought_iron": (3, 42, 81),
    "steel": (4, 42, 81),
    "black_steel": (5, 42, 81),
    "blue_steel": (6, 42, 81),
    "red_steel": (6, 42, 81),
}

# item -> (result path, layers)
# Every recipe is made from plain ingots: the number of ingots is simply the voxel count of the pattern
# (one ingot is 42 voxels). This is what the player actually does - place one ingot, spread the metal,
#         # add another one - so "double ingots" are only produced by welding.
ITEMS = {
    "shovel_head": ("metal/shovel_head", [
        [".###.", "#####", "#####", "#####", "#####", "#####", "..#..", "..#.."],
    ]),
    "pickaxe_head": ("metal/pickaxe_head", [
        ["....###....", "..#######..", "###.....###"],
    ]),
    "axe_head": ("metal/axe_head", [
        ["#....", "#####", "#####", "#####", "#...."],
    ]),
    "hoe_head": ("metal/hoe_head", [
        [".#####", "######"],
    ]),
    "hammer_head": ("metal/hammer_head", [
        ["######", "######", "######"],
        ["######", "######", "######"],
        ["######", "######", "######"],
    ]),
    "knife_blade": ("metal/knife_blade", [
        [".#", "##", "##", "##", "##", "##", ".#", ".#"],
    ]),
    "sword_blade": ("metal/sword_blade", [
        [".##.", "####", "####", "####", "####", "####", "####", "####", "####"],
    ]),
    "javelin_head": ("metal/javelin_head", [
        [".#.", "###", "###", "###", "###", "###", "###"],
    ]),
    "mace_head": ("metal/mace_head", [
        [".###.", "#####", ".###.", "#####", ".###.", "#####", ".###.", ".###.", ".###.", "..#..", "..#.."],
    ]),
    "scythe_blade": ("metal/scythe_blade", [
        [".....######", "...######..", ".#####.....", "##........."],
    ]),
    "saw_blade": ("metal/saw_blade", [
        ["#############", "#############", ".############", "..#.#.#.#.#.#"],
    ]),
    "chisel_head": ("metal/chisel_head", [
        ["######"],
    ]),
    "propick_head": ("metal/propick_head", [
        ["#######", ".######"],
    ]),
    "fish_hook": ("metal/fish_hook", [
        ["...#", "...#", "...#", "#..#", ".##."],
    ]),
    "rod": ("metal/rod", [
        ["##", "##", "##", "##", "##", "##", "##", "##", "##", "##", "##", "##", "##", "##"],
    ]),
    "chain": ("metal/chain", [
        ["###", "#.#", "###", ".#.", "###", "#.#", "###", ".#.", "###", "#.#", "###", ".#.", "###", "#.#", "###"],
    ]),
    "lamp": ("metal/unfinished_lamp", [
        ["..####..", "..#..#..", "..#..#..", "########", "#......#",
         "#......#", "#......#", "#......#", "#......#", "#......#",
         "#......#", "#......#", "########"],
    ]),
    "trapdoor": ("metal/trapdoor", [
        ["################", "################", "##...######...##", "##...######...##",
         "##...######...##", "################", "################", "################",
         "################", "################", "################", "##...######...##",
         "##...######...##", "##...######...##", "################", "################"],
    ]),
    "tuyere": ("metal/tuyere", [
        ["######", "#....#", "#....#", "#....#", "#....#", "#....#", "#....#",
         "#....#", "#....#", "#....#", "#....#", "#....#", "#....#", "######"],
    ]),
    "grill": ("metal/grill", [
        ["#.#.#.#.#.#.#.#", "#.#.#.#.#.#.#.#", "###.###.###.###", "#.#.#.#.#.#.#.#",
         "#.#.#.#.#.#.#.#", "#.###.###.###.#", "#.#.#.#.#.#.#.#", "#.#.#.#.#.#.#.#",
         "###.###.###.###", "#.#.#.#.#.#.#.#", "#.#.#.#.#.#.#.#", "#.###.###.###.#",
         "#.#.#.#.#.#.#.#", "#.#.#.#.#.#.#.#"],
    ]),
    "unfinished_helmet": ("metal/unfinished_helmet", [
        ["########", "########", "###..###", "##....##", "##....##", "##....##"],
    ]),
    "unfinished_chestplate": ("metal/unfinished_chestplate", [
        ["##.....##", "###...###", "#########", "#########", "#########",
         "#########", "#########", "#########", "#########", "#########"],
    ]),
    "unfinished_greaves": ("metal/unfinished_greaves", [
        ["########", "########", "##....##", "##....##", "##....##",
         "##....##", "##....##", "##....##", "##....##"],
    ]),
    "unfinished_boots": ("metal/unfinished_boots", [
        ["..##..##..", "..##..##..", "..##..##..", "####..####", "####..####"],
    ]),
    "shield": ("metal/shield", [
        ["############", "############", "############", "############", "############",
         "############", "############", "############", "############", "############",
         ".##########.", "..########..", "...######...", "....####...."],
    ]),
}


def voxel_count(layers):
    return sum(row.count("#") for layer in layers for row in layer)


def main():
    os.makedirs(OUT, exist_ok=True)

    written = 0
    for metal, (tier, per_ingot, per_sheet) in METALS.items():
        for item, entry in ITEMS.items():
            path, layers = entry[0], entry[1]
            voxels = voxel_count(layers)

            # Every recipe is started with a plain ingot. The amount of metal needed is simply the number
            # of voxels: one ingot is 42 voxels, so a second ingot is added by the player while forging
            # (this is exactly what the original game asks for: place, spread, add another ingot).
            ingredient = "tfc:metal/ingot/" + metal

            recipe = {
                "type": "vintage_firma:smithing",
                "ingredient": {"item": ingredient},
                "metal": metal,
                "tier": tier,
                "pattern_floor": 0,
                "pattern": layers,
                "result": {"item": "tfc:" + path + "/" + metal},
            }

            filename = "{}_{}.json".format(metal, item)
            with open(os.path.join(OUT, filename), "w", encoding="utf-8") as handle:
                json.dump(recipe, handle, ensure_ascii=False, indent=2)
                handle.write("\n")
            written += 1

    print("wrote {} recipes to {}".format(written, os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()