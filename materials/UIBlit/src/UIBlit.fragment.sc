$input v_texcoord0

#include <bgfx_shader.sh>

SAMPLER2D(s_MatTexture, 0);
uniform vec4 TintColor;
uniform vec4 HudOpacity;
void main() {
    vec4 tex = texture2D(s_MatTexture, v_texcoord0);
    vec4 diffuse = tex * TintColor;
    diffuse.a = diffuse.a * HudOpacity.x;
    gl_FragColor = diffuse;
}
