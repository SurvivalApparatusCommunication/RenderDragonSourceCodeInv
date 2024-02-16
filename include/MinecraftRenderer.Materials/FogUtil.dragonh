#ifndef FOG_UTIL_H_HEADER_GUARD
#define FOG_UTIL_H_HEADER_GUARD




//calculateFogIntensityVanilla
float calculateFogIntensity(float cameraDepth, float maxDistance, float fogStart, float fogEnd) { // #line 8
    float distance = cameraDepth / maxDistance;
    return saturate((distance - fogStart) / (fogEnd - fogStart));
}
//calculateFogIntensityFadedVanilla
float calculateFogIntensityFaded(float cameraDepth, float maxDistance, float fogStart, float fogEnd, float fogAlpha) {
    float distance = cameraDepth / maxDistance;
    distance += fogAlpha;
    return saturate((distance - fogStart) / (fogEnd - fogStart));
}
//applyFogVanilla
vec3 applyFog(vec3 diffuse, vec3 fogColor, float fogIntensity) {
    return mix(diffuse, fogColor, fogIntensity);
}

//float calculateFogIntensity(float cameraDepth, float fogStart, float fogEndMinusStartReciprocal) {
//    return saturate((cameraDepth - fogStart) * fogEndMinusStartReciprocal);
//}

//float calculateFogIntensityFaded(float cameraDepth, float maxDistance, float fogStart, float fogEndMinusStartReciprocal, float fogAlpha) {
//    float distance = cameraDepth / maxDistance;
//    distance += fogAlpha;
//    return saturate((distance - fogStart) * fogEndMinusStartReciprocal);
//}

#endif // FOG_UTIL_H_HEADER_GUARD