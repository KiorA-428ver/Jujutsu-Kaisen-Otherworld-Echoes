import json
from pathlib import Path

from bbmodel import Model

# Minecraft pixel-art authoring scale; runtime renderer scales this landmark to ~9 blocks tall.
m = Model("sukuna_shrine", tex_size=16)

# Compact 16x16 atlas regions used by the generated entity texture.
for name, x, y, color in [
    ("wood", 0, 0, (78, 22, 27)), ("roof", 4, 0, (24, 20, 24)),
    ("bone", 8, 0, (190, 164, 125)), ("horn", 12, 0, (216, 189, 145)),
    ("mouth", 0, 4, (72, 10, 28)), ("gold", 4, 4, (174, 120, 46)),
    ("dark", 8, 4, (18, 12, 17)), ("bone2", 12, 4, (128, 109, 82)),
]:
    m.region(name, x, y, x + 4, y + 4)
    m.fill(name, color)

# Bone-strewn stepped plinth.
for x, z, w, d, h in [
    (2, 5, 18, 8, 4), (44, 5, 18, 8, 4), (5, 47, 16, 10, 4),
    (43, 45, 17, 12, 4), (0, 19, 9, 20, 3), (55, 18, 9, 20, 3),
    (15, 1, 34, 8, 3), (15, 55, 34, 8, 3),
]:
    m.cube("base", (x, 0, z), (x + w, h, z + d), tex="bone2")
m.cube("base_top", (7, 3, 7), (57, 7, 57), tex="bone")
m.cube("base_core", (13, 6, 13), (51, 10, 51), tex="dark")

# Four skull/shoulder masses and rib-like side bones.
for x, z in [(5, 13), (48, 13), (5, 39), (48, 39)]:
    m.cube("skull", (x, 5, z), (x + 11, 14, z + 11), tex="bone")
    m.cube("socket", (x + 3, 9, z - 0.3), (x + 5, 12, z + 1), tex="dark")
    m.cube("socket", (x + 7, 9, z - 0.3), (x + 9, 12, z + 1), tex="dark")
for x in (10, 16, 22, 42, 48, 54):
    m.cube("rib", (x, 7, 5), (x + 3, 25, 8), tex="bone2")
    m.cube("rib", (x, 7, 56), (x + 3, 25, 59), tex="bone2")

# Tall dark-red shrine body, recessed front, and heavy pillars.
m.cube("rear", (13, 9, 18), (51, 41, 48), tex="dark")
for x in (10, 48):
    for z in (17, 41):
        m.cube("pillar", (x, 10, z), (x + 6, 42, z + 6), tex="wood")
m.cube("lintel", (13, 35, 16), (51, 43, 49), tex="wood")
m.cube("lintel_trim", (8, 41, 13), (56, 46, 52), tex="gold")

# Giant open mouth on the facade, with upper/lower teeth and tongue.
m.cube("mouth_back", (18, 15, 13), (46, 35, 18), tex="mouth")
m.cube("upper_jaw", (19, 14, 12), (45, 19, 17), tex="bone")
m.cube("lower_jaw", (20, 31, 12), (44, 36, 17), tex="bone2")
for x in (21, 26, 31, 36, 41):
    m.cube("upper_tooth", (x, 17, 10.8), (x + 3, 22, 14), tex="bone")
for x in (23, 28, 33, 38):
    m.cube("lower_tooth", (x, 29, 10.8), (x + 3, 34, 14), tex="bone")
m.cube("tongue", (26, 24, 10.5), (38, 30, 14), tex="mouth")

# Layered Japanese roof with broad eaves and gold edge trim.
m.cube("eave_lower", (3, 42, 8), (61, 47, 56), tex="roof")
m.cube("eave_lower_trim", (1, 46, 6), (63, 49, 58), tex="gold")
m.cube("roof_mid", (8, 47, 12), (56, 53, 52), tex="roof")
m.cube("roof_mid_trim", (6, 52, 10), (58, 55, 54), tex="gold")
m.cube("roof_upper", (14, 53, 18), (50, 59, 46), tex="roof")
m.cube("roof_upper_trim", (12, 58, 16), (52, 60, 48), tex="gold")
m.cube("roof_spine", (25, 59, 24), (39, 64, 40), tex="dark")

# Symmetric stepped horns/antlers and crown ornament.
for x, y, z, w, h, d in [
    (19, 59, 25, 7, 7, 6), (14, 64, 25, 6, 7, 6), (9, 69, 25, 5, 7, 6),
    (4, 74, 25, 5, 6, 6), (38, 59, 25, 7, 7, 6), (44, 64, 25, 6, 7, 6),
    (50, 69, 25, 5, 7, 6), (55, 74, 25, 5, 6, 6),
]:
    m.cube("horn", (x, y, z), (x + w, y + h, z + d), tex="horn")
m.cube("horn_tip_l", (0, 79, 26), (5, 83, 31), tex="horn")
m.cube("horn_tip_r", (59, 79, 26), (64, 83, 31), tex="horn")
m.cube("crown", (27, 64, 23), (37, 71, 41), tex="gold")
m.cube("crown_face", (29, 66, 20), (35, 72, 24), tex="bone")

# Keep Blockbench's embedded texture and the runtime texture byte-for-byte identical.
# The previous version had an unrelated legacy PNG in the runtime resource, so the
# model looked different in Blockbench and in-game.
model_path = Path("src/main/resources/assets/sukunamod/models/entity/shrine.bbmodel")
texture_path = Path("src/main/resources/assets/sukunamod/textures/entity/shrine.png")
m.save(str(model_path))
data = json.loads(model_path.read_text(encoding="utf-8"))
embedded = data["textures"][0]["source"].split(",", 1)[1]
texture_path.write_bytes(__import__("base64").b64decode(embedded))
# This model is static; an empty default animation only creates a validation
# warning and can confuse animation-aware importers.
data.pop("animations", None)
model_path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
print(f"synchronized {texture_path} with embedded model texture")
