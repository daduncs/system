#version 330

#moj_import <system:stair.glsl>

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 quadSize;
in vec4 corners;
in float extra;

out vec4 fragColor;

const int SAMPLES = 40;
const float GOLDEN = 2.39996323;
const float SHEEN = 0.55;
const vec2 LIGHT = vec2(-0.55, -0.83);

vec3 ring(vec2 base, vec2 texel, float radius, float turn) {
    vec3 sum = vec3(0.0);
    float total = 0.0;

    for (int i = 0; i < SAMPLES; i++) {
        float index = float(i) + 0.5;
        float spread = sqrt(index / float(SAMPLES));
        float angle = index * GOLDEN + turn;

        vec2 offset = vec2(cos(angle), sin(angle)) * spread * radius * texel;
        float weight = 1.0 - spread * 0.25;

        sum += texture(Sampler0, base + offset).rgb * weight;
        total += weight;
    }

    return sum / total;
}

vec3 frost(vec2 base, vec2 texel, float radius) {
    vec3 wide = ring(base, texel, radius, 0.0);
    vec3 tight = ring(base, texel, radius * 0.42, 1.7);

    return mix(wide, tight, 0.35);
}

float edge(float dist) {
    float aa = max(fwidth(dist), 1.0e-4);
    return clamp(0.5 - dist / aa, 0.0, 1.0);
}

void main() {
    vec2 point = texCoord0 * quadSize;

    vec2 unit = vec2(dFdx(point.x), dFdy(point.y));
    unit = mix(vec2(0.0001), unit, step(0.0001, abs(unit)));

    float dist = stairDistance(point, quadSize, corners);
    float alpha = edge(dist);

    if (alpha <= 0.001) discard;

    vec2 nudge = vec2(1.2, 0.0);
    vec2 slope = vec2(
        stairDistance(point + nudge.xy, quadSize, corners) - stairDistance(point - nudge.xy, quadSize, corners),
        stairDistance(point + nudge.yx, quadSize, corners) - stairDistance(point - nudge.yx, quadSize, corners)
    );
    vec2 normal = slope / max(length(slope), 0.0001);

    float rim = clamp(1.0 + dist / 7.0, 0.0, 1.0);
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    float radius = max(extra, 1.0) * 2.6;

    vec2 base = gl_FragCoord.xy - (normal / unit) * pow(rim, 2.4) * 18.0;
    vec3 scene = frost(base * texel, texel, radius);

    vec2 split = (normal / unit) * rim * rim * 3.2;
    float red = frost((base + split) * texel, texel, radius).r;
    float blue = frost((base - split) * texel, texel, radius).b;
    scene = mix(scene, vec3(red, scene.g, blue), rim * 0.9);

    vec3 glass = mix(scene, vertexColor.rgb, vertexColor.a);

    float lit = clamp(dot(normal, LIGHT), 0.0, 1.0);
    float back = clamp(-dot(normal, LIGHT), 0.0, 1.0);
    float lip = clamp(1.0 + dist / 1.5, 0.0, 1.0);
    float dust = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);

    glass *= 1.0 - 0.26 * rim * rim * back;
    glass += rim * rim * rim * SHEEN * (0.06 + 0.36 * lit + 0.14 * back);
    glass += pow(lip, 3.0) * SHEEN * (0.12 + 0.7 * lit + 0.2 * back);
    glass += (dust - 0.5) * 0.01;

    fragColor = vec4(glass, alpha * clamp(corners.w / 100.0, 0.0, 1.0)) * ColorModulator;
}
