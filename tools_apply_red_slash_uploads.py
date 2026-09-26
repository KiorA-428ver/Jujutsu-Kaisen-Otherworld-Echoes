"""把用户这轮上传的红边/红色斩击三帧原画转成 RGBA 贴图，放进 entity 贴图目录。

红边斩击1/2/3 → red_edge_frame_1/2/3.png（红边斩击：暗红刀身 + 亮红描边）
红色斩击1/2/3 → red_frame_1/2/3.png（纯亮红斩击）
原画刀身沿画布左上→右下对角线贯穿，与 slash_frame_*.png 布局一致，
渲染端整幅直贴，不做任何重绘或缩放。
"""
from PIL import Image
import os

SRC = "/workspace/uploads"
DST = "src/main/resources/assets/sukunamod/textures/entity"

MAPPING = {
    "宿傩红边斩击1.png": "red_edge_frame_1.png",
    "宿傩红边斩击2.png": "red_edge_frame_2.png",
    "宿傩红边斩击3.png": "red_edge_frame_3.png",
    "宿傩红色斩击1.png": "red_frame_1.png",
    "宿傩红色斩击2.png": "red_frame_2.png",
    "宿傩红色斩击3.png": "red_frame_3.png",
}

os.makedirs(DST, exist_ok=True)
for src, dst in MAPPING.items():
    image = Image.open(os.path.join(SRC, src)).convert("RGBA")
    image.save(os.path.join(DST, dst))
    print(dst, image.size, image.mode)
