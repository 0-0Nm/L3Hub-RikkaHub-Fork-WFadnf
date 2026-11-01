# sshd 的 ExceptionUtils.peelException 通过反射检查 JMX 异常，Android 没有 javax.management。
# 这些类只出现在反射分支里，运行期不会真正加载，屏蔽 R8 的缺失类检查即可。
-dontwarn javax.management.**
-dontwarn javax.naming.**
