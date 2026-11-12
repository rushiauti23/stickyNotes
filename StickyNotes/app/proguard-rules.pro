# Room, Compose and Navigation ship their own consumer rules — nothing extra needed.

# Glance creates widget tap actions by class name, so keep them through R8.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
