//wip, expect bugs
$input v_texcoord0

#include <bgfx_shader.sh>
uniform vec4 MatColor;

void main() {
    vec4 color = MatColor;
    color.a = 0.1;
    gl_FragColor = color;
    gl_FragDepth = 0.0;
}
