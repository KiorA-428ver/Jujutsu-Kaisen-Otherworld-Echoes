#version 150

// 乱解·后处理：施放期间挂在施放者屏幕上的"柔和暗化屏幕边缘 + 中度色差"滤镜。
// 算法与 sukuna_stream.fsh / sukuna_domain.fsh 同源——先在像素空间定单通道重影
// 宽度，再除回各轴像素数变回 UV，保证 16:9 屏上下与左右的重影一样可见；
// 强度整体取中间档：重影约为连续解的 1.5 倍、领域的一半上下（上下边缘红蓝总
// 分离约 6 像素、左右与四角约 8.5 像素），暗化到约 66% 亮度并带轻微血色倾斜，
// 配合 140% 的方向性震屏，就是"近身处连环斩开"的压迫感。
uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform float Time;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float radius = length(centered);
    float breath = 0.9 + 0.1 * sin(Time * 2.9 + radius * 5.5);

    vec2 size = max(InSize, vec2(1.0));
    vec2 shift;
    if (size.x < 16.0 || size.y < 16.0) {
        // 兜底：拿不到真实缓冲尺寸时退回保守的 UV 比例偏移，绝不整屏撕裂。
        shift = centered * (0.0034 + 0.0026 * radius) * breath;
    } else {
        vec2 pixelPos = centered * size;
        float radiusPx = length(pixelPos);
        // 归一化半径：以屏幕竖直半高为 1.0，上下边缘 = 1.0，左右与四角被夹到 1.35。
        float t = clamp(radiusPx / max(0.5 * size.y, 1.0), 0.0, 1.35);
        // 除零兜底：正中心 radiusPx = 0 → dir = 0，中心自然没有任何位移。
        vec2 dir = pixelPos / max(radiusPx, 1.0);
        // 单通道重影宽度（像素）：上下边缘约 3.0、左右与四角约 4.2——
        // 红蓝总分离上下约 6.0 像素、左右约 8.5 像素（介于连续解与领域之间，
        // 校验方法同 tools_check_domain_shader.py 的 stream 段，把系数换成这里的）。
        float ghostPx = (0.5 + 1.7 * t + 0.8 * t * t) * breath;
        shift = dir * (ghostPx / size);
    }

    vec3 col;
    col.r = texture(DiffuseSampler, texCoord + shift).r;
    col.g = texture(DiffuseSampler, texCoord).g;
    col.b = texture(DiffuseSampler, texCoord - shift).b;

    // 轻度血色倾向：乱解是术式连环斩，比连续解浓一档、远不及领域的压顶暗红。
    col = mix(col, col * vec3(1.08, 0.90, 0.94), 0.45);

    // 柔和暗化（vignette）：写成 1.0 - smoothstep(小, 大, x)，
    // 避开 GLSL 里 edge0 >= edge1 的未定义行为，任何显卡上表现一致。
    float vignette = 1.0 - smoothstep(0.45, 1.1, radius * 1.35);
    col *= mix(0.66, 1.0, vignette);

    fragColor = vec4(col, 1.0);
}
