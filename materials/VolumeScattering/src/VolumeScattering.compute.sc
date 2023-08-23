#include <bgfx_compute.sh>

uniform vec4 ClearValue;
uniform vec4 VolumeNearFar;
uniform vec4 VolumeDimensions;

IMAGE2D_ARRAY_RO(s_ScatteringBufferIn,  rgba16f, 0);
IMAGE2D_ARRAY_WR(s_ScatteringBufferOut, rgba16f, 1);


#if SCATTERING
    #include "Scattering.sc"
#elif CLEAR
    #include "Clear.sc"
#endif
