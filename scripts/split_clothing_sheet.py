#!/usr/bin/env python3
"""Split generated clothing sheets into Android drawable PNGs.

Example: python scripts/split_clothing_sheet.py infant sheet.png
Existing resources are preserved unless --overwrite is supplied.
"""

import argparse
from pathlib import Path

from PIL import Image


ITEMS = {
    "infant": (4, 4, "romper sandals shorts long_romper cardigan sneakers vest light_jacket sweatshirt light_padding beanie thermal snowsuit neck_warmer gloves winter_boots"),
    "child": (5, 4, "short_sleeve shorts sandals sneakers cardigan cotton_pants long_sleeve sweatshirt light_jacket light_padding jeans thermal padding fleece_pants beanie gloves winter_boots knit long_padding neck_warmer"),
    "adult": (5, 5, "sleeveless short_sleeve shorts sandals linen_shirt cotton_pants sneakers long_sleeve cardigan sweatshirt knit light_jacket jeans trench_coat coat thermal padding fleece_pants scarf gloves winter_boots long_padding beanie"),
    "senior": (5, 4, "short_sleeve linen_shirt cotton_pants sandals sneakers long_sleeve cardigan knit vest light_jacket thermal coat fleece_pants scarf long_padding beanie gloves winter_boots"),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("age", choices=ITEMS)
    parser.add_argument("sheet", type=Path)
    parser.add_argument("--output", type=Path, default=Path("app/src/main/res/drawable-nodpi"))
    parser.add_argument("--size", type=int, default=512)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()
    if args.size <= 0:
        parser.error("--size must be positive")
    columns, rows, names = ITEMS[args.age]
    sheet = Image.open(args.sheet).convert("RGBA")
    args.output.mkdir(parents=True, exist_ok=True)
    for index, name in enumerate(names.split()):
        dest = args.output / f"cloth_{args.age}_{name}.png"
        if dest.exists() and not args.overwrite:
            print(f"skip {dest}")
            continue
        x, y = index % columns, index // columns
        bounds = (round(x * sheet.width / columns), round(y * sheet.height / rows),
                  round((x + 1) * sheet.width / columns), round((y + 1) * sheet.height / rows))
        cell = sheet.crop(bounds)
        # Preserve the complete cell and its alpha without stretching its contents.
        scale = min(args.size / cell.width, args.size / cell.height)
        cell = cell.resize((round(cell.width * scale), round(cell.height * scale)), Image.Resampling.LANCZOS)
        result = Image.new("RGBA", (args.size, args.size), (0, 0, 0, 0))
        result.alpha_composite(cell, ((args.size - cell.width) // 2, (args.size - cell.height) // 2))
        result.save(dest, optimize=True)
        print(f"saved {dest}")


if __name__ == "__main__":
    main()
