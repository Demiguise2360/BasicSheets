# BasicSheets — default ProGuard/R8 rules. Minification is off for now (see build.gradle.kts).
-keepattributes *Annotation*
# Room generates code at build time; nothing extra needed for runtime reflection.
