#include <bgfx_compute.sh>

uniform vec4 VolumeNearFar;
uniform mat4 InvViewProj;
uniform vec4 VolumeDimensions;
uniform mat4 Proj;
uniform vec4 WorldOrigin;
uniform vec4 AlbedoExtinction;
uniform vec4 DensityFalloff;

IMAGE2D_ARRAY_WR(s_ScatteringBufferOut, rgba16f, 0);

/*
float linearToLogDepth(float linearDepth) {
    return log((exp(4.0) - 1.0) * linearDepth + 1.0) / 4.0;
}
*/

float logToLinearDepth(float logDepth) {
    return (exp(4.0 * logDepth) - 1.0) / (exp(4.0) - 1.0);
}

/*
vec3 ndcToVolume(vec3 ndc, mat4 inverseProj, vec2 nearFar) {
    vec2 uv = 0.5 * (ndc.xy + vec2(1.0, 1.0));
    vec4 view = mul(inverseProj, vec4(ndc, 1.0));
    float viewDepth = -view.z / view.w;
    float wLinear = (viewDepth - nearFar.x) / (nearFar.y - nearFar.x);
    return vec3(uv, linearToLogDepth(wLinear));
}
*/

vec3 volumeToNdc(vec3 uvw, mat4 proj, vec2 nearFar) {
    vec2 xy = 2.0 * uvw.xy - vec2(1.0, 1.0);
    float wLinear = logToLinearDepth(uvw.z);
    float viewDepth = -((1.0 - wLinear) * nearFar.x + wLinear * nearFar.y);
    vec4 ndcDepth = mul(proj, vec4(0.0, 0.0, viewDepth, 1.0));
    float z = ndcDepth.z / ndcDepth.w;
    return vec3(xy, z);
}

/*
vec3 worldToVolume(vec3 world, mat4 viewProj, mat4 invProj, vec2 nearFar) {
    vec4 proj = mul(viewProj, vec4(world, 1.0));
    vec3 ndc = proj.xyz / proj.w;
    return ndcToVolume(ndc, invProj, nearFar);
}
*/

vec3 volumeToWorld(vec3 uvw, mat4 invViewProj, mat4 proj, vec2 nearFar) {
    vec3 ndc = volumeToNdc(uvw, proj, nearFar);
    vec4 world = mul(invViewProj, vec4(ndc, 1.0));
    return world.xyz / world.w;
}

/*
vec4 sampleVolume(highp sampler2DArray volume, ivec3 dimensions, vec3 uvw) {
    float depth = uvw.z * float(dimensions.z) - 0.5;
    int index = clamp(int(depth), 0, dimensions.z - 2);
    float offset = clamp(depth - float(index), 0.0, 1.0);
    vec4 a = textureSample(volume, vec3(uvw.xy, index)).rgba;
    vec4 b = textureSample(volume, vec3(uvw.xy, index + 1)).rgba;
    return mix(a, b, offset);
}

vec3 applyScattering(vec4 sourceExtinction, vec3 color) {
    return sourceExtinction.rgb + sourceExtinction.a * color;
}
*/

void Populate(uvec3 GlobalInvocationID) {
    int volumeWidth = int(VolumeDimensions.x);
    int volumeHeight = int(VolumeDimensions.y);
    int volumeDepth = int(VolumeDimensions.z);
    int x = int(GlobalInvocationID.x);
    int y = int(GlobalInvocationID.y);
    int z = int(GlobalInvocationID.z);
    if (x >= volumeWidth || y >= volumeHeight || z >= volumeDepth) {
        return;
    }
    vec3 uvw = (vec3(x, y, z) + vec3(0.5, 0.5, 0.5)) / VolumeDimensions.xyz;
    vec3 world = volumeToWorld(uvw, InvViewProj, Proj, VolumeNearFar.xy) - WorldOrigin.xyz;
    float density = clamp(DensityFalloff.x * exp(-world.y * DensityFalloff.y), 0.0, 1.0);
    vec4 scatteringExtinction = density * vec4(AlbedoExtinction.a * AlbedoExtinction.rgb, AlbedoExtinction.a);
    imageStore(s_ScatteringBufferOut, ivec3(x, y, z), scatteringExtinction);
}

NUM_THREADS(8, 8, 8)
void main() {
    Populate(gl_GlobalInvocationID);
}
