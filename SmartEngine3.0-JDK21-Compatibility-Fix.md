# SmartEngine 3.0 在 JDK 21 上的兼容性修复方案

## 1. 问题描述

SmartEngine 3.0 在 OpenJDK 21 环境下运行时抛出以下异常：

```
java.lang.NoClassDefFoundError: java/lang/Compiler
    at com.alibaba.smart.framework.engine.org.mvel2.compiler.AbstractParser.setupParser(AbstractParser.java:214)
    at com.alibaba.smart.framework.engine.org.mvel2.compiler.AbstractParser.<clinit>(AbstractParser.java:152)
```

## 2. 根因分析

| 要素 | 说明 |
|------|------|
| **直接原因** | `java.lang.Compiler` 类在 JDK 9 中被标记为废弃，JDK 9+ 完全移除 |
| **核心原因** | SmartEngine 3.0 通过 Maven Shade 将 MVEL2 重定位（relocate）到 `com.alibaba.smart.framework.engine.org.mvel2` 包内，成为 SmartEngine JAR 的一部分。该 shaded MVEL2 版本较旧，`AbstractParser.<clinit>()` 静态初始化块中直接引用了 `java.lang.Compiler` |
| **触发路径1** | 引擎初始化 → 注解扫描器扫描 SmartEngine JAR → `Class.forName()` 加载 mvel2 包下的类 → 触发 `AbstractParser.<clinit>()` |
| **触发路径2** | 流程执行 → 排他网关条件表达式求值 → 调用 shaded `MVEL.compileExpression()` → 内部创建 `ExpressionCompiler extends AbstractParser` → 触发 `AbstractParser.<clinit>()` |
| **约束条件** | 不能修改 SmartEngine 3.0 源码 |

## 3. 修复方案：三个 Override 类 + 配置注入

核心思路：通过 SmartEngine 提供的 `ProcessEngineConfiguration` 扩展点，注入自定义实现替换默认行为，**完全绕过 shaded MVEL2**。

### 3.1 变更文件清单

```
pom.xml                                                          (修改)
src/test/java/.../overide/SimpleAnnotationScannerOverride.java   (新增)
src/test/java/.../overide/MvelExpressionEvaluatorOverride.java   (新增)
src/test/java/.../overide/MvelUtilOverride.java                  (新增)
src/test/java/.../test/cases/CustomBaseTestCase.java             (修改)
```

### 3.2 各文件变更详解

---

#### (1) `pom.xml` — 添加外部 MVEL2 依赖 + 编译器配置

**关键变更：** 引入外部 JDK 21 兼容的 `org.mvel:mvel2:2.5.2.Final` 替代 shaded 版本进行表达式求值。

```xml
<!-- 外部 MVEL2（JDK 21 兼容），用于替代 SmartEngine 内 shaded 的旧版 MVEL2 -->
<dependency>
    <groupId>org.mvel</groupId>
    <artifactId>mvel2</artifactId>
    <version>2.5.2.Final</version>
</dependency>

<!-- 编译器配置为 Java 21 -->
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.2</version>
    <configuration>
        <source>21</source>
        <target>21</target>
    </configuration>
</plugin>
```

---

#### (2) `SimpleAnnotationScannerOverride.java` — 修复触发路径1（注解扫描）

**作用：** 替换默认的 `SimpleAnnotationScanner`，在扫描 SmartEngine JAR 中的类时，跳过整个 `com.alibaba.smart.framework.engine.org.mvel2` 包，避免触发 `AbstractParser.<clinit>()`。

**关键代码（第92行）：**

```java
if(finalPackageName.startsWith("com.alibaba.smart.framework.engine.org.mvel2")){
    //ignore entire relocated mvel2 package, fix error:
    // java.lang.NoClassDefFoundError: java/lang/Compiler
    // (java.lang.Compiler was removed in JDK 9+)
}else {
    clazzSet.add(ClassUtil.loadClass(finalPackageName + '.' + className));
}
```

**为什么跳过整个包而不仅是 `AbstractParser`：** MVEL2 compiler 包中多个类继承 `AbstractParser`（如 `ExpressionCompiler`），加载任何一个子类都会触发父类的 `<clinit>()`。并且 mvel2 包下的类不会有 `@ExtensionBinding` 注解，跳过不影响功能。

---

#### (3) `MvelExpressionEvaluatorOverride.java` — 修复触发路径2（表达式求值入口）

**作用：** 实现 `ExpressionEvaluator` 接口，替换默认表达式求值器，将求值委托给 `MvelUtilOverride`（使用外部 MVEL2）。

```java
public class MvelExpressionEvaluatorOverride implements ExpressionEvaluator {
    @Override
    public Object eval(String expression, Map<String, Object> vars, boolean needCached) {
        return MvelUtilOverride.eval(expression, vars, needCached);
    }
}
```

---

#### (4) `MvelUtilOverride.java` — 修复触发路径2（表达式求值实现）

**作用：** 使用外部 `org.mvel2.MVEL`（v2.5.2.Final，JDK 21 兼容）代替 shaded `com.alibaba.smart.framework.engine.org.mvel2.MVEL` 进行表达式编译和执行。

**关键区别 — import 路径：**

```java
// 原代码（shaded MVEL2，引用 java.lang.Compiler，JDK 21 崩溃）:
import com.alibaba.smart.framework.engine.org.mvel2.MVEL;

// Override（外部 MVEL2 v2.5.2.Final，JDK 21 兼容）:
import org.mvel2.MVEL;
```

功能逻辑与原 `MvelUtil` 完全一致：表达式编译、缓存（ConcurrentHashMap, 128）、`${}` 包装处理。

---

#### (5) `CustomBaseTestCase.java` — 配置注入

**作用：** 在 `initProcessConfiguration()` 中将上述 Override 注入 SmartEngine 配置。

```java
protected void initProcessConfiguration() {
    processEngineConfiguration = new DefaultProcessEngineConfiguration();

    // 注入自定义表达式求值器（使用外部 MVEL2）
    processEngineConfiguration.setExpressionEvaluator(new MvelExpressionEvaluatorOverride());

    // 注入自定义注解扫描器（跳过 shaded mvel2 包）
    AnnotationScanner annotationScanner = new SimpleAnnotationScannerOverride(
        SmartEngine.class.getPackage().getName());
    processEngineConfiguration.setAnnotationScanner(annotationScanner);
}
```

## 4. 修复原理图

```
SmartEngine 3.0 初始化 & 执行流程（JDK 21）
│
├─ 触发路径1: 注解扫描
│   DefaultSmartEngine.init()
│   └─ AnnotationScanner.scan()
│       ├─ [原] SimpleAnnotationScanner → Class.forName("...mvel2.compiler.XxxParser") → AbstractParser.<clinit>() → java.lang.Compiler ❌
│       └─ [Override] SimpleAnnotationScannerOverride → 跳过 mvel2.* 包 ✅
│
└─ 触发路径2: 表达式求值
    ExclusiveGateway → ExpressionUtil.eval()
    └─ ExpressionEvaluator.eval()
        ├─ [原] MvelExpressionEvaluator → shaded MVEL.compileExpression() → AbstractParser.<clinit>() → java.lang.Compiler ❌
        └─ [Override] MvelExpressionEvaluatorOverride → 外部 org.mvel2.MVEL.compileExpression() (v2.5.2.Final, JDK 21 兼容) ✅
```

## 5. 补充方案：`--patch-module` 注入 `java.lang.Compiler` 桩类（可选）

如果存在其他未覆盖的代码路径仍然触发 shaded MVEL2，可通过 JVM 参数注入 `java.lang.Compiler` 桩类作为兜底。

**步骤：**

1. 创建桩类 `java/lang/Compiler.java`：

```java
package java.lang;

public final class Compiler {
    private Compiler() {}
    public static boolean compileClass(Class<?> clazz) { return false; }
    public static boolean compileClasses(String string) { return false; }
    public static Object command(Object any) { return null; }
    public static void enable() {}
    public static void disable() {}
}
```

2. 编译（需 `--patch-module` 才能编译 `java.lang` 包下的类）：

```bash
javac --patch-module java.base=src/test/patch-java-base \
      -d target/patch-classes \
      src/test/patch-java-base/java/lang/Compiler.java
```

3. 在 `pom.xml` 的 `maven-surefire-plugin` 中添加 JVM 参数：

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.0.0</version>
    <configuration>
        <argLine>--patch-module java.base=target/patch-classes</argLine>
    </configuration>
</plugin>
```

## 6. 适用范围

| 场景 | 是否适用 |
|------|---------|
| SmartEngine 3.0 + JDK 21 | 适用 |
| SmartEngine 3.0 + JDK 17 | 适用（`java.lang.Compiler` 同样已移除） |
| SmartEngine 3.0 + JDK 9~16 | 适用 |
| SmartEngine 3.0 + JDK 8 | 不需要（`java.lang.Compiler` 仍存在） |
