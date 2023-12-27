$input v_color, v_fog, v_light, v_texCoords

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/ActorUtil.dragonh>
#include <MinecraftRenderer.Materials/FogUtil.dragonh>

uniform vec4 DiscardValue;
uniform vec4 HudOpacity;
uniform vec4 OverlayColor;
uniform vec4 ChangeColor;
uniform vec4 CurrentColor;

SAMPLER2D(s_MatTexture, 0);

void main() {
    vec4 diffuse;
#ifndef USE_TEXTURES
    diffuse = vec4(1.0, 1.0, 1.0, 1.0);
#endif

#if defined(USE_TEXTURES) && (defined(MULTI_COLOR_TINT) || defined(ALPHA_TEST))
    diffuse = texture2D(s_MatTexture, v_texCoords);
#endif

#if defined(ALPHA_TEST) && (defined(LIT) || defined(MULTI_COLOR_TINT))
    if (diffuse.a <= 0.0) discard;
#endif

#ifdef MULTI_COLOR_TINT
    vec2 colorMask = diffuse.rg;
    diffuse.rgb = colorMask.rrr * v_color.rgb;
    diffuse.rgb = mix(diffuse.rgb, colorMask.ggg * ChangeColor.rgb, ceil(colorMask.g));
#endif

#if !defined(ALPHA_TEST) && !defined(MULTI_COLOR_TINT) && defined(USE_TEXTURES)
    diffuse = texture2D(s_MatTexture, v_texCoords);
#endif
#if defined(ALPHA_TEST) && !defined(LIT) && !defined(MULTI_COLOR_TINT)
    if (diffuse.a <= 0.0) discard;
    
#endif
    diffuse.rgb = mix(diffuse.rgb, OverlayColor.rgb, OverlayColor.a);
#ifdef LIT
    diffuse *= v_light;
#endif
#ifndef MULTI_COLOR_TINT
    diffuse = CurrentColor * v_color * diffuse;
#endif

    diffuse.rgb = applyFog(diffuse.rgb, v_fog.rgb, v_fog.a);

    gl_FragColor = diffuse;
}
