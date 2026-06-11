#include "include/native_math.h"

#include <jni.h>

namespace aurora {

Matrix4 MultiplyMatrices(const Matrix4& left, const Matrix4& right) {
    Matrix4 result{};
    for (int column = 0; column < 4; ++column) {
        for (int row = 0; row < 4; ++row) {
            float value = 0.0F;
            for (int element = 0; element < 4; ++element) {
                value += left[element * 4 + row] * right[column * 4 + element];
            }
            result[column * 4 + row] = value;
        }
    }
    return result;
}

}  // namespace aurora

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_aurora_cinema_render_nativebridge_NativeMath_multiplyMatrices(
    JNIEnv* env,
    jobject,
    jfloatArray left_array,
    jfloatArray right_array) {
    if (env->GetArrayLength(left_array) != 16 || env->GetArrayLength(right_array) != 16) {
        return nullptr;
    }

    aurora::Matrix4 left{};
    aurora::Matrix4 right{};
    env->GetFloatArrayRegion(left_array, 0, 16, left.data());
    env->GetFloatArrayRegion(right_array, 0, 16, right.data());
    const aurora::Matrix4 result = aurora::MultiplyMatrices(left, right);

    jfloatArray output = env->NewFloatArray(16);
    if (output != nullptr) {
        env->SetFloatArrayRegion(output, 0, 16, result.data());
    }
    return output;
}
