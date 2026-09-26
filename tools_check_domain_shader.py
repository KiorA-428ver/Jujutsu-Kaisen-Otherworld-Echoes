"""复刻 sukuna_domain.fsh / sukuna_stream.fsh 的取样公式，在合成高对比图上算一遍，验证色差真的可见。

纯标准库 + PIL。验证三件事：
1) 屏幕中心依旧干净（无重影）；
2) 四周的重影宽度在像素尺度上四向一致（旧版按 UV 算，16:9 下上下几乎没有）；
3) 暗角与暗红偏色仍然生效（stream 版是"柔和暗化 + 轻微色差"，一档一档对着数）。
"""
import math
from PIL import Image, ImageDraw

W, H = 480, 270          # 16:9 预览；公式里只有比值有意义，缩放不影响结论
BREATH = 1.0             # 取呼吸中位，方便核对静态数值


def make_scene():
    """造一张高对比测试图：暗红天 + 灰地 + 白色硬边栅格 + 中心白条白圆。"""
    img = Image.new("RGB", (W, H), (38, 10, 16))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, H // 2], fill=(52, 12, 20))
    d.rectangle([0, H // 2, W, H], fill=(96, 96, 100))
    for x in range(0, W, 24):
        d.line([x, H // 2, x, H], fill=(250, 250, 250), width=2)
    for y in range(H // 2, H, 24):
        d.line([0, y, W, y], fill=(250, 250, 250), width=2)
    d.rectangle([W // 2 - 3, 40, W // 2 + 3, H - 40], fill=(255, 255, 255))
    d.ellipse([W // 2 - 40, H // 2 - 40, W // 2 + 40, H // 2 + 40], outline=(255, 255, 255), width=3)
    for x in (60, 150, 330, 410):
        d.rectangle([x, 60, x + 26, 86], fill=(8, 8, 8))
    return img


def sample(px, x, y):
    """UV 取样，越界按 GLSL 默认的 CLAMP_TO_EDGE 处理。"""
    u = min(max(int(round(x)), 0), W - 1)
    v = min(max(int(round(y)), 0), H - 1)
    return px[u + v * W]


def shift_uv(cx, cy, size_x, size_y, breath, ghost=(0.5, 3.2, 1.6)):
    """照抄 .fsh：先在像素空间定重影宽度，再除回各轴像素数变回 UV 偏移。"""
    px = (cx - 0.5) * size_x
    py = (cy - 0.5) * size_y
    r = math.hypot(px, py)
    half_h = max(0.5 * size_y, 1.0)
    t = min(max(r / half_h, 0.0), 1.35)
    g = max(r, 1.0)   # 与 .fsh 一致：除零兜底
    dx, dy = px / g, py / g
    a, b, c = ghost
    ghost_px = (a + b * t + c * t * t) * breath
    return dx * ghost_px / size_x, dy * ghost_px / size_y


def apply_shader(src, size_x, size_y, spec):
    px = list(src.getdata())
    out = []
    for y in range(H):
        for x in range(W):
            cx, cy = (x + 0.5) / W, (y + 0.5) / H
            radius = math.hypot(cx - 0.5, cy - 0.5)
            sx, sy = shift_uv(cx, cy, size_x, size_y, BREATH, spec["ghost"])
            r = float(sample(px, x + sx * W, y + sy * H)[0])
            g = float(sample(px, x, y)[1])
            b = float(sample(px, x - sx * W, y - sy * H)[2])
            tr, tg, tb = spec["tint"]
            r *= tr
            g *= tg
            b *= tb
            # 柔和暗化：1 - smoothstep(e0, e1, radius * 1.35)
            e0, e1 = spec["vig"]
            v = min(max((radius * 1.35 - e1) / (e0 - e1), 0.0), 1.0)
            vignette = v * v * (3 - 2 * v)
            lo = spec["dim"]
            k = lo + (1 - lo) * vignette
            out.append((min(int(r * k), 255), min(int(g * k), 255), min(int(b * k), 255)))
    dst = Image.new("RGB", (W, H))
    dst.putdata(out)
    return dst


# 与两份 .fsh 逐项对应：ghost = a + b*t + c*t^2（像素/单通道），
# tint 为 mix 合成后的每通道系数，vig/dim 为暗角的 smoothstep 区间与最暗亮度。
SHADERS = {
    "domain (领域)": {
        "ghost": (0.5, 3.2, 1.6), "tint": (1.055, 0.912, 0.945),
        "vig": (0.35, 0.95), "dim": 0.45, "preview": "/tmp/domain_shader_preview.png",
    },
    "stream (连续解)": {
        "ghost": (0.35, 1.1, 0.55), "tint": (1.021, 0.979, 0.9895),
        "vig": (0.5, 1.1), "dim": 0.72, "preview": "/tmp/stream_shader_preview.png",
    },
}


def ghost_report(name, spec):
    print("%s —— 1080p 下红蓝总分离宽度 / 屏幕亮度（呼吸中位）：" % name)
    for point, (cx, cy) in {"屏幕中心": (0.5, 0.5), "正上边缘": (0.5, 0.0),
                            "左边缘": (0.0, 0.5), "左上角": (0.0, 0.0)}.items():
        sx, sy = shift_uv(cx, cy, 1920, 1080, BREATH, spec["ghost"])
        sep = 2.0 * math.hypot(sx * 1920, sy * 1080)
        radius = math.hypot(cx - 0.5, cy - 0.5)
        e0, e1 = spec["vig"]
        v = min(max((radius * 1.35 - e1) / (e0 - e1), 0.0), 1.0)
        vignette = v * v * (3 - 2 * v)
        lo = spec["dim"]
        k = lo + (1 - lo) * vignette
        print("  %-8s 重影 %.1f px · 亮度 %.0f%%" % (point, sep, k * 100))


scene = make_scene()
for name, spec in SHADERS.items():
    ghost_report(name, spec)
    apply_shader(scene, 1920, 1080, spec).save(spec["preview"])
    print("saved %s" % spec["preview"])
scene.save("/tmp/domain_shader_source.png")
