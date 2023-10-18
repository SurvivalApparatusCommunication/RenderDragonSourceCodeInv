$input v_texcoord0

#include <bgfx_shader.sh>

SAMPLER2D(s_CracksTexture, 0);

void main() {
    vec4 diffuse = texture2D(s_CracksTexture, v_texcoord0);

    const float ALPHA_THRESHOLD = 0.5;
    if (diffuse.a < ALPHA_THRESHOLD) {
        discard;
    }

    gl_FragColor = diffuse;
}
