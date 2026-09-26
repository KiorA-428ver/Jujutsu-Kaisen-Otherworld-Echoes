#!/usr/bin/env python3
"""由 dismantle.png 生成 4 帧斩击动画图集 dismantle_anim.png（512x128）。

帧内容：墨刀从中心向两端生长（40% -> 75% -> 100%）后整条消散（100% 低透明度）。
每帧先把 32x32 原画按轴向掩膜裁切，再 4 倍 LANCZOS 放大到 128x128，
保证放大渲染后边缘是平滑墨迹而不是锯齿台阶。
"""
from PIL import Image

SRC = "src/main/resources/assets/sukunamod/textures/entity/dismantle.png"
DST = "src/main/resources/assets/sukunamod/textures/entity/dismantle_anim.png"
UP = 4  # 每帧 128x128

src = Image.open(SRC).convert("RGBA")
W, H = src.size
px = src.load()

# 墨带沿 x-y 轴铺开：实测 x-y ∈ [-29, 28]，归一化 a = (x - y + 29) / 57
A_MIN, A_SPAN = -29.0, 57.0
FEATHER = 0.10  # 生长端 10% 轴向范围内渐隐


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def make_frame(extent, alpha_scale):
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    op = out.load()
    half = extent / 2.0
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            ax = (x - y - A_MIN) / A_SPAN  # 0..1 沿墨带
            d = abs(ax - 0.5)             # 距中心
            if d >= half:
                continue
            edge = smooth((half - d) / FEATHER) if d > half - FEATHER else 1.0
            op[x, y] = (r, g, b, int(a * edge * alpha_scale))
    return out.resize((W * UP, H * UP), Image.LANCZOS)


frames = [
    make_frame(0.40, 1.0),
    make_frame(0.75, 1.0),
    make_frame(1.00, 1.0),
    make_frame(1.00, 0.42),
]
sheet = Image.new("RGBA", (W * UP * len(frames), H * UP), (0, 0, 0, 0))
for i, f in enumerate(frames):
    sheet.paste(f, (i * W * UP, 0))
sheet.save(DST)
print("saved", DST, sheet.size)

# ASCII 预览（缩到 32 列宽）确认生长效果
for i, f in enumerate(frames):
    small = f.resize((32, 32), Image.LANCZOS)
    sp = small.load()
    print(f"--- frame {i + 1} ---")
    for y in range(32):
        print("".join("#" if sp[x, y][3] > 140 else ("o" if sp[x, y][3] > 40 else ".")
                      for x in range(32)))
