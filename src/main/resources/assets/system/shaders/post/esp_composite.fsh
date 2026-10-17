#version 330

#moj_import <system:seeds.glsl>
#moj_import <system:fluid.glsl>

uniform sampler2D SilhouetteSampler;
uniform sampler2D SeedSampler;
uniform sampler2D BlurSampler;

layout(std140) uniform EspStyle {
    vec4 Style;
    vec4 Look;
    vec4 From;
    vec4 To;
};

in vec2 texCoord;

out vec4 fragColor;

const vec2 RING[8] = vec2[](
    vec2(1.0, 0.0), vec2(0.7071, 0.7071), vec2(0.0, 1.0), vec2(-0.7071, 0.7071),
    vec2(-1.0, 0.0), vec2(-0.7071, -0.7071), vec2(0.0, -1.0), vec2(0.7071, -0.7071)
);

float openness(ivec2 pixel, float radius) {
    float open = 0.0;
    for (int i = 0; i < 8; i++) {
        ivec2 tap = pixel + ivec2(round(RING[i] * radius));
        open += texelFetch(SilhouetteSampler, tap, 0).a > 0.0 ? 0.0 : 1.0;
    }
    return open / 8.0;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 seedTexel = texelFetch(SeedSampler, pixel, 0);
    if (!hasSeed(seedTexel)) {
        discard;
    }

    float width = Style.x;
    float glow = Style.y;
    float fill = Style.z;
    float time = Style.w;

    vec2 seed = decodeSeed(seedTexel);
    vec3 color = texelFetch(SilhouetteSampler, ivec2(seed), 0).rgb;

    if (Look.y > 0.5) {
        float wave = fract((gl_FragCoord.x + gl_FragCoord.y) * 0.0012 - time * 0.12);
        color = mix(From.rgb, To.rgb, wave < 0.5 ? wave * 2.0 : (1.0 - wave) * 2.0);
    }

    if (texelFetch(SilhouetteSampler, pixel, 0).a > 0.0) {
        float edge = clamp(max(openness(pixel, 1.5), openness(pixel, 3.5) * 0.5) * 2.0, 0.0, 1.0);

        vec3 inner = color;
        float alpha = fill;

        if (Look.x > 1.5) {
            inner = fluid(gl_FragCoord.xy, vec2(textureSize(SilhouetteSampler, 0)), time, color);
            alpha = clamp(0.55 + fill, 0.0, 1.0);
        } else if (Look.x > 0.5) {
            float band = fract((gl_FragCoord.x + gl_FragCoord.y) * 0.0035 - time * 0.35);
            float sheen = smoothstep(0.0, 0.05, band) * (1.0 - smoothstep(0.05, 0.16, band));
            vec3 frosted = texture(BlurSampler, texCoord).rgb;
            inner = mix(frosted, color, 0.42) + color * sheen * 0.4;
            alpha = fill * (0.8 + sheen * 0.6);
        }

        fragColor = vec4(mix(inner, color, edge), clamp(max(alpha, edge * 0.85), 0.0, 1.0));
        return;
    }

    float dist = length(vec2(pixel) - seed) - 0.5;
    float line = 1.0 - smoothstep(width - 0.8, width + 0.8, dist);
    float halo = glow > 0.0 ? exp(-max(dist - width, 0.0) / (glow * 0.35)) * 0.6 : 0.0;

    float alpha = max(line, halo);
    if (alpha < 0.004) {
        discard;
    }
    fragColor = vec4(color, alpha);
}
