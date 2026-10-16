#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 texCoord0;
out vec4 vertexColor;
out vec2 quadSize;
out vec4 corners;
out float extra;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;
    quadSize = vec2(UV1) / 16.0;
    corners = vec4(vec2(UV2) / 16.0, Normal.x * 127.0, Normal.y * 127.0);
    extra = Normal.z * 127.0;
}
