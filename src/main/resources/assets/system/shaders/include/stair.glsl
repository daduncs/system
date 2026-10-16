#version 330

float boxAt(vec2 point, float left, float top, float width, float height, vec4 radii) {
    vec2 centre = vec2(left + width * 0.5, top + height * 0.5);
    vec2 halfSize = vec2(width * 0.5, height * 0.5);
    vec2 local = point - centre;

    float radius = (local.x > 0.0)
        ? ((local.y > 0.0) ? radii.z : radii.w)
        : ((local.y > 0.0) ? radii.y : radii.x);

    radius = min(radius, min(halfSize.x, halfSize.y));

    vec2 offset = abs(local) - halfSize + radius;
    return min(max(offset.x, offset.y), 0.0) + length(max(offset, 0.0)) - radius;
}

float fillet(vec2 point, vec2 corner, vec2 side, float radius) {
    vec2 local = abs(point - corner);

    if (radius < 0.5 || max(local.x, local.y) > radius) return 1.0e5;

    return radius - length(point - corner - side * radius);
}

float stairDistance(vec2 point, vec2 size, vec4 corners) {
    float bend = corners.z;
    float own = size.x - bend;
    float tall = size.y;
    float above = corners.x;
    float below = corners.y;

    bool up = above > 0.5;
    bool down = below > 0.5;

    float stepAbove = min(bend, abs(above - own) * 0.5);
    float stepBelow = min(bend, abs(below - own) * 0.5);

    float topLeft = up ? 0.0 : bend;
    float topRight = !up ? bend : (above < own - 0.5 ? stepAbove : 0.0);
    float bottomLeft = down ? 0.0 : bend;
    float bottomRight = !down ? bend : (below < own - 0.5 ? stepBelow : 0.0);

    float dist = boxAt(point, 0.0, 0.0, own, tall, vec4(topLeft, bottomLeft, bottomRight, topRight));

    if (up) {
        dist = min(dist, boxAt(point, 0.0, -tall, min(above, own), tall * 2.0, vec4(0.0, bottomLeft, bottomRight, 0.0)));
        dist = min(dist, boxAt(point, 0.0, -tall, above, tall, vec4(0.0, 0.0, above > own + 0.5 ? stepAbove : 0.0, 0.0)));
        dist = min(dist, fillet(point, vec2(min(above, own), 0.0), vec2(1.0, above > own ? 1.0 : -1.0), stepAbove));
    }

    if (down) {
        dist = min(dist, boxAt(point, 0.0, 0.0, min(below, own), tall * 2.0, vec4(topLeft, 0.0, 0.0, topRight)));
        dist = min(dist, boxAt(point, 0.0, tall, below, tall, vec4(0.0, 0.0, 0.0, below > own + 0.5 ? stepBelow : 0.0)));
        dist = min(dist, fillet(point, vec2(min(below, own), tall), vec2(1.0, below > own ? -1.0 : 1.0), stepBelow));
    }

    return dist;
}
