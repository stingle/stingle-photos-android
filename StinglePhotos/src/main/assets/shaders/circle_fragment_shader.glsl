varying vec2 textureCoordinate;

void main() {
    vec2 center = vec2(0.5, 0.5);
    float d = distance(textureCoordinate, center);
    float a = smoothstep(0.497, 0.5, d);

    gl_FragColor = vec4(1.0, 1.0, 1.0, (1.0 - a));
}