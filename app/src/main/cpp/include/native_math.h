#pragma once

#include <array>

namespace aurora {

using Matrix4 = std::array<float, 16>;

Matrix4 MultiplyMatrices(const Matrix4& left, const Matrix4& right);

}  // namespace aurora
