# Release builds are shrunk with R8 (mostly to drop the unused Material icons).

# BouncyCastle (pairing certificates + TLS to lockdownd): keep it whole. It looks up some
# algorithm implementations by name, and it's the part we can least afford to break.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# Keep our own classes' names readable in crash logs.
-keepnames class us.dannyhand.ipabackup.** { *; }
-keepattributes SourceFile,LineNumberTable
