$input v_texcoord0

#include <bgfx_shader.sh>

uniform vec4 ExposureCompensation;
uniform vec4 LuminanceMinMax;
uniform vec4 RenderMode;
uniform vec4 ScreenSize;
uniform vec4 TonemapCorrection;
uniform vec4 TonemapParams0;

SAMPLER2D_AUTOREG(s_RasterColor);
SAMPLER2D_AUTOREG(s_ColorTexture);
SAMPLER2D_AUTOREG(s_LuminanceColorTexture);
SAMPLER2D_AUTOREG(s_AverageLuminance);
SAMPLER2D_AUTOREG(s_MaxLuminance);
SAMPLER2D_AUTOREG(s_RasterizedColor);
SAMPLER2D_AUTOREG(s_CustomExposureCompensation);

/*float computeWeight(float depthCenter, float depthP, float phiDepth, vec3 normalCenter, vec3 normalP, float phiNormal, float luminanceIllumCenter, float luminanceIllumP, float phiIllum) {
float weightNormal = pow(clamp(dot(normalCenter, normalP), 0.0, 1.0), phiNormal);
float weightZ = (phiDepth == 0) ? 0.0 : abs(depthCenter - depthP) / phiDepth;
float weightLillum = abs(luminanceIllumCenter - luminanceIllumP) / phiIllum;
float weightIllum = exp(0.0 - max(weightLillum, 0.0) - max(weightZ, 0.0)) * weightNormal;
return weightIllum;
}*/
/*vec2 octWrap(vec2 v) {
return (1.0 - abs(v.yx)) * ((2.0 * step(0.0, v)) - 1.0);
}*/
/*vec2 ndirToOctSnorm(vec3 n) {
vec2 p = n.xy * (1.0 / (abs(n.x) + abs(n.y) + abs(n.z)));
p = (n.z < 0.0) ? octWrap(p) : p;
return p;
}*/
/*vec2 ndirToOctUnorm(vec3 n) {
vec2 p = ndirToOctSnorm(n);
return p * 0.5f + 0.5f;
}*/
/*vec3 octToNdirSnorm(vec2 p) {
vec3 n = vec3(p.xy, 1.0 - abs(p.x) - abs(p.y));
n.xy = (n.z < 0.0) ? octWrap(n.xy) : n.xy;
return normalize(n);
}*/
/*vec3 octToNdirUnorm(vec2 p) {
vec2 pSnorm = p * 2.0f - 1.0f;
return octToNdirSnorm(pSnorm);
}*/
/*void Vert(VertexInput vertInput, inout VertexOutput vertOutput) {
vertOutput.position = vec4(vertInput.position.xy * 2.0 - 1.0, 0.0, 1.0);
vertOutput.texcoord0 = vec2(vertInput.texcoord0.x, vertInput.texcoord0.y);
}*/
vec3 color_gamma(vec3 clr) {
    float e = 1.0 / 2.2;
    return pow(max(clr, vec3(0.0, 0.0, 0.0)), vec3(e, e, e));
}
vec4 color_gamma(vec4 clr) {
    return vec4(color_gamma(clr.rgb), clr.a);
}
/*vec3 color_degamma(vec3 clr) {
float e = 2.2;
return pow(max(clr, vec3(0.0, 0.0, 0.0)), vec3(e, e, e));
}*/
/*vec4 color_degamma(vec4 clr) {
return vec4(color_degamma(clr.rgb), clr.a);
}*/
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
/*vec3 desaturate(vec3 color, float amount)
{
float lum = luminance(color);
return mix(color, vec3(lum, lum, lum), amount);
}*/
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
struct ColorTransform {
    float hue;
    float saturation;
    float luminance;
};
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
float luminanceToEV100(float luminance) {
    return log2(luminance) + 3.0f;
}
vec3 TonemapReinhard(vec3 rgb, float W) {
    vec3 color = rgb / (1.0 + rgb);
    return color;
}
vec3 TonemapReinhardLuminance(vec3 rgb, float W) {
    float l_old = luminance(rgb);
    float l_new = (l_old * (1.0 + (l_old / W))) / (1.0 + l_old);
    return rgb * (l_new / l_old);
}
vec3 TonemapReinhardJodie(vec3 rgb) {
    float l = luminance(rgb);
    vec3 tc = rgb / (1.0 + rgb);
    return mix(rgb / (1.0 + l), tc, tc);
}
vec3 Uncharted2Tonemap(vec3 x) {
    float A = 0.15;
    float B = 0.50;
    float C = 0.10;
    float D = 0.20;
    float E = 0.02;
    float F = 0.30;
    return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
}
vec3 TonemapUncharted2(vec3 rgb, float W) {
    const float ExposureBias = 2.0;
    vec3 curr = Uncharted2Tonemap(ExposureBias * rgb);
    vec3 whiteScale = 1.0 / Uncharted2Tonemap(vec3_splat(W));
    return curr * whiteScale;
}
vec3 RRTAndODTFit(vec3 v) {
    vec3 a = v * (v + 0.0245786) - 0.000090537;
    vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
    return a / b;
}
vec3 ACESFitted(vec3 rgb) {
    const mat3 ACESInputMat = mat3(
        0.59719, 0.35458, 0.04823,
        0.07600, 0.90834, 0.01566,
        0.02840, 0.13383, 0.83777);
    const mat3 ACESOutputMat = mat3(
        1.60475, -0.53108, -0.07367,
        -0.10208, 1.10813, -0.00605,
        -0.00327, -0.07276, 1.07602);
    rgb = mul(ACESInputMat, rgb);
    rgb = RRTAndODTFit(rgb);
    rgb = mul(ACESOutputMat, rgb);
    rgb = clamp(rgb, 0.0, 1.0);
    return rgb;
}
vec3 TonemapACES(vec3 rgb) {
    return ACESFitted(rgb);
}
vec3 TonemapColorCorrection(vec3 rgb, float luminance, float brightness, float contrast, float saturation) {
    rgb = (rgb - 0.5) * contrast + 0.5 + brightness;
    return mix(vec3_splat(luminance), rgb, max(0.0, saturation));
}
vec3 ApplyTonemap(vec3 bloomedHDRi, vec3 sceneHDRi, float averageLuminance, float brightness, float contrast, float saturation, float compensation, float whitePoint, int tonemapper) {
    float exposure = (0.18f / averageLuminance) * compensation;
    bloomedHDRi *= exposure;
    float scaledWhitePoint = exposure * whitePoint;
    float whitePointSquared = scaledWhitePoint * scaledWhitePoint;
    if (tonemapper == 1) {
        bloomedHDRi = TonemapReinhardLuminance(bloomedHDRi, whitePointSquared);
    } else if (tonemapper == 2) {
        bloomedHDRi = TonemapReinhardJodie(bloomedHDRi);
    } else if (tonemapper == 3) {
        bloomedHDRi = TonemapUncharted2(bloomedHDRi, whitePointSquared);
    } else if (tonemapper == 4) {
        bloomedHDRi = TonemapACES(bloomedHDRi);
    } else {
        bloomedHDRi = TonemapReinhard(bloomedHDRi, whitePointSquared);
    }
    float finalLuminance = luminance(bloomedHDRi);
    bloomedHDRi = color_gamma(bloomedHDRi);
    return TonemapColorCorrection(bloomedHDRi, finalLuminance, brightness, contrast, saturation);
}

void main() {
    vec3 bloomedHDRi = texture2D(s_ColorTexture, v_texcoord0).rgb;
    vec3 sceneHDRi = texture2D(s_LuminanceColorTexture, v_texcoord0).rgb;
    vec3 finalColor = bloomedHDRi;
    if (TonemapParams0.y <= 0.5f) {
        finalColor.rgb = color_gamma(bloomedHDRi.rgb);
    } else {
        float averageLuminance = clamp(texture2D(s_AverageLuminance, vec2(0.5f, 0.5f)).r, LuminanceMinMax.x, LuminanceMinMax.y);
        float compensation = ExposureCompensation.y;
        int exposureCurveType = int(ExposureCompensation.x);
        if (exposureCurveType > 0 && exposureCurveType < 2) {
            compensation = 1.03f - 2.0f / ((1.0f / log(10.0f)) * log(averageLuminance + 1.0f) + 2.0f);
        } else if (exposureCurveType > 1) {
            vec2 uv = vec2(LuminanceMinMax.x == LuminanceMinMax.y ? 0.5f : (luminanceToEV100(averageLuminance) - luminanceToEV100(LuminanceMinMax.x)) / (luminanceToEV100(LuminanceMinMax.y) - luminanceToEV100(LuminanceMinMax.x)), 0.5f);
            compensation = texture2D(s_CustomExposureCompensation, uv).r;
        }
        float whitePoint = texture2D(s_MaxLuminance, vec2(0.5f, 0.5f)).r;
        whitePoint = whitePoint < TonemapCorrection.w ? TonemapCorrection.w : whitePoint;
        finalColor.rgb = ApplyTonemap(
            bloomedHDRi,
            sceneHDRi,
            averageLuminance,
            TonemapCorrection.x,
            TonemapCorrection.y,
            TonemapCorrection.z,
            compensation,
            whitePoint,
            int(TonemapParams0.x));
    }
    finalColor.rgb = clamp(finalColor.rgb, vec3(0.0, 0.0, 0.0), vec3(1.0, 1.0, 1.0));
    vec4 rasterized = texture2D(s_RasterizedColor, v_texcoord0);
    finalColor.rgb *= 1.0 - rasterized.a;
    finalColor.rgb += rasterized.rgb;

    gl_FragColor = vec4(finalColor.rgb, 1.0);
}
