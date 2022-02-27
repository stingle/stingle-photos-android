uniform mat4 viewProjectionMatrix;

attribute vec4 position;

void main() {
    gl_Position = viewProjectionMatrix * position;
}