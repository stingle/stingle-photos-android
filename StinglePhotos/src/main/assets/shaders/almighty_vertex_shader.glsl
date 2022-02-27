uniform mat4 viewProjectionMatrix;

attribute vec4 inputTextureCoordinate;
attribute vec4 position;

varying vec2 textureCoordinate;

void main() {
    gl_Position = viewProjectionMatrix * position;
    textureCoordinate = inputTextureCoordinate.xy;
}