// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Copia di SwingSense (firmware/lib/swing): tenere allineate le due versioni.

// Rilevamento dei colpi dal flusso IMU ed estrazione delle feature.
//
// Un colpo inizia quando il modulo della velocità angolare supera una soglia.
// Il picco viene seguito finché il segnale resta alto; quando sono passati
// `postSamples` campioni dal picco, la finestra [picco - pre, picco + post]
// viene riassunta in un vettore di feature per il classificatore.
//
// Codice C++ puro (niente Arduino), così si può testare sul PC.

#pragma once

#include <array>
#include <cstdint>

namespace swing {

struct ImuSample {
  float ax, ay, az;  // g
  float gx, gy, gz;  // gradi/s
};

constexpr int kFeatureCount = 11;
using Features = std::array<float, kFeatureCount>;

struct Swing {
  uint32_t peakIndex;  // indice (contatore campioni) del picco
  float    peakGyro;   // modulo velocità angolare al picco, °/s
  float    peakAccel;  // modulo accelerazione massimo nella finestra, g
  Features features;
};

struct DetectorConfig {
  float sampleRateHz      = 100.0f;
  float triggerDps        = 400.0f;  // sotto questa soglia non è un colpo
  float minPeakAccelG     = 2.5f;    // braccio che ruota piano senza un vero colpo
  int   preSamples        = 60;      // finestra prima del picco
  int   postSamples       = 40;      // finestra dopo il picco
  int   refractorySamples = 50;      // pausa dopo un colpo (ignora il follow-through)
};

class SwingDetector {
 public:
  static constexpr int kBufSize = 128;  // deve contenere pre + post + 1 campioni

  explicit SwingDetector(const DetectorConfig& cfg = DetectorConfig());

  // Aggiunge un campione. Restituisce true quando un colpo è completo e lo scrive in `out`.
  bool push(const ImuSample& s, Swing& out);
  void reset();

  const DetectorConfig& config() const { return cfg_; }

 private:
  const ImuSample& at(uint32_t idx) const { return buf_[idx % kBufSize]; }
  void computeFeatures(uint32_t peak, Swing& out) const;

  DetectorConfig cfg_;
  ImuSample buf_[kBufSize];
  uint32_t  count_ = 0;          // campioni ricevuti in totale
  bool      inSwing_ = false;
  uint32_t  peakIdx_ = 0;
  float     peakVal_ = 0.0f;
  uint32_t  refractoryUntil_ = 0;
};

}  // namespace swing
