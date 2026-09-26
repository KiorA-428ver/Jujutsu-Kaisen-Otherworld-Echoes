#!/usr/bin/env python3
"""把用户上传的 打击1~4 / 打击命中1~3（调色板黑底图）转成透明底粒子帧贴图。

内容都是白(灰)色线稿压在纯黑底上，直接当粒子贴会糊出一块黑方框。
转换规则：RGB 统一提纯为白，alpha = 原亮度——黑底变全透明、白线保持实心、
灰色抗锯齿边缘变成半透明白，粒子用 PARTICLE_SHEET_LIT 渲染即为发光白环。

产物（64x64 RGBA）：
  textures/particle/strike_frame_1..4.png   —— 打击帧动画（连打/追击/重击用）
  textures/particle/strike_hit_frame_1..3.png —— 打击命中帧动画（目标身上用）
另外拼一张 previews 供 read_image 自查。
"""

import os
from PIL import Image

SRC_DIR = "uploads_strike"
OUT_DIR = "src/main/resources/assets/sukunamod/textures/particle"

STRIKE = ["打击1.png", "打击2.png", "打击3.png", "打击4.png"]
HIT = ["打击命中1.png", "打击命中2.png", "打击命中3.png"]

os.makedirs(OUT_DIR, exist_ok=True)


def convert(path):
    img = Image.open(path).convert("RGBA")
    px = img.load()
    w, h = img.size
    out = Image.new("RGBA", (w, h))
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            lum = max(r, g, b)  # 灰度线稿：亮度即不透明度
            op[x, y] = (255, 255, 255, lum)
    return out


strike_frames = [convert(os.path.join(SRC_DIR, n)) for n in STRIKE]
hit_frames = [convert(os.path.join(SRC_DIR, n)) for n in HIT]

for i, f in enumerate(strike_frames):
    f.save(f"{OUT_DIR}/strike_frame_{i + 1}.png")
    print("written", f"{OUT_DIR}/strike_frame_{i + 1}.png", f.size)
for i, f in enumerate(hit_frames):
    f.save(f"{OUT_DIR}/strike_hit_frame_{i + 1}.png")
    print("written", f"{OUT_DIR}/strike_hit_frame_{i + 1}.png", f.size)

# 预览：帧贴在深灰底 + 棋盘底各一排，确认黑底已透、白环保留。
def sheet(frames, name):
    w, h = frames[0].size
    canvas = Image.new("RGBA", (w * len(frames), h * 2), (40, 40, 46, 255))
    for i, f in enumerate(frames):
        canvas.paste(f, (i * w, 0), f)
    check = Image.new("RGBA", (w * len(frames), h))
    cp = check.load()
    for y in range(h):
        for x in range(w * len(frames)):
            v = 200 if ((x // 8) + (y // 8)) % 2 == 0 else 60
            cp[x, y] = (v, v, v, 255)
    for i, f in enumerate(frames):
        check.paste(f, (i * w, h), f)
    canvas.alpha_composite(check, (0, h))
    canvas = canvas.resize((w * len(frames) * 2, h * 4), Image.NEAREST)
    canvas.save(name)
    print("preview", name)


sheet(strike_frames, "strike_frames_preview.png")
sheet(hit_frames, "strike_hit_frames_preview.png")
