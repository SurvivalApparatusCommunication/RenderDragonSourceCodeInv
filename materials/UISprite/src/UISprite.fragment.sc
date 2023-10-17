$input v_texcoord0, v_color0

#include <bgfx_shader.sh>

SAMPLER2D(s_MatTexture, 0);

uniform vec4 TintColor;
uniform vec4 HudOpacity;

#ifdef MultiColorTint
uniform vec4 ChangeColor;
#endif

void main() {
    vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);

#ifdef MultiColorTint
	vec2 colorMask = diffuse.rg;
	diffuse.rgb = colorMask.rrr * v_color0.rgb;
	diffuse.rgb = mix(diffuse.rgb, colorMask.ggg * ChangeColor.rgb, ceil(colorMask.g));
#else
	diffuse.rgb = mix(diffuse.rgb, diffuse.rgb * v_color0.rgb, diffuse.a);
#endif

	if (v_color0.a > 0.0) {
	diffuse.a = ceil(diffuse.a);
	}

	diffuse *= TintColor;
	diffuse.a = diffuse.a * HudOpacity.x;

	gl_FragColor = diffuse;
}
