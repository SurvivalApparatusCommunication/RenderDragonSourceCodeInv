$input v_texcoord0

#include <bgfx_shader.sh>
uniform vec4 MatColor;

void main() {
    gl_FragColor = MatColor;
}
