$input a_position, a_texcoord0
$output v_ndcPosition, v_texcoord0

#include <bgfx_shader.sh>

void main() {
    vec4 clipPosition = mul(u_modelViewProj, vec4(a_position, 1.0));
    vec3 ndcPosition = clipPosition.xyz / clipPosition.w;

    gl_Position = clipPosition;
    v_ndcPosition = ndcPosition;
    v_texcoord0 = a_texcoord0;
}
