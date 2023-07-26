$input v_color0, v_fog, v_normal, v_tangent, v_bitangent, v_texcoord0, v_lightmapUV, v_worldPos, v_blockAmbientContribution, v_skyAmbientContribution
#if defined(FORWARD_PBR_TRANSPARENT)
    $input v_pbrTextureId
#endif

#include <bgfx_shader.sh>
#include <bgfx_compute.sh>

struct Light {
    vec4 position;
    vec4 color;
    int gridHierarchyLevel;
    float gridLevelRadius;
    float higherGridLevelRadius;
    float lowerGridLevelRadius;
};

struct LightData {
    float lookup;
};

struct LightSourceWorldInfo {
    vec4 worldSpaceDirection;
    vec4 diffuseColorAndIlluminance;
    vec4 shadowDirection;
    mat4 shadowProj0;
    mat4 shadowProj1;
    mat4 shadowProj2;
    mat4 shadowProj3;
    int isSun;
    int shadowCascadeNumber;
    int pad0;
    int pad1;
};

struct PBRFragmentInfo {
    vec2 lightClusterUV;
    vec3 worldPosition;
    vec3 viewPosition;
    vec3 ndcPosition;
    vec3 worldNormal;
    vec3 viewNormal;
    vec3 albedo;
    float metalness;
    float roughness;
    float emissive;
    float blockAmbientContribution;
    float skyAmbientContribution;
};

struct PBRLightingContributions {
    vec3 directDiffuse;
    vec3 directSpecular;
    vec3 indirectDiffuse;
    vec3 indirectSpecular;
    vec3 emissive;
};

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

uniform mat4 PointLightProj;
uniform vec4 PointLightShadowParams1;
uniform vec4 ShadowBias;
uniform vec4 SunDir;
uniform vec4 ShadowSlopeBias;
uniform vec4 PrepassUVOffset;
uniform vec4 BlockBaseAmbientLightColorIntensity;
uniform vec4 CascadeShadowResolutions;
uniform vec4 LightWorldSpaceDirection;
uniform vec4 LightDiffuseColorAndIlluminance;
uniform vec4 AtmosphericScatteringEnabled;
uniform vec4 ViewPositionAndTime;
uniform mat4 CloudShadowProj;
uniform vec4 VolumeScatteringEnabled;
uniform vec4 FogSkyBlend;
uniform vec4 PointLightDiffuseFadeOutParameters;
uniform vec4 MoonDir;
uniform vec4 ClusterSize;
uniform vec4 AtmosphericScattering;
uniform vec4 FogAndDistanceControl;
uniform vec4 RenderChunkFogAlpha;
uniform vec4 DiffuseSpecularEmissiveAmbientTermToggles;
uniform vec4 DirectionalLightToggleAndCountAndMaxDistance;
uniform vec4 EmissiveMultiplierAndDesaturationAndCloudPCFAndContribution;
uniform vec4 DirectionalShadowModeAndCloudShadowToggleAndPointLightToggleAndShadowToggle;
uniform vec4 FogColor;
uniform vec4 VolumeDimensions;
uniform vec4 ShadowPCFWidth;
uniform vec4 MoonColor;
uniform vec4 ShadowParams;
uniform vec4 SkyAmbientLightColorIntensity;
uniform vec4 ClusterDimensions;
uniform vec4 ClusterNearFarWidthHeight;
uniform vec4 SunColor;
uniform vec4 PointLightSpecularFadeOutParameters;
uniform vec4 VolumeNearFar;
uniform vec4 IBLParameters;
uniform vec4 SkyZenithColor;
uniform vec4 SkyHorizonColor;

SAMPLER2D(s_MatTexture, 5);
SAMPLER2D(s_SeasonsTexture, 4);
SAMPLER2D(s_LightMapTexture, 6);
BUFFER_RO(s_PBRData, PBRTextureData, 7);

SAMPLERCUBE(s_SpecularIBL, 0);
SAMPLER2D(s_BrdfMap, 1);

SAMPLER2DSHADOW(s_CloudShadow, 3);
SAMPLER2DARRAYSHADOW(s_ShadowCascades0, 2);
SAMPLER2DARRAYSHADOW(s_ShadowCascades1, 9);

BUFFER_RO(s_DirectionalLightSources, LightSourceWorldInfo, 8);
BUFFER_RO(s_LightLookupArray, LightData, 10);
BUFFER_RO(s_Lights, Light, 11);

SAMPLER2DARRAYSHADOW(s_PointLightShadowTextureArray, 12);
SAMPLER2DARRAY(s_ScatteringBuffer, 13);

#if FORWARD_PBR_TRANSPARENT

/*vec3 color_gamma(vec3 clr) {
float e = 1.0 / 2.2;
return pow(max(clr, vec3(0.0, 0.0, 0.0)), vec3(e, e, e));
}*/
/*vec4 color_gamma(vec4 clr) {
return vec4(color_gamma(clr.rgb), clr.a);
}*/
vec3 color_degamma(vec3 clr) {
    float e = 2.2;
    return pow(max(clr, vec3(0.0, 0.0, 0.0)), vec3(e, e, e));
}
vec4 color_degamma(vec4 clr) {
    return vec4(color_degamma(clr.rgb), clr.a);
}
float luminance(vec3 clr) {
    return dot(clr, vec3(0.2126, 0.7152, 0.0722));
}
/*float lumaPerceptual(vec3 color) {
vec3 perceptualLuminance = vec3(0.299, 0.587, 0.114);
return dot(perceptualLuminance, color);
}*/
/*float lumaAverage(vec3 color) {
return dot(color, vec3(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0));
}*/
vec3 desaturate(vec3 color, float amount) {
    float lum = luminance(color);
    return mix(color, vec3(lum, lum, lum), amount);
}
/*vec3 convertHueToRGB(float hue) {
float R = abs(hue * 6.0 - 3.0) - 1.0;
float G = 2.0 - abs(hue * 6.0 - 2.0);
float B = 2.0 - abs(hue * 6.0 - 4.0);
return clamp(vec3(R, G, B), 0.0, 1.0);
}*/
/*float removeGammaCorrection(float v) {
if (v <= 0.04045) {
return (v / 12.92);
}
else {
return pow((v + 0.055) / 1.055, 2.4);
}
}*/
/*float applyGammaCorrection(float v) {
if (v <= 0.0031308) {
return v * 12.92;
}
else {
return (1.055 * pow(v, (1.0 / 2.4)) - 0.055);
}
}*/
/*vec3 convertRGBTosRGB(vec3 rgb) {
return vec3(
removeGammaCorrection(rgb.r),
removeGammaCorrection(rgb.g),
removeGammaCorrection(rgb.b)
);
}*/
/*vec3 convertsRGBToRGB(vec3 sRGB) {
return vec3(
applyGammaCorrection(sRGB.r),
applyGammaCorrection(sRGB.g),
applyGammaCorrection(sRGB.b)
);
}*/
/*mat3 getsRGBtoLMSTransform() {
return mat3(
0.31399022, 0.63951294, 0.04649755,
0.15537241, 0.75789446, 0.08670142,
0.01775239, 0.10944209, 0.87256922
);
}*/
/*mat3 getLMStosRGBTransform() {
return mat3(
5.47221206, -4.6419601, 0.16963708,
-1.1252419, 2.29317094, -0.1678952,
0.02980165, -0.19318073, 1.16364789
);
}*/
/*vec3 sRgbToYCbCr( vec3 rgb ) {
return vec3(
0.257 * rgb.r + 0.504 * rgb.g + 0.098 * rgb.b,
-0.148 * rgb.r - 0.291 * rgb.g + 0.439 * rgb.b,
0.439 * rgb.r - 0.368 * rgb.g - 0.071 * rgb.b
);
}*/
/*vec3 yCbCrTosRgb( vec3 yCbCr ) {
return vec3(
1.164 * yCbCr.x + 1.596 * yCbCr.z,
1.164 * yCbCr.x - 0.392 * yCbCr.y - 0.813 * yCbCr.z,
1.164 * yCbCr.x + 2.017 * yCbCr.y
);
}*/
/*
struct ColorTransform {
    float hue;
    float saturation;
    float luminance;
};
*/
/*ColorTransform getColorTransform( vec3 sRgb ) {
vec3 yCbCr = sRgbToYCbCr(sRgb);
ColorTransform ct;
vec2 dir = normalize(yCbCr.yz);
ct.hue = atan(dir.y, dir.x );
ct.luminance = yCbCr.x;
ct.saturation = length(yCbCr.yz);
return ct;
}*/
/*vec3 applyColorTransform( ColorTransform ct ) {
float lum = max(ct.luminance, 0.0);
vec2 hue = normalize(vec2( cos(ct.hue), sin(ct.hue) ));
float maxSat = 1.0/max(max(abs(hue.x), abs(hue.y)), 0.001);
float sat = clamp(ct.saturation, 0.0, maxSat);
vec3 yCbCr = vec3( lum, sat*hue );
return yCbCrTosRgb(yCbCr);
}*/
/*vec2 getMotionVector(vec4 curProj, vec4 prevProj) {
vec2 v = (curProj.xy / curProj.w) - (prevProj.xy / prevProj.w);
v.x *= -1.0;
return v;
}*/
/*mat3 getRGBToCIEXYZTransform() {
return mat3(
0.4124564, 0.3575761, 0.1804375,
0.2126729, 0.7151522, 0.0721750,
0.0193339, 0.1191920, 0.9503041
);
}*/
/*mat3 getCIEXYZToRGBTransform() {
return mat3(
3.2404542, -1.5371385, -0.4985314,
-0.9692660, 1.8760108, 0.0415560,
0.0556434, -0.2040259, 1.0572252
);
}*/
/*vec3 RGBToxyY(vec3 rgb) {
mat3 rgbToCIEXYZ = getRGBToCIEXYZTransform();
vec3 cieXYZ = ( (rgbToCIEXYZ) * (rgb) );
return vec3(
cieXYZ.x / (cieXYZ.x + cieXYZ.y + cieXYZ.z + 0.000001),
cieXYZ.y / (cieXYZ.x + cieXYZ.y + cieXYZ.z + 0.000001),
cieXYZ.y
);
}*/
/*vec3 xyYToRGB(vec3 xyY) {
vec3 cieXYZ = vec3(
(xyY.x * xyY.z) / xyY.y,
xyY.z,
((1.0 - xyY.x - xyY.y) * xyY.z) / xyY.y
);
mat3 rgbToCIEXYZInv = getCIEXYZToRGBTransform();
return ( (rgbToCIEXYZInv) * (cieXYZ) );
}*/
/*float ev100ToLuminance(float ev100) {
return 0.125f * exp2(ev100);
}*/
/*float luminanceToEV100(float luminance) {
return log2(luminance) + 3.0f;
}*/

vec4 projToView(vec4 p, mat4 inverseProj) {
#if BGFX_SHADER_LANGUAGE_GLSL
    p = vec4(
        p.x * inverseProj[0][0],
        p.y * inverseProj[1][1],
        p.w * inverseProj[3][2],
        p.z * inverseProj[2][3] + p.w * inverseProj[3][3]);
#else
    p = vec4(
        p.x * inverseProj[0][0],
        p.y * inverseProj[1][1],
        p.w * inverseProj[2][3],
        p.z * inverseProj[3][2] + p.w * inverseProj[3][3]);
#endif
    p /= p.w;
    return p;
}

vec2 octWrap(vec2 v) {
    return (1.0 - abs(v.yx)) * ((2.0 * step(0.0, v)) - 1.0);
}

/*vec2 ndirToOctSnorm(vec3 n) {
vec2 p = n.xy * (1.0 / (abs(n.x) + abs(n.y) + abs(n.z)));
p = (n.z < 0.0) ? octWrap(p) : p;
return p;
}*/
/*vec2 ndirToOctUnorm(vec3 n) {
vec2 p = ndirToOctSnorm(n);
return p * 0.5f + 0.5f;
}*/

vec3 octToNdirSnorm(vec2 p) {
    vec3 n = vec3(p.xy, 1.0 - abs(p.x) - abs(p.y));
    n.xy = (n.z < 0.0) ? octWrap(n.xy) : n.xy;
    return normalize(n);
}

/*vec3 octToNdirUnorm(vec2 p) {
vec2 pSnorm = p * 2.0f - 1.0f;
return octToNdirSnorm(pSnorm);
}*/

float D_GGX_TrowbridgeReitz(vec3 N, vec3 H, float a) {
    float a2 = a * a;
    float nDotH = max(dot(N, H), 0.0);
    float f = max((a2 - 1.0) * nDotH * nDotH + 1.0, 1e-4);
    return a2 / (f * f * 3.1415926535897932384626433832795);
}

float G_Smith(float nDotL, float nDotV, float a) {
    float k = a / 2.0;
    float viewTerm = nDotV / (nDotV * (1.0 - k) + k + 1e-4);
    float lightTerm = nDotL / (nDotL * (1.0 - k) + k + 1e-4);
    return viewTerm * lightTerm;
}

vec3 F_Schlick(vec3 V, vec3 H, vec3 R0) {
    float vDotH = max(dot(V, H), 0.0);
    return R0 + (1.0 - R0) * pow(clamp(1.0 - vDotH, 0.0, 1.0), 5.0);
}

vec3 BRDF_Spec_CookTorrance(float nDotL, float nDotV, float D, float G, vec3 F) {
    return (F * D * G) / (4.0 * nDotL * nDotV + 1e-4);
}

vec3 BRDF_Diff_Lambertian(vec3 albedo) {
    return albedo / 3.1415926535897932384626433832795;
}

float getClusterDepthIndex(float viewSpaceDepth, float maxSlices, vec2 clusterNearFar) {
    float zNear = clusterNearFar.x;
    float zFar = clusterNearFar.y;
    float nearFarLog = log(zFar / zNear);
    return floor(log(viewSpaceDepth) * (maxSlices / nearFarLog) - ((maxSlices * log(zNear)) / nearFarLog));
}

/*float getClusterDepthByIndex(float index, float maxSlices, vec2 clusterNearFar) {
float zNear = clusterNearFar.x;
float zFar = clusterNearFar.y;
float nearFarLog = log(zFar / zNear);
float logDepth = nearFarLog * index / maxSlices + log(zNear);
return exp(logDepth);
}*/

vec3 getClusterIndex(vec2 uv, float viewSpaceDepth, vec3 clusterDimensions, vec2 clusterNearFar, vec2 screenSize, vec2 clusterSize) {
#if !BGFX_SHADER_LANGUAGE_GLSL
    uv.y = 1.0 - uv.y;
#endif
    float viewportX = uv.x * screenSize.x;
    float viewportY = uv.y * screenSize.y;
    float clusterIdxX = floor(viewportX / clusterSize.x);
    float clusterIdxY = floor(viewportY / clusterSize.y);
    float clusterIdxZ = getClusterDepthIndex(viewSpaceDepth, clusterDimensions.z, clusterNearFar);
    return vec3(clusterIdxX, clusterIdxY, clusterIdxZ);
}

struct ShadowParameters {
    vec4 cascadeShadowResolutions;
    vec4 shadowBias;
    vec4 shadowSlopeBias;
    vec4 shadowPCFWidth;
    int cloudshadowsEnabled;
    float cloudshadowContribution;
    float cloudshadowPCFWidth;
    vec4 shadowParams;
    mat4 cloudShadowProj;
};

struct DirectionalLightParams {
    mat4 shadowProj[4];
    int cascadeCount;
    int isSun;
    int index;
};

ShadowParameters createShadowParams(
    vec4 cascadeShadowResolutions,
    vec4 shadowBias,
    vec4 shadowSlopeBias,
    vec4 shadowPCFWidth,
    int cloudshadowsEnabled,
    float cloudshadowContribution,
    float cloudshadowPCFWidth,
    vec4 shadowParams,
    mat4 cloudShadowProj
) {
    ShadowParameters params;
    params.cascadeShadowResolutions = cascadeShadowResolutions;
    params.shadowBias = shadowBias;
    params.shadowSlopeBias = shadowSlopeBias;
    params.shadowPCFWidth = shadowPCFWidth;
    params.cloudshadowsEnabled = cloudshadowsEnabled;
    params.cloudshadowContribution = cloudshadowContribution;
    params.cloudshadowPCFWidth = cloudshadowPCFWidth;
    params.shadowParams = shadowParams;
    params.cloudShadowProj = cloudShadowProj;
    return params;
}

/*bool isShadowedByLight(vec3 worldPos, vec3 geoNormal, vec3 lightWorldDir, float tMin, float tMax) {
return false;
}*/
/*bool areShadowsEnabled(float enabled) {
return enabled != 0.0;
}*/

bool areCascadedShadowsEnabled(float mode) {
    return int(mode) == 1;
}

int GetShadowCascade(DirectionalLightParams params, vec3 worldPos, out vec4 projPos) {
    for (int c = 0; c < params.cascadeCount; ++c) {
        mat4 proj = params.shadowProj[c];
        projPos = mul(proj, vec4(worldPos, 1.0));
        projPos /= projPos.w;
        vec3 posDiff = clamp(projPos.xyz, vec3(-1.0, -1.0, -1.0), vec3(1.0, 1.0, 1.0)) - projPos.xyz;
        if (length(posDiff) == 0.0) {
            return c;
        }
    }
    return -1;
}

/*vec3 DebugShadowCascade(DirectionalLightParams params, vec3 worldPos) {
vec4 projPos;
int cascade = GetShadowCascade(params, worldPos, projPos);
vec3 cascadeColor[4] = vec3[4](
vec3(1,0,0),
vec3(0,1,0),
vec3(0,0,1),
vec3(1,1,0)
);
if (cascade != -1) {
return cascadeColor[cascade];
}
else {
return vec3(1.f,1.f,1.f);
}
}*/

float GetFilteredCloudShadow(ShadowParameters params, vec3 worldPos, float NdL) {
    int cloudCascade = 0;
    vec4 cloudProjPos = mul(params.cloudShadowProj, vec4(worldPos, 1.0));
    cloudProjPos /= cloudProjPos.w;
#if !BGFX_SHADER_LANGUAGE_GLSL
    cloudProjPos.y *= -1.0;
#endif
    float bias = params.shadowBias[cloudCascade] + params.shadowSlopeBias[cloudCascade] * clamp(tan(acos(NdL)), 0.0, 1.0);
    cloudProjPos.z -= bias / cloudProjPos.w;
    vec2 cloudUv = vec2(cloudProjPos.x, cloudProjPos.y) * 0.5f + 0.5f;
    const int MaxFilterWidth = 9;
    int filterWidth = clamp(int(params.cloudshadowPCFWidth + 0.5f), 1, MaxFilterWidth);
    int filterOffset = filterWidth / 2;
    float amt = 0.f;
#if BGFX_SHADER_LANGUAGE_GLSL
    cloudProjPos.z = cloudProjPos.z * 0.5 + 0.5;
#endif
    for (int iy = 0; iy < filterWidth; ++iy) {
        for (int ix = 0; ix < filterWidth; ++ix) {
            float y = float(iy - filterOffset) + 0.5f;
            float x = float(ix - filterOffset) + 0.5f;
            vec2 offset = vec2(x, y) * params.shadowParams.x;
            amt += shadow2D(s_CloudShadow, vec3((cloudUv + offset) * params.cascadeShadowResolutions[0], cloudProjPos.z));
        }
    }
    return amt / float(filterWidth * filterWidth);
}

float GetFilteredShadow(ShadowParameters params, int cascadeIndex, float projZ, int cascade, vec2 uv) {
    const int MaxFilterWidth = 9;
    int filterWidth = clamp(int(params.shadowPCFWidth[cascade] + 0.5), 1, MaxFilterWidth);
    int filterOffset = filterWidth / 2;
    float amt = 0.f;
#if BGFX_SHADER_LANGUAGE_GLSL
    projZ = projZ * 0.5 + 0.5;
#endif
    for (int iy = 0; iy < filterWidth && iy < MaxFilterWidth; ++iy) {
        for (int ix = 0; ix < filterWidth && ix < MaxFilterWidth; ++ix) {
            float y = float(iy - filterOffset) + 0.5f;
            float x = float(ix - filterOffset) + 0.5f;
            vec2 offset = vec2(x, y) * params.shadowParams.x;
            switch (cascadeIndex) {
                case 0:
                    amt += shadow2DArray(s_ShadowCascades0, vec4((uv + offset) * params.cascadeShadowResolutions[cascade], float(cascade), projZ));
                    break;
                case 1:
                    amt += shadow2DArray(s_ShadowCascades1, vec4((uv + offset) * params.cascadeShadowResolutions[cascade], float(cascade), projZ));
                    break;
                default:
                    return 1.0;
            }
        }
    }
    return amt / float(filterWidth * filterWidth);
}

float GetShadowAmount(ShadowParameters params, DirectionalLightParams light, vec3 worldPos, float NdL, float viewDepth) {
    float amt = 1.0;
    float cloudAmt = 1.0;
    vec4 projPos;
    int cascade = GetShadowCascade(light, worldPos, projPos);
    if (cascade != -1) {
#if !BGFX_SHADER_LANGUAGE_GLSL
        projPos.y *= -1.0;
#endif
        float bias = params.shadowBias[light.index] + params.shadowSlopeBias[light.index] * clamp(tan(acos(NdL)), 0.0, 1.0);
        projPos.z -= bias / projPos.w;
        vec2 uv = vec2(projPos.x, projPos.y) * 0.5f + 0.5f;
        amt = GetFilteredShadow(params, light.index, projPos.z, cascade, uv);
        if (light.isSun > 0 && params.cloudshadowsEnabled > 0) {
            cloudAmt = GetFilteredCloudShadow(params, worldPos, NdL);
            if (cloudAmt < 1.0) {
                cloudAmt = max(cloudAmt, 1.0 - params.cloudshadowContribution);
                amt = min(amt, cloudAmt);
            }
        }
        float shadowRange = params.shadowParams.y;
        float shadowFade = smoothstep(max(0.0, shadowRange - 8.0), shadowRange, -viewDepth);
        amt = mix(amt, 1.0, shadowFade);
    }
    return amt;
}

/*float calculateFogIntensityVanilla(float cameraDepth, float maxDistance, float fogStart, float fogEnd) {
float distance = cameraDepth / maxDistance;
return clamp((distance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);
}*/

float calculateFogIntensityFadedVanilla(float cameraDepth, float maxDistance, float fogStart, float fogEnd, float fogAlpha) {
    float distance = cameraDepth / maxDistance;
    distance += fogAlpha;
    return clamp((distance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);
}

vec3 applyFogVanilla(vec3 diffuse, vec3 fogColor, float fogIntensity) {
    return mix(diffuse, fogColor, fogIntensity);
}

/*float calculateFogIntensity(float cameraDepth, float fogStart, float fogEndMinusStartReciprocal) {
return clamp((cameraDepth - fogStart) * fogEndMinusStartReciprocal, 0.0, 1.0);
}*/

float calculateFogIntensityFaded(float cameraDepth, float maxDistance, float fogStart, float fogEndMinusStartReciprocal, float fogAlpha) {
    float distance = cameraDepth / maxDistance;
    distance += fogAlpha;
    return clamp((distance - fogStart) * fogEndMinusStartReciprocal, 0.0, 1.0);
}

vec3 applyFog(vec3 diffuse, vec3 fogColor, float fogIntensity) {
    return mix(diffuse, fogColor, fogIntensity);
}

float getHorizonBlend(float start, float end, float x) {
    float horizon = smoothstep(start, end, x);
    horizon = horizon * horizon * horizon;
    return clamp(horizon, 0.0, 1.0);
}

float getRayleighContribution(float VdL, float strength) {
    float rayleigh = 0.5 * (VdL + 1.0);
    return (rayleigh * rayleigh) * strength;
}

struct AtmosphereParams {
    vec3 sunDir;
    vec3 moonDir;
    vec4 sunColor;
    vec4 moonColor;
    vec3 skyZenithColor;
    vec3 skyHorizonColor;
    vec4 fogColor;
    float horizonBlendMin;
    float horizonBlendStart;
    float mieStart;
    float horizonBlendMax;
    float rayleighStrength;
    float sunMieStrength;
    float moonMieStrength;
    float sunGlareShape;
};

vec3 calculateSkyColor(AtmosphereParams params, vec3 V) {
    float startHorizon = params.horizonBlendStart;
    float endHorizon = params.horizonBlendMin - params.horizonBlendMax;
    float mieStartHorizon = params.mieStart - params.horizonBlendMax;
    vec3 sunColor = params.sunColor.a * params.sunColor.rgb;
    vec3 moonColor = params.moonColor.a * params.moonColor.rgb;
    float horizon = getHorizonBlend(startHorizon, endHorizon, V.y);
    float mieHorizon = getHorizonBlend(mieStartHorizon, endHorizon, V.y);
    vec3 zenithColor = params.skyZenithColor;
    vec3 horizonColor = params.skyHorizonColor;
    float sunVdL = dot(V, params.sunDir);
    float moonVdL = dot(V, params.moonDir);
    float sunRay = getRayleighContribution(sunVdL, params.sunColor.a);
    float moonRay = getRayleighContribution(moonVdL, params.moonColor.a);
    const float kThreeOverSixteenPi = (3.0 / (16.0 * 3.1415926535897932384626433832795));
    vec3 rayleighColor = params.rayleighStrength * mix(zenithColor, horizonColor, horizon);
    vec3 rayleigh = rayleighColor * kThreeOverSixteenPi * (sunRay + moonRay);
    float miePower = params.sunGlareShape;
    float mieSunVdL = clamp(pow(max(sunVdL, 0.0), miePower), 0.0, 1.0);
    float mieMoonVdL = clamp(pow(max(moonVdL, 0.0), miePower), 0.0, 1.0);
    const float kMieEccentricity = -0.9;
    const float kTwoMieEccentricity = kMieEccentricity * 2.0;
    const float kSquaredMieEccentricity = kMieEccentricity * kMieEccentricity;
    const float kOnePlusSquaredMieEccentricity = 1.0 + kSquaredMieEccentricity;
    const float kSquaredInverseSquaredMieEccentricity = (1.0 - kSquaredMieEccentricity) * (1.0 - kSquaredMieEccentricity);
    float sunPhase = kSquaredInverseSquaredMieEccentricity / pow((kOnePlusSquaredMieEccentricity - kTwoMieEccentricity * -mieSunVdL), 1.5);
    float moonPhase = kSquaredInverseSquaredMieEccentricity / pow((kOnePlusSquaredMieEccentricity - kTwoMieEccentricity * -mieMoonVdL), 1.5);
    const float kOneOverFourPi = (1.0 / (4.0 * 3.1415926535897932384626433832795));
    vec3 mieSun = params.sunMieStrength * sunColor * mieSunVdL * sunPhase;
    vec3 mieMoon = params.moonMieStrength * moonColor * mieMoonVdL * moonPhase;
    vec3 mieColor = horizonColor * mieHorizon;
    vec3 mie = kOneOverFourPi * mieColor * (mieSun + mieMoon);
    return rayleigh + mie;
}

AtmosphereParams getAtmosphereParams() {
    AtmosphereParams params;
    params.sunDir = SunDir.xyz;
    params.moonDir = MoonDir.xyz;
    params.sunColor = SunColor;
    params.moonColor = MoonColor;
    params.skyZenithColor = SkyZenithColor.rgb;
    params.skyHorizonColor = SkyHorizonColor.rgb;
    params.fogColor = FogColor;
    params.horizonBlendMin = FogSkyBlend.x;
    params.horizonBlendStart = FogSkyBlend.y;
    params.mieStart = FogSkyBlend.z;
    params.horizonBlendMax = FogSkyBlend.w;
    params.rayleighStrength = AtmosphericScattering.x;
    params.sunMieStrength = AtmosphericScattering.y;
    params.moonMieStrength = AtmosphericScattering.z;
    params.sunGlareShape = AtmosphericScattering.w;
    return params;
}

vec3 worldSpaceViewDir(vec3 worldPosition) {
    vec3 cameraPosition = mul(u_invView, vec4(0.f, 0.f, 0.f, 1.f)).xyz;
    return normalize(worldPosition - cameraPosition);
}

float linearToLogDepth(float linearDepth) {
    return log((exp(4.0) - 1.0) * linearDepth + 1.0) / 4.0;
}

/*float logToLinearDepth(float logDepth) {
return (exp(4.0 * logDepth) - 1.0) / (exp(4.0) - 1.0);
}*/

vec3 ndcToVolume(vec3 ndc, mat4 inverseProj, vec2 nearFar) {
    vec2 uv = 0.5 * (ndc.xy + vec2(1.0, 1.0));
    vec4 view = mul(inverseProj, vec4(ndc, 1.0));
    float viewDepth = -view.z / view.w;
    float wLinear = (viewDepth - nearFar.x) / (nearFar.y - nearFar.x);
    return vec3(uv, linearToLogDepth(wLinear));
}

/*vec3 volumeToNdc(vec3 uvw, mat4 proj, vec2 nearFar) {
vec2 xy = 2.0 * uvw.xy - vec2(1.0, 1.0);
float wLinear = logToLinearDepth(uvw.z);
float viewDepth = -((1.0 - wLinear) * nearFar.x + wLinear * nearFar.y);
vec4 ndcDepth = ( (proj) * (vec4(0.0, 0.0, viewDepth, 1.0)) );
float z = ndcDepth.z / ndcDepth.w;
return vec3(xy, z);
}*/
/*vec3 worldToVolume(vec3 world, mat4 viewProj, mat4 invProj, vec2 nearFar) {
vec4 proj = ( (viewProj) * (vec4(world, 1.0)) );
vec3 ndc = proj.xyz / proj.w;
return ndcToVolume(ndc, invProj, nearFar);
}*/
/*vec3 volumeToWorld(vec3 uvw, mat4 invViewProj, mat4 proj, vec2 nearFar) {
vec3 ndc = volumeToNdc(uvw, proj, nearFar);
vec4 world = ( (invViewProj) * (vec4(ndc, 1.0)) );
return world.xyz / world.w;
}*/

vec4 sampleVolume(highp sampler2DArray volume, ivec3 dimensions, vec3 uvw) {
    float depth = uvw.z * float(dimensions.z) - 0.5;
    int index = clamp(int(depth), 0, dimensions.z - 2);
    float offset = clamp(depth - float(index), 0.0, 1.0);
    vec4 a = texture2DArray(volume, vec3(uvw.xy, index)).rgba;
    vec4 b = texture2DArray(volume, vec3(uvw.xy, index + 1)).rgba;
    return mix(a, b, offset);
}

vec3 applyScattering(vec4 sourceExtinction, vec3 color) {
    return sourceExtinction.rgb + sourceExtinction.a * color;
}

vec3 getFresnelSchlickRoughness(float cosTheta, vec3 F0, float roughness) {
    float smoothness = 1.0 - roughness;
    return F0 + (max(F0, vec3_splat(smoothness)) - F0) * pow(1.0 - cosTheta, 5.0);
}

float getMipLevel(float roughness, float numMips) {
    float x = 1.0 - roughness;
    return (1.0 - (x * x)) * (numMips - 1.0);
}

DirectionalLightParams getDirectionalLightParams(LightSourceWorldInfo light) {
    DirectionalLightParams params;
    params.shadowProj[0] = light.shadowProj0;
    params.shadowProj[1] = light.shadowProj1;
    params.shadowProj[2] = light.shadowProj2;
    params.shadowProj[3] = light.shadowProj3;
    params.cascadeCount = 4;
    params.isSun = light.isSun;
    params.index = light.shadowCascadeNumber;
    return params;
}

void BSDF_VanillaMinecraft(vec3 n, vec3 l, float nDotL, vec3 v, vec3 color, float metalness, float linearRoughness, vec3 rf0, inout vec3 diffuse, inout vec3 specular) {
    vec3 h = normalize(l + v);
    float nDotV = max(dot(n, v), 0.0);
    float roughness = linearRoughness * linearRoughness;
    float d = D_GGX_TrowbridgeReitz(n, h, roughness);
    float g = G_Smith(nDotL, nDotV, roughness);
    vec3 f = F_Schlick(v, h, rf0);
    vec3 albedo = (1.0 - f) * (1.0 - metalness) * color;
    diffuse = BRDF_Diff_Lambertian(albedo) * DiffuseSpecularEmissiveAmbientTermToggles.x;
    specular = BRDF_Spec_CookTorrance(nDotL, nDotV, d, g, f) * DiffuseSpecularEmissiveAmbientTermToggles.y;
}

void BSDF_VanillaMinecraft_DiffuseOnly(vec3 color, float metalness, inout vec3 diffuse) {
    vec3 albedo = (1.0 - metalness) * color;
    diffuse = BRDF_Diff_Lambertian(albedo) * DiffuseSpecularEmissiveAmbientTermToggles.x;
}

void BSDF_VanillaMinecraft_SpecularOnly(vec3 n, vec3 l, float nDotL, vec3 v, float metalness, float linearRoughness, vec3 rf0, inout vec3 specular) {
    vec3 h = normalize(l + v);
    float nDotV = max(dot(n, v), 0.0);
    float roughness = linearRoughness * linearRoughness;
    float d = D_GGX_TrowbridgeReitz(n, h, roughness);
    float g = G_Smith(nDotL, nDotV, roughness);
    vec3 f = F_Schlick(v, h, rf0);
    specular = BRDF_Spec_CookTorrance(nDotL, nDotV, d, g, f) * DiffuseSpecularEmissiveAmbientTermToggles.y;
}

float smoothDistanceAttenuation(float sqrDistance, float sqrRadius) {
    float ratio = sqrDistance / sqrRadius;
    float smoothFactor = clamp(1.0f - ratio * ratio, 0.0, 1.0);
    return smoothFactor * smoothFactor;
}

float getDistanceAttenuation(float sqrDistance, float radius) {
    float attenuation = 1.0f / max(sqrDistance, 0.01f * 0.01f);
    attenuation *= smoothDistanceAttenuation(sqrDistance, radius * radius);
    return attenuation;
}

vec3 findLinePlaneIntersectionForCubemap(vec3 normal, vec3 lineDirection) {
    return lineDirection * (1.f / dot(lineDirection, normal));
}

vec3 getSampleCoordinateForAdjacentFace(vec3 inCoordinate) {
    vec3 outCoordinate = inCoordinate;
    if (inCoordinate.y > 1.0) {
        switch (int(inCoordinate.z)) {
            case 0:
                outCoordinate.z = float(2);
                outCoordinate.x = 2.0 - inCoordinate.y;
                outCoordinate.y = inCoordinate.x;
                break;
            case 1:
                outCoordinate.z = float(2);
                outCoordinate.x = inCoordinate.y - 1.0f;
                outCoordinate.y = 1.0f - inCoordinate.x;
                break;
            case 2:
                outCoordinate.z = float(5);
                outCoordinate.x = 1.0f - inCoordinate.x;
                outCoordinate.y = 2.0f - inCoordinate.y;
                break;
            case 3:
                outCoordinate.z = float(4);
                outCoordinate.x = inCoordinate.x;
                outCoordinate.y = inCoordinate.y - 1.0f;
                break;
            case 4:
                outCoordinate.z = float(2);
                outCoordinate.x = inCoordinate.x;
                outCoordinate.y = inCoordinate.y - 1.0f;
                break;
            case 5:
                outCoordinate.z = float(2);
                outCoordinate.x = 1.0f - inCoordinate.x;
                outCoordinate.y = 2.0f - inCoordinate.y;
                break;
            default:
                break;
        }
    } else if (inCoordinate.y < 0.0) {
        switch (int(inCoordinate.z)) {
            case 0:
                outCoordinate.z = float(3);
                outCoordinate.x = 1.0f + inCoordinate.y;
                outCoordinate.y = 1.0f - inCoordinate.x;
                break;
            case 1:
                outCoordinate.z = float(3);
                outCoordinate.x = -inCoordinate.y;
                outCoordinate.y = inCoordinate.x;
                break;
            case 2:
                outCoordinate.z = float(4);
                outCoordinate.x = inCoordinate.x;
                outCoordinate.y = 1.0f + inCoordinate.y;
                break;
            case 3:
                outCoordinate.z = float(5);
                outCoordinate.x = 1.0f - inCoordinate.x;
                outCoordinate.y = -inCoordinate.y;
                break;
            case 4:
                outCoordinate.z = float(3);
                outCoordinate.x = inCoordinate.x;
                outCoordinate.y = 1.0 + inCoordinate.y;
                break;
            case 5:
                outCoordinate.z = float(3);
                outCoordinate.x = 1.0f - inCoordinate.x;
                outCoordinate.y = -inCoordinate.y;
                break;
            default:
                break;
        }
    }
    vec2 uvCache = outCoordinate.xy;
    if (uvCache.x > 1.0) {
        switch (int(outCoordinate.z)) {
            case 0:
                outCoordinate.z = float(5);
                outCoordinate.x = uvCache.x - 1.0f;
                outCoordinate.y = uvCache.y;
                break;
            case 1:
                outCoordinate.z = float(4);
                outCoordinate.x = uvCache.x - 1.0f;
                outCoordinate.y = uvCache.y;
                break;
            case 2:
                outCoordinate.z = float(0);
                outCoordinate.x = uvCache.y;
                outCoordinate.y = 2.0f - uvCache.x;
                break;
            case 3:
                outCoordinate.z = float(0);
                outCoordinate.x = 1.0f - uvCache.y;
                outCoordinate.y = uvCache.x - 1.0f;
                break;
            case 4:
                outCoordinate.z = float(0);
                outCoordinate.x = uvCache.x - 1.0f;
                outCoordinate.y = uvCache.y;
                break;
            case 5:
                outCoordinate.z = float(1);
                outCoordinate.x = uvCache.x - 1.0f;
                outCoordinate.y = uvCache.y;
                break;
            default:
                break;
        }
    } else if (uvCache.x < 0.0) {
        switch (int(outCoordinate.z)) {
            case 0:
                outCoordinate.z = float(4);
                outCoordinate.x = 1.0f + uvCache.x;
                outCoordinate.y = uvCache.y;
                break;
            case 1:
                outCoordinate.z = float(5);
                outCoordinate.x = 1.0f + uvCache.x;
                outCoordinate.y = uvCache.y;
                break;
            case 2:
                outCoordinate.z = float(1);
                outCoordinate.x = 1.0f - uvCache.y;
                outCoordinate.y = 1.0f + uvCache.x;
                break;
            case 3:
                outCoordinate.z = float(1);
                outCoordinate.x = uvCache.y;
                outCoordinate.y = -uvCache.x;
                break;
            case 4:
                outCoordinate.z = float(1);
                outCoordinate.x = 1.0f + uvCache.x;
                outCoordinate.y = uvCache.y;
                break;
            case 5:
                outCoordinate.z = float(0);
                outCoordinate.x = 1.0f + uvCache.x;
                outCoordinate.y = uvCache.y;
                break;
            default:
                break;
        }
    }
    return outCoordinate;
}

float calculateDirectOcclusionForDiscreteLight(int lightIndex, vec3 surfaceWorldPos, vec3 surfaceWorldNormal) {
    Light lightInfo = s_Lights[lightIndex];
    vec3 lightToPoint = surfaceWorldPos - lightInfo.position.xyz;
    vec3 surfaceViewPosition = abs(lightToPoint);
    vec3 sampleCoordinates = vec3_splat(0.f);
    if (surfaceViewPosition.x >= surfaceViewPosition.y && surfaceViewPosition.x >= surfaceViewPosition.z) {
        surfaceViewPosition = vec3(surfaceViewPosition.y, surfaceViewPosition.z, surfaceViewPosition.x);
        if (lightToPoint.x > 0.f) {
            vec3 intersection = findLinePlaneIntersectionForCubemap(vec3(1.f, 0.f, 0.f), lightToPoint);
            intersection = intersection * 0.5f + 0.5f;
            sampleCoordinates = vec3(1.f - intersection.z, intersection.y, float(0));
        } else {
            vec3 intersection = findLinePlaneIntersectionForCubemap(vec3(-1.f, 0.f, 0.f), lightToPoint);
            intersection = intersection * 0.5f + 0.5f;
            sampleCoordinates = vec3(intersection.z, intersection.y, float(1));
        }
    } else if (surfaceViewPosition.y >= surfaceViewPosition.z) {
        surfaceViewPosition = vec3(surfaceViewPosition.x, surfaceViewPosition.z, surfaceViewPosition.y);
        if (lightToPoint.y > 0.f) {
            vec3 intersection = findLinePlaneIntersectionForCubemap(vec3(0.f, 1.f, 0.f), lightToPoint);
            intersection = intersection * 0.5f + 0.5f;
            sampleCoordinates = vec3(intersection.x, 1.f - intersection.z, float(2));
        } else {
            vec3 intersection = findLinePlaneIntersectionForCubemap(vec3(0.f, -1.f, 0.f), lightToPoint);
            intersection = intersection * 0.5f + 0.5f;
            sampleCoordinates = vec3(intersection.x, intersection.z, float(3));
        }
    } else {
        if (lightToPoint.z > 0.f) {
            vec3 intersection = findLinePlaneIntersectionForCubemap(vec3(0.f, 0.f, 1.f), lightToPoint);
            intersection = intersection * 0.5f + 0.5f;
            sampleCoordinates = vec3(intersection.x, intersection.y, float(4));
        } else {
            vec3 intersection = findLinePlaneIntersectionForCubemap(vec3(0.f, 0.f, -1.f), lightToPoint);
            intersection = intersection * 0.5f + 0.5f;
            sampleCoordinates = vec3(1.f - intersection.x, intersection.y, float(5));
        }
    }
    surfaceViewPosition.z *= -1.f;
    vec4 surfaceProjPos = mul(PointLightProj, (vec4(surfaceViewPosition, 1.0)));
    float NdL = dot(-normalize(lightToPoint), surfaceWorldNormal);
    float bias = PointLightShadowParams1.x + PointLightShadowParams1.y * clamp(tan(acos(NdL)), 0.0, 1.0);
    surfaceProjPos.z += bias;
    surfaceProjPos /= surfaceProjPos.w;
    int filterWidth = 4;
    int filterOffset = filterWidth / 2;
    float directOcclusion = 0.f;
    for (int iy = 0; iy < filterWidth; ++iy) {
        for (int ix = 0; ix < filterWidth; ++ix) {
            float y = float(iy - filterOffset) + 0.5f;
            float x = float(ix - filterOffset) + 0.5f;
            vec2 offset = vec2(x, y) * PointLightShadowParams1.w;
            vec3 offsetSampleCoordinates = getSampleCoordinateForAdjacentFace(sampleCoordinates + vec3(offset.x, offset.y, 0.0f));
            offsetSampleCoordinates.z = float(lightIndex * 6) + offsetSampleCoordinates.z;
            directOcclusion += shadow2DArray(s_PointLightShadowTextureArray, vec4(offsetSampleCoordinates, surfaceProjPos.z));
        }
    }
    return directOcclusion / float(filterWidth * filterWidth);
}

void evaluateDiscreteLightsDirectContribution(inout PBRLightingContributions lightContrib, vec2 lightClusterUV, vec3 surfacePos, vec3 n, vec3 v, vec3 color, float metalness, float linearRoughness, vec3 rf0, vec3 surfaceWorldPos, vec3 surfaceWorldNormal, bool calculateDiffuse, bool calculateSpecular) {
    if (!(calculateSpecular || calculateDiffuse)) {
        return;
    }
    vec3 clusterId = getClusterIndex(lightClusterUV, -surfacePos.z, ClusterDimensions.xyz, ClusterNearFarWidthHeight.xy, ClusterNearFarWidthHeight.zw, ClusterSize.xy);
    if (clusterId.x >= ClusterDimensions.x || clusterId.y >= ClusterDimensions.y || clusterId.z >= ClusterDimensions.z) {
        return;
    }
    highp int clusterIdx = int(clusterId.x + clusterId.y * ClusterDimensions.x + clusterId.z * ClusterDimensions.x * ClusterDimensions.y);
    highp int rangeStart = clusterIdx * int(ClusterDimensions.w);
    highp int rangeEnd = rangeStart + int(ClusterDimensions.w);
    for (highp int i = rangeStart; i < rangeEnd; ++i) {
        int lightIndex = int(s_LightLookupArray[i].lookup);
        if (lightIndex < 0) {
            break;
        }
        Light lightInfo = s_Lights[lightIndex];
        float lightGridBlending = 1.f;
        float surfaceDistanceFromCamera = length(surfacePos);
        if (surfaceDistanceFromCamera < lightInfo.gridLevelRadius && lightInfo.lowerGridLevelRadius >= 0.f) {
            lightGridBlending = (surfaceDistanceFromCamera - lightInfo.lowerGridLevelRadius) / (lightInfo.gridLevelRadius - lightInfo.lowerGridLevelRadius);
        } else if (surfaceDistanceFromCamera > lightInfo.gridLevelRadius && lightInfo.higherGridLevelRadius >= 0.f) {
            lightGridBlending = (surfaceDistanceFromCamera - lightInfo.higherGridLevelRadius) / (lightInfo.gridLevelRadius - lightInfo.higherGridLevelRadius);
        }
        if (lightGridBlending <= 0.f) {
            continue;
        }
        vec3 lightWorldDir = lightInfo.position.xyz - surfaceWorldPos.xyz;
        float squaredDistanceToLight = dot(lightWorldDir, lightWorldDir);
        float r = lightInfo.position.w;
        if (squaredDistanceToLight >= r * r) {
            continue;
        }
        float directOcclusion = 1.0f;
        if (int(PointLightShadowParams1.z) > lightIndex) {
            directOcclusion = calculateDirectOcclusionForDiscreteLight(lightIndex, surfaceWorldPos, surfaceWorldNormal);
            if (directOcclusion <= 0.0f) {
                continue;
            }
        }
        vec3 lightPos = mul(u_view, (vec4(lightInfo.position.xyz, 1.0))).xyz;
        vec3 lightDir = lightPos - surfacePos;
        vec3 l = normalize(lightDir);
        float nDotl = max(dot(n, l), 0.0);
        float lightIntensity = lightInfo.color.a;
        float attenuation = lightIntensity * getDistanceAttenuation(squaredDistanceToLight, r);
        vec3 lightColor = lightInfo.color.rgb;
        vec3 illuminance = lightColor * attenuation * nDotl;
        vec3 diffuse = vec3_splat(0.0);
        vec3 specular = vec3_splat(0.0);
        if (calculateDiffuse) {
            if (calculateSpecular) {
                BSDF_VanillaMinecraft(n, l, nDotl, v, color, metalness, linearRoughness, rf0, diffuse, specular);
            } else {
                BSDF_VanillaMinecraft_DiffuseOnly(color, metalness, diffuse);
            }
        } else {
            if (calculateSpecular) {
                BSDF_VanillaMinecraft_SpecularOnly(n, l, nDotl, v, metalness, linearRoughness, rf0, specular);
            }
        }
        lightContrib.directDiffuse += diffuse * directOcclusion * illuminance * DirectionalShadowModeAndCloudShadowToggleAndPointLightToggleAndShadowToggle.z * lightGridBlending;
        lightContrib.directSpecular += specular * directOcclusion * illuminance * DirectionalShadowModeAndCloudShadowToggleAndPointLightToggleAndShadowToggle.z * lightGridBlending;
    }
}

void evaluateDirectionalLightsDirectContribution(inout PBRLightingContributions lightContrib, float viewDepth, vec3 n, vec3 v, vec3 color, float metalness, float linearRoughness, vec3 rf0, vec3 worldPosition, vec3 worldNormal) {
    ShadowParameters shadowParams = createShadowParams(
        CascadeShadowResolutions,
        ShadowBias,
        ShadowSlopeBias,
        ShadowPCFWidth,
        int(DirectionalShadowModeAndCloudShadowToggleAndPointLightToggleAndShadowToggle.y),
        EmissiveMultiplierAndDesaturationAndCloudPCFAndContribution.w,
        EmissiveMultiplierAndDesaturationAndCloudPCFAndContribution.z,
        ShadowParams,
        CloudShadowProj);
    int lightCount = int(DirectionalLightToggleAndCountAndMaxDistance.y);
    for (int i = 0; i < lightCount; i++) {
        float directOcclusion = 1.0;
        if (areCascadedShadowsEnabled(DirectionalShadowModeAndCloudShadowToggleAndPointLightToggleAndShadowToggle.x)) {
            vec3 sl = normalize(mul(u_view, s_DirectionalLightSources[i].shadowDirection).xyz);
            float nDotsl = max(dot(n, sl), 0.0);
            directOcclusion = GetShadowAmount(
                shadowParams,
                getDirectionalLightParams(s_DirectionalLightSources[i]),
                worldPosition,
                nDotsl,
                viewDepth);
        }
        vec3 l = normalize(mul(u_view, (s_DirectionalLightSources[i].worldSpaceDirection)).xyz);
        float nDotl = max(dot(n, l), 0.0);
        vec4 colorAndIlluminance = s_DirectionalLightSources[i].diffuseColorAndIlluminance;
        vec3 illuminance = colorAndIlluminance.rgb * colorAndIlluminance.a * nDotl;
        vec3 diffuse = vec3_splat(0.0);
        vec3 specular = vec3_splat(0.0);
        BSDF_VanillaMinecraft(n, l, nDotl, v, color, metalness, linearRoughness, rf0, diffuse, specular);
        lightContrib.directDiffuse += diffuse * directOcclusion * illuminance * DirectionalLightToggleAndCountAndMaxDistance.x;
        lightContrib.directSpecular += specular * directOcclusion * illuminance * DirectionalLightToggleAndCountAndMaxDistance.x;
    }
}

void evaluateIndirectLightingContribution(inout PBRLightingContributions lightContrib, vec3 albedo, float blockAmbientContribution, float skyAmbientContribution, float ambientFadeInMultiplier, float linearRoughness, vec3 v, vec3 n, vec3 f0) {
    vec3 sampledBlockAmbient = blockAmbientContribution * BlockBaseAmbientLightColorIntensity.rgb * BlockBaseAmbientLightColorIntensity.a * ambientFadeInMultiplier;
    vec3 sampledSkyAmbient = skyAmbientContribution * SkyAmbientLightColorIntensity.rgb * SkyAmbientLightColorIntensity.a;
    vec3 sampledAmbient = sampledBlockAmbient + sampledSkyAmbient;
    sampledAmbient = max(sampledAmbient, vec3_splat(0.03));
    lightContrib.indirectDiffuse += albedo * sampledAmbient * DiffuseSpecularEmissiveAmbientTermToggles.w;
    vec3 R = reflect(v, n);
    float nDotv = clamp(dot(n, v), 0.0, 1.0);
    float roughness = linearRoughness * linearRoughness;
    vec3 preFilteredColor = textureCubeLod(s_SpecularIBL, R, getMipLevel(roughness, IBLParameters.y)).rgb;
    vec2 envDFG = texture2D(s_BrdfMap, vec2(nDotv, 1.0 - roughness)).rg;
    vec3 F = getFresnelSchlickRoughness(nDotv, f0, roughness);
    lightContrib.indirectSpecular += preFilteredColor * (F * envDFG.x + envDFG.y) * IBLParameters.x * IBLParameters.z;
}

vec4 evaluateFragmentColor(PBRFragmentInfo fragmentInfo) {
    PBRLightingContributions lightContrib;
    lightContrib.directDiffuse = vec3_splat(0.0);
    lightContrib.directSpecular = vec3_splat(0.0);
    lightContrib.indirectDiffuse = vec3_splat(0.0);
    lightContrib.indirectSpecular = vec3_splat(0.0);
    lightContrib.emissive = vec3_splat(0.0);
    float dist = length(fragmentInfo.viewPosition);
    float distPointLightSpecularFadeOut_Begin = PointLightSpecularFadeOutParameters.x;
    float distPointLightSpecularFadeOut_End = PointLightSpecularFadeOutParameters.y;
    bool enablePointLightSpecularFade = distPointLightSpecularFadeOut_Begin > 0.0f;
    float percentOfSpecularFade = 0.0f;
    if (enablePointLightSpecularFade) {
        percentOfSpecularFade = smoothstep(distPointLightSpecularFadeOut_Begin, distPointLightSpecularFadeOut_End, dist);
    }
    float fadeOutSpecularMultiplier = 1.0f - percentOfSpecularFade;
    float distPointLightDiffuseFadeOut_Begin = PointLightDiffuseFadeOutParameters.x;
    float distPointLightDiffuseFadeOut_End = PointLightDiffuseFadeOutParameters.y;
    float ambientBlockContributionNaught = PointLightDiffuseFadeOutParameters.z;
    float ambientBlockContributionFinal = PointLightDiffuseFadeOutParameters.w;
    bool enablePointLightDiffuseFade = distPointLightDiffuseFadeOut_Begin > 0.0f;
    float percentOfDiffuseFade = 0.0f;
    float fadeInAmbient = ambientBlockContributionNaught;
    if (enablePointLightDiffuseFade) {
        percentOfDiffuseFade = smoothstep(distPointLightDiffuseFadeOut_Begin, distPointLightDiffuseFadeOut_End, dist);
        fadeInAmbient += ((ambientBlockContributionFinal - ambientBlockContributionNaught) * percentOfDiffuseFade);
    }
    float fadeOutDiffuseMultiplier = 1.0f - percentOfDiffuseFade;
    float reflectance = 0.5;
    float dielectricF0 = 0.16f * reflectance * reflectance * (1.0f - fragmentInfo.metalness);
    vec3 rf0 = vec3(dielectricF0, dielectricF0, dielectricF0) + fragmentInfo.albedo * fragmentInfo.metalness;
    float viewDistance = length(fragmentInfo.viewPosition);
    vec3 viewDir = -(fragmentInfo.viewPosition / viewDistance);
    vec3 viewDirWorld = worldSpaceViewDir(fragmentInfo.worldPosition.xyz);
    evaluateIndirectLightingContribution(
        lightContrib,
        fragmentInfo.albedo.rgb,
        fragmentInfo.blockAmbientContribution, fragmentInfo.skyAmbientContribution, fadeInAmbient,
        fragmentInfo.roughness, viewDirWorld, fragmentInfo.worldNormal, rf0);
    if (fragmentInfo.emissive < 0.25) {
        evaluateDirectionalLightsDirectContribution(
            lightContrib,
            fragmentInfo.viewPosition.z, fragmentInfo.viewNormal, viewDir, fragmentInfo.albedo,
            fragmentInfo.metalness, fragmentInfo.roughness, rf0, fragmentInfo.worldPosition, fragmentInfo.worldNormal);
        bool shouldCalculateSpecularTerm = (!enablePointLightSpecularFade) || (enablePointLightSpecularFade && dist < distPointLightSpecularFadeOut_End);
        bool shouldCalculateDiffuseTerm = (!enablePointLightDiffuseFade) || (enablePointLightDiffuseFade && dist < distPointLightDiffuseFadeOut_End);
        PBRLightingContributions discreteLightContrib;
        discreteLightContrib.directDiffuse = vec3_splat(0.0);
        discreteLightContrib.directSpecular = vec3_splat(0.0);
        discreteLightContrib.indirectDiffuse = vec3_splat(0.0);
        discreteLightContrib.indirectSpecular = vec3_splat(0.0);
        discreteLightContrib.emissive = vec3_splat(0.0);
        evaluateDiscreteLightsDirectContribution(
            discreteLightContrib,
            fragmentInfo.lightClusterUV, fragmentInfo.viewPosition.xyz, fragmentInfo.viewNormal, viewDir,
            fragmentInfo.albedo, fragmentInfo.metalness, fragmentInfo.roughness, rf0, fragmentInfo.worldPosition, fragmentInfo.worldNormal, shouldCalculateDiffuseTerm, shouldCalculateSpecularTerm);
        lightContrib.directDiffuse += discreteLightContrib.directDiffuse * fadeOutDiffuseMultiplier;
        lightContrib.directSpecular += discreteLightContrib.directSpecular * fadeOutSpecularMultiplier;
    } else {
        lightContrib.emissive += DiffuseSpecularEmissiveAmbientTermToggles.z * desaturate(fragmentInfo.albedo, EmissiveMultiplierAndDesaturationAndCloudPCFAndContribution.y) * vec3(fragmentInfo.emissive, fragmentInfo.emissive, fragmentInfo.emissive) * EmissiveMultiplierAndDesaturationAndCloudPCFAndContribution.x;
    }
    vec3 surfaceRadiance = lightContrib.indirectDiffuse + lightContrib.directDiffuse + lightContrib.indirectSpecular + lightContrib.directSpecular + lightContrib.emissive;
    vec3 fogAppliedColor;
    if (AtmosphericScatteringEnabled.x != 0.0) {
        float fogIntensity = calculateFogIntensityFaded(viewDistance, FogAndDistanceControl.z, FogAndDistanceControl.x, FogAndDistanceControl.y, RenderChunkFogAlpha.x);
        if (fogIntensity > 0.0) {
            vec3 skyColor = calculateSkyColor(getAtmosphereParams(), viewDirWorld);
            fogAppliedColor = applyFog(surfaceRadiance, skyColor, fogIntensity);
        } else {
            fogAppliedColor = surfaceRadiance;
        }
    } else {
        float fogIntensity = calculateFogIntensityFadedVanilla(viewDistance, FogAndDistanceControl.z, FogAndDistanceControl.x, FogAndDistanceControl.y, RenderChunkFogAlpha.x);
        fogAppliedColor = applyFogVanilla(surfaceRadiance, FogColor.rgb, fogIntensity);
    }
    vec3 outColor;
    if (VolumeScatteringEnabled.x != 0.0) {
        vec3 uvw = ndcToVolume(fragmentInfo.ndcPosition, u_invProj, VolumeNearFar.xy);
        vec4 sourceExtinction = sampleVolume(s_ScatteringBuffer, ivec3(VolumeDimensions.xyz), uvw);
        outColor = applyScattering(sourceExtinction, fogAppliedColor);
    } else {
        outColor = fogAppliedColor;
    }
    return vec4(outColor, 1.0);
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

#endif // FORWARD_PBR_TRANSPARENT

void main() {
#if FORWARD_PBR_TRANSPARENT
    vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);

    const float ALPHA_THRESHOLD = 0.5;
    if (diffuse.a < ALPHA_THRESHOLD) {
        discard;
    }

    diffuse.rgb *= v_color0.rgb;
    diffuse.a *= v_color0.a;

    //RenderChunkSurfTransparent
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

    PBRFragmentInfo fragmentData;
    vec4 viewPosition = mul(u_view, vec4(v_worldPos, 1.0));
    vec4 clipPosition = mul(u_proj, viewPosition);
    vec3 ndcPosition = clipPosition.xyz / clipPosition.w;
    vec2 uv = (ndcPosition.xy + vec2(1.0, 1.0)) / 2.0;
    vec4 worldNormal = vec4(viewSpaceNormal, 0.0);
    vec4 viewNormal = mul(u_view, worldNormal);
    fragmentData.lightClusterUV = uv;
    fragmentData.worldPosition = v_worldPos;
    fragmentData.viewPosition = viewPosition.xyz;
    fragmentData.ndcPosition = ndcPosition;
    fragmentData.worldNormal = worldNormal.xyz;
    fragmentData.viewNormal = viewNormal.xyz;
    fragmentData.albedo = diffuse.rgb;
    fragmentData.roughness = linearRoughness;
    fragmentData.metalness = metalness;
    fragmentData.emissive = emissive;
    fragmentData.blockAmbientContribution = blockLight.x;
    fragmentData.skyAmbientContribution = skyLight.x;

    gl_FragColor.rgb = evaluateFragmentColor(fragmentData).rgb;
    gl_FragColor.a = diffuse.a;

#else

    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
#endif
}
