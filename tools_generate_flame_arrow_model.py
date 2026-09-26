"""生成灶·开火焰矢的 Blockbench 模型与 32x32 像素贴图。"""
from pathlib import Path
import shutil

from bbmodel import Model
from pngio import write_png

ROOT = Path(__file__).resolve().parent
MODEL_PATH = ROOT / "src/main/resources/assets/sukunamod/models/entity/flame_arrow.bbmodel"
MODEL_TEXTURE = MODEL_PATH.with_name("flame_arrow.png")
ITEM_TEXTURE = ROOT / "src/main/resources/assets/sukunamod/textures/item/flame_arrow.png"
ENTITY_TEXTURE = ROOT / "src/main/resources/assets/sukunamod/textures/entity/flame_arrow.png"

# 贴图密度按 1 单位=1 像素；暖色火焰用多档明暗表现炽热核心和深色余烬。
m = Model("flame_arrow", tex_size=32)
m.region("core", 0, 0, 8, 8)
m.region("hot", 8, 0, 16, 8)
m.region("flame", 16, 0, 24, 8)
m.region("ember", 24, 0, 32, 8)

CORE = (255, 218, 92)
CORE_LIGHT = (255, 248, 190)
ORANGE = (255, 112, 18)
ORANGE_LIGHT = (255, 168, 32)
RED = (190, 38, 12)
EMBER = (100, 20, 8)

m.fill("core", CORE)
m.paint("core", lambda u, v, w, h:
        CORE_LIGHT if (2 <= u <= 5 and 1 <= v <= 6) else
        (255, 190, 48) if (u in (0, 7) or v in (0, 7)) else None)
m.fill("hot", ORANGE_LIGHT)
m.paint("hot", lambda u, v, w, h:
        CORE_LIGHT if 3 <= u <= 4 and 2 <= v <= 5 else
        ORANGE if u in (0, 7) or v in (0, 7) else None)
m.fill("flame", ORANGE)
m.paint("flame", lambda u, v, w, h:
        ORANGE_LIGHT if (2 <= u <= 5 and 1 <= v <= 3) else
        RED if u in (0, 1, 6, 7) or v >= 6 else None)
m.fill("ember", RED)
m.paint("ember", lambda u, v, w, h:
        ORANGE if 2 <= u <= 5 and 1 <= v <= 4 else
        EMBER if u in (0, 7) or v in (0, 7) else None)

# 箭头沿局部 +X。右端是由宽到窄的炽热箭尖，左端是有层次的收尖火舌，
# 让整体更像凝聚成形的火元素，而不是散开的方盒子。
m.cube("ember_shaft", (-8, -1.0, -1.0), (7, 1.0, 1.0), tex="hot")
m.cube("arrow_core", (5, -2.5, -2.5), (9, 2.5, 2.5), tex={"*": "hot", "east": "core"})
m.cube("arrow_mid_tip", (9, -1.8, -1.8), (12, 1.8, 1.8), tex="core")
m.cube("arrow_tip", (12, -1.0, -1.0), (15, 1.0, 1.0), tex="core")
m.cube("flame_core", (-15, -3.0, -3.0), (-7, 3.0, 3.0), tex={"*": "flame", "east": "hot"})
m.cube("flame_hot", (-16, -2.0, -2.0), (-10, 2.0, 2.0), tex="hot", inflate=0.02)
m.cube("flame_upper", (-15, 2.0, -1.5), (-10, 4.5, 1.5), tex="flame")
m.cube("flame_lower", (-15, -4.5, -1.5), (-10, -2.0, 1.5), tex="flame")
m.cube("flame_side", (-15, -1.5, 3.0), (-10, 1.5, 4.5), tex="ember")
m.cube("tail_spark", (-20, -1.0, -1.0), (-15, 1.0, 1.0), tex="ember")

MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
m.save(str(MODEL_PATH))
# 实体渲染使用与 bbmodel 完全一致的 32x32 贴图；物品图单独做成更清晰的透明图标。
shutil.copyfile(MODEL_TEXTURE, ENTITY_TEXTURE)

# 物品使用透明底像素图，箭尖朝右；模型内嵌贴图仍保留给 Blockbench 预览。
transparent = (0, 0, 0, 0)
item = [[transparent for _ in range(32)] for _ in range(32)]
def rect(x0, y0, x1, y1, color):
    for y in range(max(0, y0), min(32, y1)):
        for x in range(max(0, x0), min(32, x1)):
            item[y][x] = color

def pixel(x, y, color):
    if 0 <= x < 32 and 0 <= y < 32:
        item[y][x] = color

# 细长火焰矢：外焰深红，内焰橙色，中心白黄高光。
rect(7, 14, 24, 18, (190, 38, 12, 255))
rect(10, 12, 22, 20, (255, 112, 18, 255))
rect(13, 13, 22, 19, (255, 168, 32, 255))
rect(16, 14, 23, 18, (255, 248, 190, 255))
rect(22, 15, 29, 17, (255, 218, 92, 255))
rect(28, 14, 31, 18, (255, 168, 32, 255))
rect(4, 15, 10, 17, (255, 112, 18, 255))
rect(1, 14, 5, 18, (100, 20, 8, 255))
rect(4, 11, 9, 14, (190, 38, 12, 255))
rect(6, 18, 11, 21, (190, 38, 12, 255))
rect(9, 9, 13, 13, (255, 112, 18, 255))
rect(9, 19, 13, 23, (255, 112, 18, 255))
rect(12, 10, 16, 13, (255, 168, 32, 255))
rect(12, 19, 16, 22, (255, 168, 32, 255))
for x, y in ((2, 13), (3, 12), (5, 10), (7, 22), (8, 23), (11, 8), (11, 24)):
    pixel(x, y, (190, 38, 12, 255))
write_png(str(ITEM_TEXTURE), item)
lo, hi = m.bounds()
print(f"saved {MODEL_PATH}  包围盒 {tuple(lo)} -> {tuple(hi)}")
print(f"wrote transparent item texture {ITEM_TEXTURE}")
