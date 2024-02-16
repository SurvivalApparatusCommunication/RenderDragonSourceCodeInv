$input v_color0, v_fog, v_texcoord0, v_lightmapUV, v_normal, v_tangent, v_bitangent, v_worldPos, v_blockAmbientContribution, v_skyAmbientContribution
#if defined(GEOMETRY_PREPASS) || defined(GEOMETRY_PREPASS_ALPHA_TEST)
    $input v_pbrTextureId
#endif

#include <bgfx_shader.sh>
#include <bgfx_compute.sh>

struct PBRTextureData {
    float colourToMaterialUvScale0;
    float colourToMaterialUvScale1;
    float colourToMaterialUvBias0;
    float colourToMaterialUvBias1;
    float colourToNormalUvScale0;
    float colourToNormalUvScale1;
    float colourToNormalUvBias0;
    float colourToNormalUvBias1;
    int flags;
    float uniformRoughness;
    float uniformEmissive;
    float uniformMetalness;
    float maxMipColour;
    float maxMipMer;
    float maxMipNormal;
    float pad;
};

uniform vec4 LightWorldSpaceDirection;
uniform vec4 GlobalRoughness;
uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;
uniform vec4 LightDiffuseColorAndIlluminance;
uniform vec4 ViewPositionAndTime;
uniform vec4 RenderChunkFogAlpha;

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_SeasonsTexture);
SAMPLER2D_AUTOREG(s_LightMapTexture);

#if defined(GEOMETRY_PREPASS) || defined(GEOMETRY_PREPASS_ALPHA_TEST)

BUFFER_RO_AUTOREG(s_PBRData, PBRTextureData);

vec2 octWrap(vec2 v) {
    return (1.0 - abs(v.yx)) * ((2.0 * step(0.0, v)) - 1.0);
}

vec2 ndirToOctSnorm(vec3 n) {
    vec2 p = n.xy * (1.0 / (abs(n.x) + abs(n.y) + abs(n.z)));
    p = (n.z < 0.0) ? octWrap(p) : p;
    return p;
}

vec2 ndirToOctUnorm(vec3 n) {
    vec2 p = ndirToOctSnorm(n);
    return p * 0.5f + 0.5f;
}

vec3 octToNdirSnorm(vec2 p) {
    vec3 n = vec3(p.xy, 1.0 - abs(p.x) - abs(p.y));
    n.xy = (n.z < 0.0) ? octWrap(n.xy) : n.xy;
    return normalize(n);
}

vec3 octToNdirUnorm(vec2 p) {
    vec2 pSnorm = p * 2.0f - 1.0f;
    return octToNdirSnorm(pSnorm);
}

float saturatedLinearRemapZeroToOne(float value, float zeroValue, float oneValue) {
    return saturate((((value) * (1.f / (oneValue - zeroValue))) + -zeroValue / (oneValue - zeroValue)));
}

vec3 calculateTangentNormalFromHeightmap(sampler2D heightmapTexture, vec2 heightmapUV, float mipLevel) {
    vec3 tangentNormal = vec3(0.f, 0.f, 1.f);
    const float kHeightMapPixelEdgeWidth = 1.0f / 12.0f;
    const float kHeightMapDepth = 4.0f;
    const float kRecipHeightMapDepth = 1.0f / kHeightMapDepth;
    float fadeForLowerMips = saturatedLinearRemapZeroToOne(mipLevel, 2.f, 1.f);
    if (fadeForLowerMips > 0.f) {
        vec2 widthHeight = vec2(textureSize(heightmapTexture, 0));
        vec2 pixelCoord = heightmapUV * widthHeight;
        {
            const float kNudgePixelCentreDistEpsilon = 0.0625f;
            const float kNudgeUvEpsilon = 0.25f / 65536.f;
            vec2 nudgeSampleCoord = fract(pixelCoord);
            if (abs(nudgeSampleCoord.x - 0.5) < kNudgePixelCentreDistEpsilon) {
                heightmapUV.x += (nudgeSampleCoord.x > 0.5f) ? kNudgeUvEpsilon : -kNudgeUvEpsilon;
            }
            if (abs(nudgeSampleCoord.y - 0.5) < kNudgePixelCentreDistEpsilon) {
                heightmapUV.y += (nudgeSampleCoord.y > 0.5f) ? kNudgeUvEpsilon : -kNudgeUvEpsilon;
            }
        }
        vec4 heightSamples = textureGather(heightmapTexture, heightmapUV, 0);
        vec2 subPixelCoord = fract(pixelCoord + 0.5f);
        const float kBevelMode = 0.0f;
        vec2 axisSamplePair = (subPixelCoord.y > 0.5f) ? heightSamples.xy : heightSamples.wz;
        float axisBevelCentreSampleCoord = subPixelCoord.x;
        axisBevelCentreSampleCoord += ((axisSamplePair.x > axisSamplePair.y) ? kHeightMapPixelEdgeWidth : -kHeightMapPixelEdgeWidth) * kBevelMode;
        ivec2 axisSampleIndices = ivec2(saturate(vec2(axisBevelCentreSampleCoord - kHeightMapPixelEdgeWidth, axisBevelCentreSampleCoord + kHeightMapPixelEdgeWidth) * 2.f));
        tangentNormal.x = (axisSamplePair[axisSampleIndices.x] - axisSamplePair[axisSampleIndices.y]);
        axisSamplePair = (subPixelCoord.x > 0.5f) ? heightSamples.zy : heightSamples.wx;
        axisBevelCentreSampleCoord = subPixelCoord.y;
        axisBevelCentreSampleCoord += ((axisSamplePair.x > axisSamplePair.y) ? kHeightMapPixelEdgeWidth : -kHeightMapPixelEdgeWidth) * kBevelMode;
        axisSampleIndices = ivec2(saturate(vec2(axisBevelCentreSampleCoord - kHeightMapPixelEdgeWidth, axisBevelCentreSampleCoord + kHeightMapPixelEdgeWidth) * 2.f));
        tangentNormal.y = (axisSamplePair[axisSampleIndices.x] - axisSamplePair[axisSampleIndices.y]);
        tangentNormal.z = kRecipHeightMapDepth;
        tangentNormal = normalize(tangentNormal);
        tangentNormal.xy *= fadeForLowerMips;
    }
    return tangentNormal;
}

vec2 getPBRDataUV(vec2 surfaceUV, vec2 uvScale, vec2 uvBias) {
    return (((surfaceUV) * (uvScale)) + uvBias);
}

float lumaPerceptual(vec3 color) {
    vec3 perceptualLuminance = vec3(0.299, 0.587, 0.114);
    return dot(perceptualLuminance, color);
}

#endif

vec4 applySeasons(vec3 vertexColor, float vertexAlpha, vec4 diffuse) {
    vec2 uv = vertexColor.xy;
    diffuse.rgb *= mix(vec3(1.0, 1.0, 1.0), texture2D(s_SeasonsTexture, uv).rgb * 2.0, vertexColor.b);
    diffuse.rgb *= vec3_splat(vertexAlpha);
    diffuse.a = 1.0;
    return diffuse;
}



void main() {
    vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);

#if defined(ALPHA_TEST) || defined(GEOMETRY_PREPASS_ALPHA_TEST) || defined(DEPTH_ONLY)
    const float ALPHA_THRESHOLD = 0.5;
    if (diffuse.a < ALPHA_THRESHOLD) {
        discard;
    }
#endif

#if defined(SEASONS) && !defined(TRANSPARENT) && !defined(TRANSPARENT_PBR)
    diffuse = applySeasons(v_color0.rgb, v_color0.a, diffuse);
#else
    diffuse.rgb *= v_color0.rgb;
    diffuse.a *= v_color0.a;
#endif

#if (defined(GEOMETRY_PREPASS) || defined(GEOMETRY_PREPASS_ALPHA_TEST)) && (BGFX_SHADER_LANGUAGE_GLSL >= 310 || BGFX_SHADER_LANGUAGE_HLSL >= 500 || BGFX_SHADER_LANGUAGE_PSSL || BGFX_SHADER_LANGUAGE_SPIRV || BGFX_SHADER_LANGUAGE_METAL)
    //RenderChunkSurfGeometryPrepass
    //applyPBRValuesToSurfaceOutput
    PBRTextureData pbrTextureData = s_PBRData[v_pbrTextureId];
    vec2 normalUVScale = vec2(pbrTextureData.colourToNormalUvScale0, pbrTextureData.colourToNormalUvScale1);
    vec2 normalUVBias = vec2(pbrTextureData.colourToNormalUvBias0, pbrTextureData.colourToNormalUvBias1);
    vec2 materialUVScale = vec2(pbrTextureData.colourToMaterialUvScale0, pbrTextureData.colourToMaterialUvScale1);
    vec2 materialUVBias = vec2(pbrTextureData.colourToMaterialUvBias0, pbrTextureData.colourToMaterialUvBias1);

    int kPBRTextureDataFlagHasMaterialTexture  = (1 << 0);
    // These two are mutually exclusive
    int kPBRTextureDataFlagHasNormalTexture    = (1 << 1);
    int kPBRTextureDataFlagHasHeightMapTexture = (1 << 2);

    vec3 tangentNormal = vec3(0, 0, 1);
    if ((pbrTextureData.flags & kPBRTextureDataFlagHasNormalTexture) == kPBRTextureDataFlagHasNormalTexture) {
        vec2 uv = getPBRDataUV(v_texcoord0, normalUVScale, normalUVBias);
        tangentNormal = texture2D(s_MatTexture, uv).xyz * 2.f - 1.f;
    } else if ((pbrTextureData.flags & kPBRTextureDataFlagHasHeightMapTexture) == kPBRTextureDataFlagHasHeightMapTexture) {
        vec2 normalUv = getPBRDataUV(v_texcoord0, normalUVScale, normalUVBias);
        float normalMipLevel = min(pbrTextureData.maxMipNormal - pbrTextureData.maxMipColour, pbrTextureData.maxMipNormal);
        tangentNormal = calculateTangentNormalFromHeightmap(s_MatTexture, normalUv, normalMipLevel);
    }

    float emissive = pbrTextureData.uniformEmissive;
    float metalness = pbrTextureData.uniformMetalness;
    float linearRoughness = pbrTextureData.uniformRoughness;
    if ((pbrTextureData.flags & kPBRTextureDataFlagHasMaterialTexture) == kPBRTextureDataFlagHasMaterialTexture) {
        vec2 uv = getPBRDataUV(v_texcoord0, materialUVScale, materialUVBias);
        vec3 texel = texture2D(s_MatTexture, uv).rgb;
        metalness = texel.r;
        emissive = texel.g;
        linearRoughness = texel.b;
    }

    mat3 tbn = mtxFromRows(
        normalize(v_tangent),
        normalize(v_bitangent),
        normalize(v_normal));
    tbn = transpose(tbn);
    vec3 viewSpaceNormal = mul(tbn, tangentNormal).xyz;

    //computeLighting_RenderChunk_SplitLightMapValues
    vec2 newLightmapUV = min(v_lightmapUV.xy, vec2(1.0f, 1.0f));
    vec3 blockLight = texture2D(s_LightMapTexture, min(vec2(newLightmapUV.x, 1.5f * 1.0f / 16.0f), vec2(1.0, 1.0))).rgb;
    vec3 skyLight = texture2D(s_LightMapTexture, min(vec2(newLightmapUV.y, 0.5f * 1.0f / 16.0f), vec2(1.0, 1.0))).rgb;

    //RenderChunkGeometryPrepass
    //applyPrepassSurfaceToGBuffer
    gl_FragData[0].rgb = diffuse.rgb;
    gl_FragData[0].a = metalness;

    vec3 viewNormal = normalize(viewSpaceNormal).xyz;
    gl_FragData[1].xy = ndirToOctSnorm(viewNormal);

    vec3 worldPosition = v_worldPos;
    vec3 prevWorldPosition = v_worldPos - u_prevWorldPosOffset.xyz;

    vec4 screenSpacePos = mul(u_viewProj, vec4(worldPosition, 1.0));
    screenSpacePos /= screenSpacePos.w;
    screenSpacePos = screenSpacePos * 0.5 + 0.5;

    vec4 prevScreenSpacePos = mul(u_prevViewProj, vec4(prevWorldPosition, 1.0));
    prevScreenSpacePos /= prevScreenSpacePos.w;
    prevScreenSpacePos = prevScreenSpacePos * 0.5 + 0.5;

    gl_FragData[1].zw = screenSpacePos.xy - prevScreenSpacePos.xy;

    gl_FragData[2] = vec4(
        emissive, 
        lumaPerceptual(blockLight), 
        lumaPerceptual(skyLight), 
        linearRoughness);

#else

    #if defined(DEPTH_ONLY) || defined(DEPTH_ONLY_OPAQUE)
        diffuse = vec4(1.0, 1.0, 1.0, 1.0);
    #endif

    #if defined(TRANSPARENT_PBR)
        //computeLighting_RenderChunk_Split
        vec3 blockLight = texture2D(s_LightMapTexture, min(vec2(v_lightmapUV.x, 0.09375), 1.0)).xyz;
        vec3 skyLight = texture2D(s_LightMapTexture, min(vec2(v_lightmapUV.y, 0.03125), 1.0)).xyz;
        diffuse.rgb *= saturate(blockLight + skyLight);
    #endif

    gl_FragData[0].rgb = mix(diffuse.rgb, FogColor.rgb, v_fog.a);
    gl_FragData[0].a = diffuse.a;
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, 0.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 0.0);

#endif
}
