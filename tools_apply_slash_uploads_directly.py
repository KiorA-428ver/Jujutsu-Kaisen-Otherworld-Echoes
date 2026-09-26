#!/usr/bin/env python3
"""按用户要求"直接把贴图动画拍上去"：不再摆平重建条带，直接使用
uploads_slash/slash_N.png 的原画（360x360，刀身沿左上→右下对角线）
生成 slash_frame_N.png 四帧。

四帧一律逐像素原样转 RGBA 拷贝，笔触（白刀身、细线、飞白虚线）
忠实保留，不做任何重建/实心化。slash_2 是带 tRNS 的调色板图，
PIL 的 convert("RGBA") 会把 tRNS 正确展开成 alpha（背景透明、
白描边实心、刀身黑芯透明），不需要额外抠底。"""
import warnings
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent
SRC_DIR = ROOT / "uploads_slash"
DST_DIR = ROOT / "src/main/resources/assets/sukunamod/textures/entity"


def main() -> None:
    DST_DIR.mkdir(parents=True, exist_ok=True)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")  # P 图 tRNS 的转换提示，行为本身正确
        for n in range(1, 5):
            src = SRC_DIR / f"slash_{n}.png"
            out = Image.open(src).convert("RGBA")
            dst = DST_DIR / f"slash_frame_{n}.png"
            out.save(dst)
            print(f"{src.name} -> {dst.relative_to(ROOT)}  {out.size[0]}x{out.size[1]}")


if __name__ == "__main__":
    main()
