float linearToLogDepth(float linearDepth) {
    return log((exp(4.0) - 1.0) * linearDepth + 1.0) / 4.0;
}

float logToLinearDepth(float logDepth) {
    return (exp(4.0 * logDepth) - 1.0) / (exp(4.0) - 1.0);
}

void Scattering(uvec3 GlobalInvocationID) {
    int volumeWidth = int(VolumeDimensions.x);
    int volumeHeight = int(VolumeDimensions.y);
    int volumeDepth = int(VolumeDimensions.z);
    int x = int(GlobalInvocationID.x);
    int y = int(GlobalInvocationID.y);
    if (x >= volumeWidth || y >= volumeHeight) {
        return;
    }
    float prevW = -0.5 / VolumeDimensions.z;
    float prevWLinear = logToLinearDepth(prevW);
    float prevDepth = (1.0 - prevWLinear) * VolumeNearFar.x + prevWLinear * VolumeNearFar.y;
    vec4 accum = vec4(0.0, 0.0, 0.0, 1.0);
    for (int z = 0; z < volumeDepth; ++z) {
        float nextW = (float(z) + 0.5) / VolumeDimensions.z;
        float nextWLinear = logToLinearDepth(nextW);
        float nextDepth = (1.0 - nextWLinear) * VolumeNearFar.x + nextWLinear * VolumeNearFar.y;
        float stepSize = nextDepth - prevDepth;
        prevDepth = nextDepth;
        vec4 sourceExtinction = imageLoad(s_ScatteringBufferIn, ivec3(x, y, z));
        float transmittance = exp(-sourceExtinction.a * stepSize);
        float contribution = abs(sourceExtinction.a) > 1e-6 ? ((1.0 - transmittance) / sourceExtinction.a) : stepSize;
        accum.rgb += accum.a * contribution * sourceExtinction.rgb;
        accum.a *= transmittance;
        imageStore(s_ScatteringBufferOut, ivec3(x, y, z), accum);
    }
}

NUM_THREADS(8, 8, 1)
void main() {
    Scattering(gl_GlobalInvocationID);
}
