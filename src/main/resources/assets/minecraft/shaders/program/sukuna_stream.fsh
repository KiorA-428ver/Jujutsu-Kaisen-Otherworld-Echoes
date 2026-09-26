#version 150

// 连续解·后处理：施放期间挂在施放者屏幕上的轻量版领域滤镜——
// 边缘柔和暗化 + 刚好可辨的轻微色差，比领域展开明显收敛一档
// （领域是暗化到 45% 亮度、上下边缘 10.6 像素重影；这里暗化只到
// 约 76%、边缘重影约 4~5.7 像素），再配上持续轻微震屏就是"蓄力出招"的氛围。
// 与 sukuna_domain.fsh 同一套算法：先换算到像素空间定重影宽度，再除回
// 各轴像素数变回 UV，保证 16:9 屏上下与左右的重影一样可见。
uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform float Time;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float radius = length(centered);
    float breath = 0.9 + 0.1 * sin(Time * 2.6 + radius * 5.0);

    vec2 size = max(InSize, vec2(1.0));
    vec2 shift;
    if (size.x < 16.0 || size.y < 16.0) {
        // 兜底：拿不到真实缓冲尺寸时退回保守的 UV 比例偏移，绝不整屏撕裂。
        shift = centered * (0.0022 + 0.0016 * radius) * breath;
    } else {
        vec2 pixelPos = centered * size;
        float radiusPx = length(pixelPos);
        // 归一化半径：以屏幕竖直半高为 1.0，上下边缘 = 1.0，左右与四角被夹到 1.35。
        float t = clamp(radiusPx / max(0.5 * size.y, 1.0), 0.0, 1.35);
        // 除零兜底：正中心 radiusPx = 0 → dir = 0，中心自然没有任何位移。
        vec2 dir = pixelPos / max(radiusPx, 1.0);
        // 单通道重影宽度（像素）：上下边缘约 2.0、左右与四角约 2.8——
        // 红蓝总分离上下约 4.0 像素、左右约 5.7 像素（CPU 复刻校验见
        // tools_check_domain_shader.py 的 stream 段），属"刚好看出色彩重影"的下一档。
        float ghostPx = (0.35 + 1.1 * t + 0.55 * t * t) * breath;
        shift = dir * (ghostPx / size);
    }

    vec3 col;
    col.r = texture(DiffuseSampler, texCoord + shift).r;
    col.g = texture(DiffuseSampler, texCoord).g;
    col.b = texture(DiffuseSampler, texCoord - shift).b;

    // 极淡的血色倾向：连续解是宿傩之力在贯注出手，颜色比领域克制得多。
    col = mix(col, col * vec3(1.06, 0.94, 0.97), 0.35);

    // 柔和暗化（vignette）：写成 1.0 - smoothstep(小, 大, x)，
    // 避开 GLSL 里 edge0 >= edge1 的未定义行为，任何显卡上表现一致。
    float vignette = 1.0 - smoothstep(0.5, 1.1, radius * 1.35);
    col *= mix(0.72, 1.0, vignette);

    fragColor = vec4(col, 1.0);
}
