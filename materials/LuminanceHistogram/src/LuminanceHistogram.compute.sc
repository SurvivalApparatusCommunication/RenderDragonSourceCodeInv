#include <bgfx_compute.sh>

struct Histogram {
    uint count;
};

uniform vec4 MinLogLuminance;
uniform vec4 AdaptiveParameters;
uniform vec4 DeltaTime;
uniform vec4 ScreenSize;
uniform vec4 LogLuminanceRange;
uniform vec4 EnableCustomWeight;

SAMPLER2D(s_GameColor, 0);
SAMPLER2D(s_CustomWeight, 4);
BUFFER_RW(s_CurFrameLuminanceHistogram, Histogram, 1);
IMAGE2D_RW(s_AdaptedFrameAverageLuminance, r32f, 2);
IMAGE2D_RW(s_MaxFrameLuminance, r32f, 3);

SHARED uint curFrameLuminanceHistogramShared[256];

uint luminanceToHistogramBin(float luminance) {
    if (luminance < 0.00095f) {
        return 0u;
    }
    float logLuminance = clamp((log2(luminance) - MinLogLuminance.x) * 1.0f / LogLuminanceRange.x, 0.0f, 1.0f);
    return uint(logLuminance * 254.0 + 1.0);
}

void Build(uint LocalInvocationIndex, uvec3 GlobalInvocationID) {
    uint x = GlobalInvocationID.x;
    uint y = GlobalInvocationID.y;
    curFrameLuminanceHistogramShared[LocalInvocationIndex] = 0u;
    barrier();
    if (x < uint(ScreenSize.x) && y < uint(ScreenSize.y)) {
        ivec2 pixel = ivec2(x, y);
        vec2 uv = vec2(pixel) / ScreenSize.xy;
        vec3 color = texture2DLod(s_GameColor, uv, 0.0f).rgb;
        float luminance = dot(color.rgb, vec3(0.2126f, 0.7152f, 0.0722f));
        uint index = luminanceToHistogramBin(luminance);
        atomicAdd(curFrameLuminanceHistogramShared[index], 1u);
    }
    barrier();
    atomicAdd(s_CurFrameLuminanceHistogram[LocalInvocationIndex].count, curFrameLuminanceHistogramShared[LocalInvocationIndex]);
}

void Average(uint LocalInvocationIndex) {
    uint histogramCount = s_CurFrameLuminanceHistogram[LocalInvocationIndex].count;
    if (EnableCustomWeight.x != 0.0) {
        vec2 uv = vec2((float(LocalInvocationIndex) + 0.5f) / float(256), 0.5f);
        curFrameLuminanceHistogramShared[LocalInvocationIndex] = float(histogramCount) * texture2DLod(s_CustomWeight, uv, 0.0f).r;
    } else {
        curFrameLuminanceHistogramShared[LocalInvocationIndex] = float(histogramCount) * float(LocalInvocationIndex);
    }
    barrier();
    for (uint cutoff = uint(256 >> 1); cutoff > 0u;) {
        if (uint(LocalInvocationIndex) < cutoff) {
            curFrameLuminanceHistogramShared[LocalInvocationIndex] = curFrameLuminanceHistogramShared[LocalInvocationIndex] + curFrameLuminanceHistogramShared[LocalInvocationIndex + cutoff];
        }
        barrier();
        cutoff = (cutoff >> 1u);
    }
    if (LocalInvocationIndex == 0u) {
        float weightedLogAverage = (curFrameLuminanceHistogramShared[0] / max(ScreenSize.x * ScreenSize.y - float(histogramCount), 1.0f)) - 1.0f;
        if (weightedLogAverage < 1.f) {
            weightedLogAverage = 0.0f;
        }
        float weightedAverageLuminance = exp2(((weightedLogAverage * LogLuminanceRange.x) / 254.0f) + MinLogLuminance.x);
        float prevLuminance = imageLoad(s_AdaptedFrameAverageLuminance, ivec2(0, 0)).r;
        bool isBrighter = (prevLuminance < weightedAverageLuminance);
        float speedParam = isBrighter ? AdaptiveParameters.y : AdaptiveParameters.z;
        float adaptedLuminance = prevLuminance + (weightedAverageLuminance - prevLuminance) * (1.0f - exp(-DeltaTime.x * AdaptiveParameters.x * speedParam));
        if (isBrighter) {
            adaptedLuminance = adaptedLuminance > weightedAverageLuminance ? weightedAverageLuminance : adaptedLuminance;
        } else {
            adaptedLuminance = adaptedLuminance > weightedAverageLuminance ? adaptedLuminance : weightedAverageLuminance;
        }
        imageStore(s_AdaptedFrameAverageLuminance, ivec2(0, 0), vec4(adaptedLuminance, adaptedLuminance, adaptedLuminance, adaptedLuminance));
        int maxLuminanceBin = 0;
        for (int i = 256 - 1; i > 0; i--) {
            if (float(s_CurFrameLuminanceHistogram[i].count) >= AdaptiveParameters.w) {
                maxLuminanceBin = i;
                break;
            }
        }
        vec2 uv = vec2((float(maxLuminanceBin) + 0.5f) / float(256), 0.5f);
        float maxLuminance = 0.0f;
        if (EnableCustomWeight.x != 0.0) {
            maxLuminance = exp2(((texture2DLod(s_CustomWeight, uv, 0.0f).r - 1.0f) * LogLuminanceRange.x) / 254.0f + MinLogLuminance.x);
        } else {
            maxLuminance = exp2(((float(maxLuminanceBin) - 1.0f) * LogLuminanceRange.x) / 254.0f + MinLogLuminance.x);
        }
        imageStore(s_MaxFrameLuminance, ivec2(0, 0), vec4(maxLuminance, maxLuminance, maxLuminance, maxLuminance));
    }
}

void Clean(uint LocalInvocationIndex) {
    s_CurFrameLuminanceHistogram[LocalInvocationIndex].count = 0u;
}

NUM_THREADS(16, 16, 1)
void main() {
#if BUILD_HISTOGRAM
    Build(gl_LocalInvocationIndex, gl_GlobalInvocationID);
#elif CALCULATE_AVERAGE
    Average(gl_LocalInvocationIndex);
#elif CLEAN_UP
    Clean(gl_LocalInvocationIndex);
#endif
}
