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
    vec2 point = texCoord0 * quadSize;

    float alpha = edge(stairDistance(point, quadSize, corners));
    if (alpha <= 0.001) discard;

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha) * ColorModulator;
}
