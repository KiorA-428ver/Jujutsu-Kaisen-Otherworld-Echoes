#!/usr/bin/env python3
"""把 uploads_flame/火矢1~4.png 做成灶·开火焰箭的 4 帧循环动画贴图。

产物（均为 360x1440 竖排帧条，渲染器按游戏 tick 手动切帧，保证两张严格同步）：
  * textures/entity/flame_arrow_anim.png      —— 原图 4 帧（箭尖朝上）；
  * textures/entity/flame_arrow_anim_rot.png  —— 每帧顺时针 90° 翻转（箭尖朝右），
    与第一张交叉叠合成"更立体"的双面火矢。

循环时长 1.2 秒 = 24 tick，一帧 6 tick；帧序号 = (gameTime / 6) % 4。
另外输出 preview_cross.png：把同一帧的两张贴图各 50% 叠加，模拟游戏里的交叉观感。
"""

from PIL import Image

SRC = ["uploads_flame/火矢1.png", "uploads_flame/火矢2.png",
       "uploads_flame/火矢3.png", "uploads_flame/火矢4.png"]
OUT_DIR = "src/main/resources/assets/sukunamod/textures/entity"


def load_frames():
    frames = []
    for path in SRC:
        im = Image.open(path).convert("RGBA")
        if im.size != (360, 360):
            im = im.resize((360, 360), Image.LANCZOS)
        frames.append(im)
    return frames


def stack(frames, name):
    strip = Image.new("RGBA", (360, 360 * len(frames)), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        strip.paste(frame, (0, i * 360))
    strip.save(f"{OUT_DIR}/{name}")
    print("written", f"{OUT_DIR}/{name}", strip.size)


frames = load_frames()
stack(frames, "flame_arrow_anim.png")
# 顺时针 90°：箭尖从朝上变成朝右（横着的变成竖着的镜像用法由 UV 处理）。
stack([f.transpose(Image.ROTATE_270) for f in frames], "flame_arrow_anim_rot.png")

# 预览：第 1 帧原图 + 第 1 帧翻转图各半透明叠加，模拟交叉双面观感。
a = frames[0]
b = frames[0].transpose(Image.ROTATE_270)
preview = a.copy()
preview.paste(Image.alpha_composite(a, b), (0, 0))
preview = Image.alpha_composite(Image.new("RGBA", a.size, (24, 24, 28, 255)), preview)
preview.save("flame_arrow_cross_preview.png")
print("written flame_arrow_cross_preview.png", preview.size)
