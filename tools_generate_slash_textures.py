# -*- coding: utf-8 -*-
"""生成三张细长斩击贴图（256x16，透明底），对应渲染时 10 格长、约 0.3 格宽。

- slash_frame_1.png：连续实心白色斩线（参考 image-14：一条横贯的白线，两端收尖，
  左端碎成几段短虚线）。
- slash_frame_2.png：一串“白边内黑”的虚线段（参考 image-12）。
- slash_frame_3.png：虚线更稀疏、更细、渐隐的残影（参考 image-13）。
"""
import math
import random
import struct
import zlib
from pathlib import Path

W, H = 256, 16
BASE_Y = 7.5
OUT = Path(__file__).resolve().parent / "src/main/resources/assets/sukunamod/textures/entity"


def write_png(path, pixels):
    raw = bytearray()
    for y in range(H):
        raw.append(0)
        for x in range(W):
            r, g, b, a = pixels[y][x]
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    path.write_bytes(png)


def blank():
    return [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]


def put(px, x, y, color):
    """写入像素，与已有像素按最大 alpha 叠加。"""
    if 0 <= x < W and 0 <= y < H:
        r, g, b, a = color
        cr, cg, cb, ca = px[y][x]
        if a >= ca:
            px[y][x] = (r, g, b, a)


def center_y(x):
    """斩线的轻微起伏：整条线不是笔直的，带一点手抖的波动。"""
    return BASE_Y + 1.5 * math.sin(x * 0.055 + 0.4) + 0.55 * math.sin(x * 0.17 + 1.1)


def envelope(x, rng):
    """整条斩击的粗细包络：两端收尖，中段最粗（约 3.6px）。"""
    t = x / (W - 1.0)
    if t < 0.10:
        base = t / 0.10
    elif t > 0.90:
        base = (1.0 - t) / 0.10
    else:
        base = 1.0
    base = max(0.0, min(1.0, base)) ** 0.6
    return 3.6 * base + rng.uniform(-0.35, 0.35)


def frame1():
    rng = random.Random(11)
    px = blank()
    white = (255, 255, 255, 255)
    soft = (255, 255, 255, 165)
    for x in range(W):
        # 左端 1/8 段碎成短虚线，和参考图一致。
        if x < 30 and (x % 7) >= 4:
            continue
        yc = center_y(x)
        half = envelope(x, rng)
        if half <= 0.2:
            continue
        y0 = int(math.floor(yc - half))
        y1 = int(math.ceil(yc + half))
        for y in range(y0, y1 + 1):
            d = abs(y + 0.5 - yc)
            if d <= half:
                put(px, x, y, white)
            elif d <= half + 0.9 and ((x + y) % 2) == 0:
                put(px, x, y, soft)  # 边缘抖动，做出像素化的毛糙感
    # 右端尾部的几颗火星
    for _ in range(14):
        x = rng.randint(196, 254)
        y = int(center_x_safe(x) + rng.choice([-1, 1]) * rng.uniform(2.2, 4.5))
        put(px, x, y, (255, 255, 255, rng.randint(150, 235)))
    return px


def center_x_safe(x):
    return center_y(x)


def dashes(px, rng, min_len, max_len, min_gap, max_gap, max_half, alpha,
           outline_black_core, density):
    """沿斩线画一串透镜状虚线段。"""
    white = (255, 255, 255, alpha)
    black = (10, 10, 14, alpha)
    x = rng.randint(2, 8)
    while x < W - 2:
        if rng.random() > density:
            x += rng.randint(min_gap, max_gap)
            continue
        length = rng.randint(min_len, max_len)
        for i in range(length):
            xx = x + i
            if xx >= W:
                break
            t = i / max(1, length - 1)
            # 每段自身两端收尖的透镜轮廓，再乘整条斩线的首尾包络
            lens = math.sin(math.pi * t) ** 0.55
            env = envelope(xx, rng) / 3.6
            half = max_half * lens * max(0.15, env)
            if half < 0.6:
                continue
            yc = center_y(xx)
            y0 = int(math.floor(yc - half - 1))
            y1 = int(math.ceil(yc + half + 1))
            for y in range(y0, y1 + 1):
                d = abs(y + 0.5 - yc)
                if d > half + 0.55:
                    continue
                if outline_black_core:
                    if d > half - 1.35:
                        put(px, xx, y, white)      # 白边（约 1~2px）
                    elif d <= half - 1.35:
                        put(px, xx, y, black)      # 内芯为黑
                else:
                    put(px, xx, y, white)
        x += length + rng.randint(min_gap, max_gap)


def frame2():
    rng = random.Random(23)
    px = blank()
    # 白边内黑的虚线段：段较长、间隙适中（对应参考图第二帧）
    dashes(px, rng, 10, 30, 6, 16, 3.4, 255, True, 0.92)
    return px


def frame3():
    rng = random.Random(37)
    px = blank()
    # 更稀疏、更细的残影虚线
    dashes(px, rng, 2, 9, 10, 24, 1.5, 235, False, 0.5)
    # 散落的单像素碎屑
    white = (255, 255, 255, 255)
    for _ in range(70):
        x = rng.randint(0, W - 1)
        y = int(center_y(x) + rng.choice([-1, 1]) * rng.uniform(0.5, 4.2))
        a = rng.randint(90, 200)
        put(px, x, y, (white[0], white[1], white[2], a))
    return px


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    write_png(OUT / "slash_frame_1.png", frame1())
    write_png(OUT / "slash_frame_2.png", frame2())
    write_png(OUT / "slash_frame_3.png", frame3())
    print("generated:", sorted(p.name for p in OUT.glob("slash_frame_*.png")))


if __name__ == "__main__":
    main()
