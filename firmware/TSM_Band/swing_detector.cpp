// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Copia di SwingSense (firmware/lib/swing): tenere allineate le due versioni.

#include "swing_detector.h"

#include <cmath>

namespace swing {

static float norm3(float x, float y, float z) { return std::sqrt(x * x + y * y + z * z); }

SwingDetector::SwingDetector(const DetectorConfig& cfg) : cfg_(cfg) { reset(); }

void SwingDetector::reset() {
  count_ = 0;
  inSwing_ = false;
  peakIdx_ = 0;
  peakVal_ = 0.0f;
  refractoryUntil_ = 0;
}

bool SwingDetector::push(const ImuSample& s, Swing& out) {
  const uint32_t idx = count_++;
  buf_[idx % kBufSize] = s;
  const float mag = norm3(s.gx, s.gy, s.gz);

  if (!inSwing_) {
    // Serve abbastanza storia per la parte di finestra prima del picco.
    if (mag >= cfg_.triggerDps && idx >= refractoryUntil_ && idx >= (uint32_t)cfg_.preSamples) {
      inSwing_ = true;
      peakIdx_ = idx;
      peakVal_ = mag;
    }
    return false;
  }

  if (mag > peakVal_) {
    peakIdx_ = idx;
    peakVal_ = mag;
  }
  if (idx - peakIdx_ < (uint32_t)cfg_.postSamples) return false;

  inSwing_ = false;
  refractoryUntil_ = idx + cfg_.refractorySamples;
  out.peakIndex = peakIdx_;
  out.peakGyro = peakVal_;
  computeFeatures(peakIdx_, out);
  return out.peakAccel >= cfg_.minPeakAccelG;
}

// Feature (scalate per stare tutte attorno a ±1..3):
//   0-2   accelerazione media nei primi 200 ms della finestra (postura del braccio), g
//   3-5   rotazione integrata su ogni asse nella finestra, centinaia di gradi
//   6-8   velocità angolare al picco su ogni asse, migliaia di °/s
//   9     modulo massimo dell'accelerazione, unità di 8 g
//   10    durata con |gyro| > metà soglia, unità di 0.5 s
void SwingDetector::computeFeatures(uint32_t peak, Swing& out) const {
  const uint32_t start = peak - cfg_.preSamples;
  const uint32_t end = peak + cfg_.postSamples;  // incluso
  const float dt = 1.0f / cfg_.sampleRateHz;
  const int postureSamples = (int)(0.2f * cfg_.sampleRateHz);

  float pax = 0, pay = 0, paz = 0;
  float rx = 0, ry = 0, rz = 0;
  float maxAcc = 0;
  int activeSamples = 0;

  for (uint32_t i = start; i <= end; i++) {
    const ImuSample& s = at(i);
    if ((int)(i - start) < postureSamples) {
      pax += s.ax;
      pay += s.ay;
      paz += s.az;
    }
    rx += s.gx * dt;
    ry += s.gy * dt;
    rz += s.gz * dt;
    const float acc = norm3(s.ax, s.ay, s.az);
    if (acc > maxAcc) maxAcc = acc;
    if (norm3(s.gx, s.gy, s.gz) > cfg_.triggerDps * 0.5f) activeSamples++;
  }

  const ImuSample& p = at(peak);
  out.peakAccel = maxAcc;
  out.features = {
      pax / postureSamples, pay / postureSamples, paz / postureSamples,
      rx / 100.0f,          ry / 100.0f,          rz / 100.0f,
      p.gx / 1000.0f,       p.gy / 1000.0f,       p.gz / 1000.0f,
      maxAcc / 8.0f,
      activeSamples / (0.5f * cfg_.sampleRateHz),
  };
}

}  // namespace swing
