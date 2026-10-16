#version 330

#moj_import <system:fluid.glsl>

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

void main() {
    if (any(lessThan(texCoord0, vec2(0.0))) || any(greaterThan(texCoord0, vec2(1.0)))) discard;

    float mask = texture(Sampler0, texCoord0).a;

    if (mask <= 0.003) discard;

    vec3 flow = fluid(texCoord0 * 96.0, vec2(96.0), corners.x * 0.6, vertexColor.rgb);
    vec3 colour = mix(vertexColor.rgb * 1.15, flow * 1.3, 0.5);

    fragColor = vec4(colour, mask * vertexColor.a) * ColorModulator;
}
