#version 330

#moj_import <system:stair.glsl>

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 quadSize;
in vec4 corners;
in float extra;

out vec4 fragColor;

float edge(float dist) {
    float aa = max(fwidth(dist), 1.0e-4);
    return clamp(0.5 - dist / aa, 0.0, 1.0);
}

void main() {
    float grow = corners.w;
    vec2 point = texCoord0 * quadSize - grow;

    float dist = stairDistance(point, quadSize - grow * 2.0, corners);
    float thickness = max(extra, 1.0);

    float outside = edge(dist - (thickness - 1.0));
    float inside = edge(dist + 1.0);
    float alpha = clamp(outside - inside, 0.0, 1.0);

    if (alpha <= 0.001) discard;

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha) * ColorModulator;
}
