#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform float Time;
uniform float Aberration;
uniform float Distort;
uniform float Grain;
uniform float Vignette;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

void main() {
    vec2 uv = texCoord;
    vec2 c = uv - 0.5;
    float r = length(c);
    uv += vec2(sin(uv.y * 34.0 + Time * 5.0) + sin(uv.y * 91.0 - Time * 7.0) * 0.4, cos(uv.x * 28.0 + Time * 4.3) * 0.6) * 0.0045 * Distort;
    float slice = floor(uv.y * 28.0);
    float g = step(0.985 - 0.06 * Distort, hash(vec2(slice, floor(Time * 12.0))));
    uv.x += g * (hash(vec2(slice, Time)) - 0.5) * 0.12 * clamp(Distort * 2.0, 0.0, 1.0);
    vec2 dir = normalize(c + vec2(1e-5));
    float ab = Aberration * 0.012 * (0.3 + r * 1.7);
    vec3 col = vec3(texture(DiffuseSampler, uv + dir * ab).r, texture(DiffuseSampler, uv).g, texture(DiffuseSampler, uv - dir * ab).b);
    col += (hash(uv * InSize + Time * 60.0) - 0.5) * Grain * 0.35;
    col *= 1.0 - smoothstep(0.35, 0.95, r * 1.25) * Vignette;
    fragColor = vec4(col, 1.0);
}
