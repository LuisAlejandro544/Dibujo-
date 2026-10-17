# Reglas de R8 para :app. El motor nativo (JNI) accede por nombre a los
# métodos external de NativeEngine: mantenerlos si algún día se activa
# minifyEnabled en release.
-keep class com.dibujo.engine.NativeEngine { *; }
