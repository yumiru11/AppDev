# core:markdown 的 R8 keep 规则（随 AAR 分发给 :app）
#
# coil-gif（缺陷 #3：GIF 动图只出首帧）依赖 Coil 3 的 ServiceLoader 自动注册：
# META-INF/services/coil3.util.DecoderServiceLoaderTarget 里按**原始类名**列出
# coil3.gif.internal.GifDecoderServiceLoaderTarget。R8 改名/裁剪后 ServiceLoader
# 找不到实现类 → release 变体 GIF 静默退回首帧（debug 不受影响，因此仅在 release 暴露）。
# 与 :app/proguard-rules.pro 对 coil3.svg 的既有处理同型。
-keep class coil3.gif.** { *; }
