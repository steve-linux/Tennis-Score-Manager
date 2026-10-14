// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Copia di SwingSense (firmware/lib/swing): tenere allineate le due versioni.

#include "stroke_classifier.h"

#include <cmath>
#include <cstring>

namespace swing {

static constexpr uint8_t kMagic[4] = {'S', 'W', 'S', 1};  // "SWS" + versione formato
static constexpr size_t  kExampleSize = 1 + kFeatureCount * sizeof(float);
static constexpr float   kMinStd = 0.1f;  // evita che feature quasi costanti dominino

const char* strokeName(uint8_t stroke) {
  static const char* const kNames[kStrokeCount] = {"dritto", "rovescio", "servizio", "volee"};
  return stroke < kStrokeCount ? kNames[stroke] : "?";
}

void StrokeClassifier::add(uint8_t stroke, const Features& f) {
  if (stroke >= kStrokeCount) return;
  if (count(stroke) >= kMaxPerClass) {
    for (int i = 0; i < n_; i++) {
      if (ex_[i].stroke == stroke) {
        removeAt(i);
        break;
      }
    }
  }
  ex_[n_++] = Example{stroke, f};
  updateStats();
}

bool StrokeClassifier::removeLast() {
  if (n_ == 0) return false;
  n_--;
  updateStats();
  return true;
}

void StrokeClassifier::clear(uint8_t stroke) {
  for (int i = n_ - 1; i >= 0; i--) {
    if (ex_[i].stroke == stroke) removeAt(i);
  }
  updateStats();
}

void StrokeClassifier::clearAll() {
  n_ = 0;
  updateStats();
}

int StrokeClassifier::count(uint8_t stroke) const {
  int c = 0;
  for (int i = 0; i < n_; i++) c += ex_[i].stroke == stroke;
  return c;
}

bool StrokeClassifier::ready() const {
  int classes = 0;
  for (uint8_t s = 0; s < kStrokeCount; s++) classes += count(s) >= kMinPerClass;
  return classes >= 2;
}

void StrokeClassifier::removeAt(int i) {
  for (int j = i; j < n_ - 1; j++) ex_[j] = ex_[j + 1];
  n_--;
}

void StrokeClassifier::updateStats() {
  for (int k = 0; k < kFeatureCount; k++) {
    float sum = 0, sq = 0;
    for (int i = 0; i < n_; i++) {
      sum += ex_[i].f[k];
      sq += ex_[i].f[k] * ex_[i].f[k];
    }
    float var = n_ > 1 ? (sq - sum * sum / n_) / (n_ - 1) : 0.0f;
    std_[k] = std::fmax(std::sqrt(std::fmax(var, 0.0f)), kMinStd);
  }
}

// Distanza euclidea sulle feature normalizzate, divisa per il numero di
// feature (RMS): 1.0 significa "in media una deviazione standard".
float StrokeClassifier::distance(const Features& a, const Features& b) const {
  float d = 0;
  for (int k = 0; k < kFeatureCount; k++) {
    float z = (a[k] - b[k]) / std_[k];
    d += z * z;
  }
  return std::sqrt(d / kFeatureCount);
}

StrokeClassifier::Result StrokeClassifier::classify(const Features& f) const {
  Result r{kUnknown, 0.0f, INFINITY};
  if (!ready()) return r;

  bool usable[kStrokeCount];
  for (uint8_t s = 0; s < kStrokeCount; s++) usable[s] = count(s) >= kMinPerClass;

  // I k esempi più vicini, ordinati per distanza.
  float   bestD[kK];
  uint8_t bestS[kK];
  int     found = 0;
  for (int i = 0; i < n_; i++) {
    if (!usable[ex_[i].stroke]) continue;
    float d = distance(f, ex_[i].f);
    int pos = found < kK ? found++ : kK;
    while (pos > 0 && bestD[pos - 1] > d) {
      if (pos < kK) {
        bestD[pos] = bestD[pos - 1];
        bestS[pos] = bestS[pos - 1];
      }
      pos--;
    }
    if (pos < kK) {
      bestD[pos] = d;
      bestS[pos] = ex_[i].stroke;
    }
  }

  float votes[kStrokeCount] = {};
  float total = 0;
  for (int i = 0; i < found; i++) {
    float w = 1.0f / (bestD[i] + 0.1f);
    votes[bestS[i]] += w;
    total += w;
  }
  uint8_t winner = 0;
  for (uint8_t s = 1; s < kStrokeCount; s++) {
    if (votes[s] > votes[winner]) winner = s;
  }

  r.distance = bestD[0];
  r.confidence = votes[winner] / total;
  r.stroke = bestD[0] <= kRejectDistance ? winner : (uint8_t)kUnknown;
  return r;
}

size_t StrokeClassifier::serialize(uint8_t* buf, size_t cap) const {
  const size_t len = 8 + n_ * kExampleSize;
  if (cap < len) return 0;
  memcpy(buf, kMagic, 4);
  buf[4] = (uint8_t)(n_ & 0xFF);
  buf[5] = (uint8_t)(n_ >> 8);
  buf[6] = buf[7] = 0;
  uint8_t* p = buf + 8;
  for (int i = 0; i < n_; i++) {
    *p++ = ex_[i].stroke;
    memcpy(p, ex_[i].f.data(), kFeatureCount * sizeof(float));
    p += kFeatureCount * sizeof(float);
  }
  return len;
}

bool StrokeClassifier::deserialize(const uint8_t* buf, size_t len) {
  if (len < 8 || memcmp(buf, kMagic, 4) != 0) return false;
  const int n = buf[4] | (buf[5] << 8);
  if (n > kMaxPerClass * kStrokeCount || len < 8 + n * kExampleSize) return false;

  for (int i = 0; i < n; i++) {
    if (buf[8 + i * kExampleSize] >= kStrokeCount) return false;
  }

  const uint8_t* p = buf + 8;
  for (int i = 0; i < n; i++) {
    ex_[i].stroke = p[0];
    memcpy(ex_[i].f.data(), p + 1, kFeatureCount * sizeof(float));
    p += kExampleSize;
  }
  n_ = n;
  updateStats();
  return true;
}

}  // namespace swing
