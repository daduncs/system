#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec4 cell;
in float thickness;

out vec4 fragColor;

const int RING = 24;

float ink(vec2 at, vec2 low, vec2 high) {
    if (any(lessThan(at, low)) || any(greaterThan(at, high))) return 0.0;

    return texture(Sampler0, at).a;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 low = cell.xy * texel;
    vec2 high = (cell.xy + cell.zw) * texel;

    float alpha = ink(texCoord0, low, high);

    if (thickness > 0.0) {
        for (int i = 0; i < RING; i++) {
            float angle = float(i) * 6.2831853 / float(RING);
            vec2 reach = vec2(cos(angle), sin(angle)) * texel * thickness;

            alpha = max(alpha, ink(texCoord0 + reach, low, high));
            alpha = max(alpha, ink(texCoord0 + reach * 0.5, low, high));
        }
    }

    alpha *= vertexColor.a;

    if (alpha <= 0.003) discard;

    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
}
