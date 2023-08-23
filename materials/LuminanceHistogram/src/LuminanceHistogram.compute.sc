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


#if BUILD_HISTOGRAM
    #include "BuildHistogram.sc"
#elif CALCULATE_AVERAGE
    #include "CalculateAverage.sc"
#elif CLEAN_UP
    #include "CleanUp.sc"
#endif
