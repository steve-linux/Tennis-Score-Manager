// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Copia di SwingSense (firmware/lib/swing): tenere allineate le due versioni.

// Classificatore k-NN dei colpi, allenato direttamente sullo stick.
//
// In calibrazione ogni colpo rilevato diventa un esempio con la sua etichetta.
// In gioco un nuovo colpo viene confrontato con gli esempi (feature
// normalizzate z-score) e vince la classe con più voti tra i k più vicini.
// Se anche l'esempio più vicino è troppo distante il colpo resta "sconosciuto".

#pragma once

#include <cstddef>
#include <cstdint>

#include "swing_detector.h"

namespace swing {

enum Stroke : uint8_t {
  kForehand = 0,
  kBackhand,
  kServe,
  kVolley,
  kStrokeCount,
  kUnknown = 0xFF,
};

const char* strokeName(uint8_t stroke);

class StrokeClassifier {
 public:
  static constexpr int   kMaxPerClass = 20;  // oltre, l'esempio più vecchio viene sostituito
  static constexpr int   kMinPerClass = 3;   // esempi minimi perché una classe conti
  static constexpr int   kK = 3;
  // Distanza RMS (in deviazioni standard) oltre la quale il colpo è "sconosciuto". Sui colpi veri il più
  // vicino della classe giusta sta entro ~0,7 (mediana 0,3); colpi diversi da tutti gli esempi, come con lo
  // stick girato rispetto alla calibrazione, partono da ~1,0: meglio "?" che un nome a caso.
  static constexpr float kRejectDistance = 0.9f;

  struct Result {
    uint8_t stroke;      // Stroke o kUnknown
    float   confidence;  // 0..1, quota dei voti della classe vincente
    float   distance;    // distanza dall'esempio più vicino
  };

  void add(uint8_t stroke, const Features& f);
  bool removeLast();  // annulla l'ultimo esempio aggiunto
  void clear(uint8_t stroke);
  void clearAll();

  int  count(uint8_t stroke) const;
  int  total() const { return n_; }
  bool ready() const;  // almeno due classi con kMinPerClass esempi

  Result classify(const Features& f) const;

  // Persistenza (blob binario per NVS / file).
  static constexpr size_t kMaxSerializedSize = 8 + kMaxPerClass * kStrokeCount * (1 + kFeatureCount * 4);
  size_t serialize(uint8_t* buf, size_t cap) const;
  bool   deserialize(const uint8_t* buf, size_t len);

 private:
  struct Example {
    uint8_t  stroke;
    Features f;
  };

  void removeAt(int i);
  void updateStats();
  float distance(const Features& a, const Features& b) const;

  Example ex_[kMaxPerClass * kStrokeCount];
  int     n_ = 0;  // esempi in ordine di inserimento
  Features std_{};  // deviazione standard di ogni feature sugli esempi
};

}  // namespace swing
