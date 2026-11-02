# ROVARI — правила запутывания кода (R8)
# Все классы переносятся в один безымянный пакет и получают короткие имена.
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute SourceFile
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable
# Отладочные сообщения вырезаются из выпущенной версии
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
