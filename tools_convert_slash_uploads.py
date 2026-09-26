#!/usr/bin/env python3
"""把用户提供的斜向斩击原画（uploads_slash/slash_N.png，360x360，
左上到右下的对角斩线）烘焙成渲染器使用的水平细长条贴图
slash_frame_N.png：沿斩线主轴旋转摆平、按垂直范围精确裁切，双线性重采样。

实心化重建（本轮修复"一边开叉、还带透明"）：
原画 slash_3 的尾端是"黑芯提前收笔、两侧白描边继续发散"的飞白，
slash_4 的尾端整段是干笔虚线（实测 11 个全空列段）。上一版忠实保留笔触，
游戏里最后两帧就呈现为"线条一端开叉、中间透出背景"。现在不再保留笔触，
而是从黑芯列提取中心线+半宽包络，做三件事：
1. 虚线缺口：干笔虚线段之间是全空列，用缺口左右相邻段各自最宽处
   （段头/段尾 8 列窗口内最大半宽及其中心）线性插值跨缺口接出实心，
   再做 ±3 列取宽滤波抹平 blob 端部的收尖缺口，线不再断；
2. 尾部开叉：黑芯结束之后的白描边列直接丢弃，用末段包络外推并
   在最后 TIP_TAPER 列内把半宽线性收到 0，重新收出实心尖；
3. 半透明：重建后所有落笔像素 alpha 一律 255（外圈 1px 白描边、
   内芯黑色；帧 1 原画是纯白笔画，整条白色实心），不再有任何软边。
包络按列中值滤波去抖，再整体缩放让线占满条带高度（保证 0.3/0.15 格宽
规格不被稀释）。尖端消失问题沿用上版方案：条带居中贴 2 的幂宽画布。"""
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent
SRC_DIR = ROOT / "uploads_slash"
DST_DIR = ROOT / "src/main/resources/assets/sukunamod/textures/entity"

ALPHA_THRESHOLD = 16      # 低于此 alpha 视为背景
CORE_ALPHA = 200          # 黑芯判定：alpha 下限
CORE_LUM = 100            # 黑芯判定：亮度上限（0..255）
TIP_TAPER = 10            # 两端各自重新收尖所用的列数


def opaque_points(im: Image.Image):
    px = im.load()
    w, h = im.size
    return [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > ALPHA_THRESHOLD]


def principal_axis(pts):
    """返回主轴单位向量 (cos, sin)（指向 +x）与质心。"""
    n = len(pts)
    cx = sum(p[0] for p in pts) / n
    cy = sum(p[1] for p in pts) / n
    sxx = sum((p[0] - cx) ** 2 for p in pts) / n
    syy = sum((p[1] - cy) ** 2 for p in pts) / n
    sxy = sum((p[0] - cx) * (p[1] - cy) for p in pts) / n
    t = math.atan2(2 * sxy, sxx - syy) / 2.0
    dx, dy = math.cos(t), math.sin(t)
    if dx < 0:
        dx, dy = -dx, -dy
    return dx, dy, cx, cy


def med3(vals):
    v = sorted(vals)
    return v[len(v) // 2]


def reconstruct(strip: Image.Image) -> Image.Image:
    """从摆平条带里提取黑芯包络，补虚线、去开叉、全不透明地重画一条实心斩线。"""
    w, h = strip.size
    px = strip.load()

    stroke_cols = [x for x in range(w) if any(px[x, y][3] > ALPHA_THRESHOLD for y in range(h))]
    if not stroke_cols:
        raise SystemExit("条带里没有笔触")
    x0, x1 = stroke_cols[0], stroke_cols[-1]

    # 每列黑芯包络（top/bot 行号）；白色笔画（帧 1）没有黑芯，退化为描边包络
    tops = [None] * w
    bots = [None] * w
    for x in range(x0, x1 + 1):
        dark = [y for y in range(h)
                if px[x, y][3] >= CORE_ALPHA and sum(px[x, y][:3]) / 3.0 < CORE_LUM]
        if dark:
            tops[x], bots[x] = min(dark), max(dark)
    dark_cols = [x for x in range(x0, x1 + 1) if tops[x] is not None]
    white_stroke = len(dark_cols) * 2 < (x1 - x0 + 1)
    if white_stroke:
        tops = bots = None
        tops = [None] * w
        bots = [None] * w
        for x in range(x0, x1 + 1):
            lite = [y for y in range(h) if px[x, y][3] > ALPHA_THRESHOLD]
            tops[x], bots[x] = min(lite), max(lite)
        dark_cols = list(range(x0, x1 + 1))

    # 虚线缺口/飞白列：干笔的虚线段之间是"全空列"，而虚线 blob 两端是收尖的，
    # 直接在尖与尖之间插值会把缺口画成细白线。改为：取缺口左右相邻段各自
    # 最宽处（段尾/段头 8 列窗口内的最大半宽及其中心）做插值，跨缺口接出实心。
    centers = [0.0] * w
    halves = [0.0] * w
    defined = set(dark_cols)
    for x in dark_cols:
        centers[x], halves[x] = (tops[x] + bots[x]) / 2.0, (bots[x] - tops[x]) / 2.0
    x = x0
    while x <= x1:
        if x in defined:
            x += 1
            continue
        g0 = x
        while x <= x1 and x not in defined:
            x += 1
        g1 = x - 1
        a = g0 - 1
        b = g1 + 1
        la = [z for z in range(max(x0, a - 7), a + 1) if z in defined]
        rb = [z for z in range(b, min(x1, b + 7) + 1) if z in defined]
        if la and rb:
            za = max(la, key=lambda z: halves[z])
            zb = max(rb, key=lambda z: halves[z])
            for gx in range(g0, g1 + 1):
                t = (gx - a) / float(b - a)
                halves[gx] = halves[za] * (1 - t) + halves[zb] * t
                centers[gx] = centers[za] * (1 - t) + centers[zb] * t
        elif la:
            za = max(la, key=lambda z: halves[z])
            for gx in range(g0, g1 + 1):
                halves[gx], centers[gx] = halves[za], centers[za]
        elif rb:
            zb = max(rb, key=lambda z: halves[z])
            for gx in range(g0, g1 + 1):
                halves[gx], centers[gx] = halves[zb], centers[zb]

    # 取宽滤波抹平 blob 两端的收尖缺口，再做中值滤波去手抖
    for _ in range(2):
        centers = [med3([centers[max(x0, x - 1)], centers[x], centers[min(x1, x + 1)]])
                   for x in range(w)]
        halves = [max(halves[max(x0, x - 3):min(x1, x + 3) + 1]) for x in range(w)]

    # 整体缩放：让半宽包络的最大值占满条带半高（描边行含在包络内），
    # 保证 0.3/0.15 格宽规格足额兑现
    max_half = max(halves[x0:x1 + 1])
    target_half = (h - 1) / 2.0
    scale = target_half / max_half if max_half > 0 else 1.0
    halves = [min(halves[x] * scale, (h - 1) / 2.0) for x in range(w)]

    # 两端重新收尖：最后 TIP_TAPER 列内半宽线性收到 0，收出实心尖
    for i in range(TIP_TAPER):
        f = i / float(TIP_TAPER)
        halves[x0 + i] *= f
        halves[x1 - i] *= f

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    op = out.load()
    for x in range(x0, x1 + 1):
        c, hh = centers[x], halves[x]
        top = max(0, int(math.floor(c - hh)))
        bot = min(h - 1, int(math.ceil(c + hh)))
        for y in range(top, bot + 1):
            if white_stroke or (bot - y) < 1 or (y - top) < 1:
                op[x, y] = (255, 255, 255, 255)   # 描边 / 尖端：白
            else:
                op[x, y] = (0, 0, 0, 255)         # 黑芯
    return out


def next_pow2(n):
    return 1 << max(0, math.ceil(math.log2(max(1, n))))


def center_on_pow2(out: Image.Image):
    """把条带水平居中贴到宽度为 2 的幂的透明画布上，两端留对称边。
    MipmapGenerator 逐级 >>1 只在奇数尺寸时丢最右列/最底行；
    POT 宽度 + 对称边距让头尾两端在每一级 mip 的衰减完全一致。
    高度保持内容原样占满：quad 的半高即用户要求的斩线半宽
    （帧 1、2 共 0.3 格，帧 3、4 共 0.15 格），不能被画布边距稀释。"""
    w, h = out.size
    cw = next_pow2(w)
    canvas = Image.new("RGBA", (cw, h), (0, 0, 0, 0))
    canvas.paste(out, ((cw - w) // 2, 0), out)
    return canvas


def bake(src: Path, dst: Path):
    im = Image.open(src).convert("RGBA")
    pts = opaque_points(im)
    if not pts:
        raise SystemExit(f"{src}: 找不到不透明像素")
    dx, dy, _, _ = principal_axis(pts)
    ts = [p[0] * dx + p[1] * dy for p in pts]
    us = [-p[0] * dy + p[1] * dx for p in pts]
    tmin, tmax = min(ts), max(ts)
    umin, umax = min(us), max(us)
    umid = (umin + umax) / 2.0
    out_w = int(math.ceil(tmax - tmin)) + 1
    out_h = max(1, int(math.ceil(umax - umin)))  # 垂直方向不留边：斩线正好占满条带高度
    a, b = dx, -dy
    c = tmin * dx + (out_h / 2.0 - umid) * dy
    d, e = dy, dx
    f = tmin * dy - (out_h / 2.0 - umid) * dx
    out = im.transform((out_w, out_h), Image.AFFINE, (a, b, c, d, e, f),
                       resample=Image.BILINEAR)
    out = reconstruct(out)
    out = center_on_pow2(out)
    out.save(dst)
    print(f"{dst.name}: {src.name} -> {out.size[0]}x{out.size[1]} (line {tmax - tmin:.0f}px "
          f"long, {umax - umin:.1f}px thick, angle "
          f"{math.degrees(math.atan2(dy, dx)):.2f} deg)")


def main():
    DST_DIR.mkdir(parents=True, exist_ok=True)
    for i in (1, 2, 3, 4):
        bake(SRC_DIR / f"slash_{i}.png", DST_DIR / f"slash_frame_{i}.png")


if __name__ == "__main__":
    main()
