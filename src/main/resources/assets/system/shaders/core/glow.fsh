#version 330

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

const int STEPS = 8;

vec2 erf(vec2 x) {
    vec2 s = sign(x);
    vec2 a = abs(x);
    x = 1.0 + (0.278393 + (0.230389 + 0.078108 * (a * a)) * a) * a;
    x *= x;
    return s - s / (x * x);
}

float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma)) / (2.5066283 * sigma);
}

float row(float x, float y, float sigma, float corner, vec2 extent) {
    float delta = min(extent.y - corner - abs(y), 0.0);
    float curved = extent.x - corner + sqrt(max(0.0, corner * corner - delta * delta));
    vec2 integral = 0.5 + 0.5 * erf((x + vec2(-curved, curved)) * (0.70710678 / sigma));

    return integral.y - integral.x;
}

float blur(vec2 point, vec2 extent, float sigma, float corner) {
    float start = clamp(-3.0 * sigma, point.y - extent.y, point.y + extent.y);
    float end = clamp(3.0 * sigma, point.y - extent.y, point.y + extent.y);
    float stride = (end - start) / float(STEPS);
    float y = start + stride * 0.5;
    float value = 0.0;

    for (int i = 0; i < STEPS; i++) {
        value += row(point.x, point.y - y, sigma, corner, extent) * gaussian(y, sigma) * stride;
        y += stride;
    }

    return value;
}

void main() {
    float margin = corners.x;
    float sigma = max(corners.y, 0.5);
    vec2 extent = (quadSize - margin * 2.0) * 0.5;
    float corner = min(corners.z, min(extent.x, extent.y));
    vec2 point = texCoord0 * quadSize - quadSize * 0.5;

    float light = blur(point, extent, sigma, corner) * extra / 35.0 * vertexColor.a;

    if (light <= 0.002) discard;

    vec3 colour = vertexColor.rgb;
    vec3 vivid = clamp(mix(vec3(dot(colour, vec3(0.299, 0.587, 0.114))), colour, 1.6), 0.0, 1.0);

    fragColor = vec4(vivid, min(light, 1.0)) * ColorModulator;
}
