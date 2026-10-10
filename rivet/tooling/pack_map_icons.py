"""Pack checked-in, licensed 16x16 sprites. Pillow is only needed to edit artwork."""
from pathlib import Path
import json
from PIL import Image

root = Path(__file__).resolve().parents[2]
gui = root / "rivet/mod/src/main/resources/assets/rivet/textures/gui"
for directory, filename, expected in [("map-icons", "map_icons.png", 103), ("map-tools", "map_tools.png", 50)]:
    folder = gui / directory
    names = json.loads((folder / "index.json").read_text())
    if len(names) != expected or len(names) != len(set(names)):
        raise ValueError(f"Wrong sprite count or duplicate keys: {directory}")
    atlas = Image.new("RGBA", (128, ((len(names) + 7) // 8) * 16))
    for index, name in enumerate(names):
        sprite = Image.open(folder / (name + ".png")).convert("RGBA")
        if sprite.size != (16, 16):
            raise ValueError(f"Wrong sprite size: {name}")
        atlas.alpha_composite(sprite, ((index % 8) * 16, (index // 8) * 16))
    atlas.save(gui / filename)
