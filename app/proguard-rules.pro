# sshd 的 ExceptionUtils.peelException 通过反射检查 JMX 异常，Android 没有 javax.management。
# 这些类只出现在反射分支里，运行期不会真正加载，屏蔽 R8 的缺失类检查即可。
-dontwarn javax.management.**
-dontwarn javax.naming.**

# sshd 大量通过反射 / ServiceLoader 装配（cipher、kex、mac、signature 工厂全在
# META-INF/services 里按类名加载），R8 看不到这些引用就会把它们裁掉，
# 运行期表现为 NoClassDefFoundError: org.apache.sshd.server.SshServer。
-keep class org.apache.sshd.** { *; }
-keep interface org.apache.sshd.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-dontwarn org.apache.sshd.**

# 兜底：R8 在 AGP 9 的 optimization 模式下仍可能把「只作为其它类的父类型出现」的接口裁掉，
# 显式保留全部类型（含内部类），并禁止改名，避免反射里按名字找不到
-keepnames class org.apache.sshd.** { *; }
-keep class org.apache.sshd.server.** { *; }
