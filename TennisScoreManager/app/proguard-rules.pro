# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later

# Regole R8 della build release. Le librerie (AndroidX, Compose, coroutine,
# kotlinx-serialization) portano già le loro regole; qui solo ciò che R8 non può dedurre.

# Tabellone (DisplayActivity): scoreboard.html chiama window.TSMDisplay.lost(), un metodo
# visibile solo da JavaScript che R8 altrimenti rinominerebbe o toglierebbe.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Numeri di riga leggibili negli stack trace delle segnalazioni di errore
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
