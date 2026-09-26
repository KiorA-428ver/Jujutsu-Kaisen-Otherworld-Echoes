#!/usr/bin/env python3
"""用用户上传的 uploads_flame/闪光.png 替换正在应用的旧红色十字闪光贴图。

产物（粒子图集引用名不变，直接覆盖旧文件即完成替换）：
  * textures/particle/flash_cross_1.png —— 新闪光原样（缩到 360x360，保留透明底）；
  * textures/particle/flash_cross_2.png —— 新闪光整体旋转 90°（竖臂与横臂互换），
    供引信里两张贴图交替闪烁时保留节奏变化。

灶·开的闪光不允许出现 45° 斜十字：第二发只按 90° 整数倍转，
两条臂永远与屏幕的水平/竖直方向对齐，尺寸也不再因 expand 缩放而缩水。
flash_cross_5.json 复用 flash_cross_2，一并换成新闪光。
"""

from PIL import Image

SRC = "uploads_flame/闪光.png"
OUT_DIR = "src/main/resources/assets/sukunamod/textures/particle"
SIZE = 360

star = Image.open(SRC).convert("RGBA")

variant1 = star.resize((SIZE, SIZE), Image.LANCZOS)
variant1.save(f"{OUT_DIR}/flash_cross_1.png")
print("written", f"{OUT_DIR}/flash_cross_1.png", variant1.size)

# 90° 整数倍旋转（不 expand，尺寸原样保留）：横竖臂互换，但两臂仍是正十字，不出现 45° 斜向。
# 交替闪烁的节奏差别由两发各自的最大尺寸（Provider1/Provider2）体现。
rotated = star.transpose(Image.ROTATE_90)
variant2 = rotated.resize((SIZE, SIZE), Image.LANCZOS)
variant2.save(f"{OUT_DIR}/flash_cross_2.png")
print("written", f"{OUT_DIR}/flash_cross_2.png", variant2.size)
