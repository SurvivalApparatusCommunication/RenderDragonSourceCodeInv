$input a_position, a_texcoord0
$output v_texcoord0

#include <bgfx_shader.sh>
uniform vec4 SubPixelOffset;
void main() {
    highp vec4 pos1;
    highp vec4 pos2;
    highp mat4 offsetProj_3;

    offsetProj_3 = u_proj;

    highp vec4 pos3;
    pos3.yzw = u_proj[2].yzw;
    pos3.x = (u_proj[2].x + SubPixelOffset.x);
    offsetProj_3[2] = pos3;

    highp vec4 pos4;
    pos4.xzw = offsetProj_3[2].xzw;
    pos4.y = (offsetProj_3[2].y - SubPixelOffset.y);
    offsetProj_3[2] = pos4;

    highp vec4 pos5;
    pos5.w = 1.0;
    pos5.xyz = a_position;
    pos2 = (offsetProj_3 * (u_view * pos5));
    pos1.xyw = pos2.xyw;
    pos1.z = (pos2.z - 0.0001220703);

    v_texcoord0 = a_texcoord0;
    gl_Position = pos1;
}
