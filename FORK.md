# Fork Notes

本仓库是 [RikkaHub](https://github.com/rikkahub/rikkahub) 的二次改造版，对外名称 **L3Hub**，目标是**与原版共存**（同一台设备上两个应用并排安装）。

## 共存原理

Android 用 `applicationId` 识别应用，不是 Kotlin 包名：

| | 值 | 说明 |
|---|---|---|
| `namespace` | `me.rerere.rikkahub` | 保留，547 个文件的 import 不用动 |
| `applicationId` | `com.rikkahub.l3hub.wfadnf` | 与上游不同，系统视为独立应用 |

- 不同 applicationId + 自己的签名 → 可以共存
- 同一 applicationId + 不同签名 → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
- 改 applicationId 后数据目录独立，读不到原版数据（要迁移走内置备份的导出/导入）

## 本仓库相对上游的改动

1. `app/build.gradle.kts`
   - `applicationId` → `com.rikkahub.l3hub.wfadnf`
   - 移除 Firebase：`google.services` / `firebase.crashlytics` 两个插件 + bom/analytics/crashlytics 三个依赖
   - 新增 `buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "false")`
2. `build.gradle.kts`：移除两个 Firebase 插件声明
3. `di/AppModule.kt`：移除 `Firebase.crashlytics` / `Firebase.analytics` 两个单例
4. `ui/pages/chat/ChatVM.kt`：移除 `FirebaseAnalytics` 构造参数和 5 处埋点；更新检查受 `UPDATE_CHECK_ENABLED` 控制
5. `ui/pages/chat/ChatDrawer.kt`、`ui/pages/setting/SettingPreferencesNotificationPage.kt`：更新卡片入口受同一开关控制
6. `di/ViewModelModule.kt`：移除 `ChatVM` 的 `analytics = get()` 注入
7. `androidTest/.../ExampleInstrumentedTest.kt`：断言改为 `BuildConfig.APPLICATION_ID`
8. 新增上下文预警进度条（见下节）

### 为什么摘 Firebase

- 原仓库的 `google-services.json` 是 CI 从 secret 注入的，本地没有 → `com.google.gms.google-services` 插件会直接让构建失败
- 就算补上，analytics / crashlytics 也会把数据发到原作者的项目
- 需要自己的埋点或崩溃收集时：注册自己的 Firebase 项目、放回 `app/google-services.json`、恢复上面删掉的 4 处即可

## 上下文预警进度条（fork 新增）

输入框上方两条进度条，分别以模型上的两个门限为分母，共用同一个当前占用量：

| 条 | 含义 | 配置 |
|---|---|---|
| `WARN` | 性能最佳点，超过后回复质量开始下滑 | 模型编辑页 → WARN 阈值 |
| `MAX` | 性能骤降点，到达后禁止继续发送消息 | 模型编辑页 → MAX 阈值 |

- 当前占用取最近一次回复的真实用量（`prompt + completion`，即 `Conversation.lastContextTokens()`），新会话或刚压缩完显示 0
- 点按进度条在百分比和绝对值（`12.3K / 200K`）之间切换，选择存在 `DisplaySetting.contextMeterAbsolute`
- 占用 ≥ 80% 转提醒色，MAX 条满格转 error 色并显示提示
- 两个门限都留空时整块不显示
- 拦截点：聊天页发送（toast）、`ChatService.sendMessage`、语音入队、队列派发（暂停队列）

改动文件（`Model` 多了两个可空字段，旧设置 JSON 缺字段时按 null 处理，向后兼容）：

```
ai/.../provider/Model.kt                        warnContextTokens / maxContextTokens
app/.../data/model/ContextWindow.kt             纯逻辑：比例、是否到达上限
app/.../ui/components/ai/ContextUsageMeter.kt   两条进度条的 UI
app/.../ui/components/ai/ChatInput.kt           插到输入框上方
app/.../ui/pages/chat/ChatPage.kt               传入占用、点击切换、发送拦截
app/.../ui/pages/setting/SettingProviderDetailPage.kt  模型表单里的 WARN/MAX 输入
app/.../service/ChatService.kt                  发送/语音/队列的硬拦截
app/.../data/datastore/Settings.kt              contextMeterAbsolute
```

## 恢复备份时的附件路径改写（fork 必需）

原版把附件存成绝对路径：

```
file:///data/user/0/me.rerere.rikkahub/files/upload/<uuid>.png
```

这条路径同时出现在 `settings.json`（用户头像、助手头像、聊天背景、字体）和数据库的
`conversation.nodes` / `message_node.messages` JSON 里。fork 换了 applicationId，
新应用读不到原应用的私有目录，导入备份后就会出现「聊天记录和配置都在，但图片/文件全是空白」。

`data/sync/BackupPaths.kt` 在 `stageRestore()` 里做前缀改写：

- `settings.json`：整段文本正则替换
- 数据库：遍历所有非 FTS 文本列，取出现过的前缀后用 `REPLACE()` 批量改写
- 同时兼容 `file://` URI 与裸路径（字体）、`/data/user/N` 与 `/data/data`

改写后路径指向当前应用的 `filesDir`，而文件在同一个函数里已经解包到了相同的相对位置，所以能正常显示。
同一包名恢复（前缀相同）时不做任何修改，可重复执行。

> 已经导入过坏数据的，重新导入一次备份即可修复。

另外把 AI 生图的输出目录 `files/images` 加进了备份范围（上游只备份 upload/skills/fonts/media_creation），
否则生成的图片在换包名恢复后同样找不回来。旧备份里没有这个目录，恢复时跳过，不影响。

## 自建功能二：语言 / 调色板 / 上下文开关 / md 导入 / 消息队列

| 功能 | 入口 | 实现 |
|---|---|---|
| 应用内语言 | 设置 → 常规 → 语言 | `utils/AppLanguage.kt`，选择存 SharedPreferences（attachBaseContext 要同步读），Application 和 RouteActivity 各包一层 configuration，选完 `recreate()` |
| RGB/HSV 调色板 | 设置 → 主题 → 自定义颜色 | `SettingThemePage.ColorPickerRow` 从 HSL 改成 HSV/RGB 双模式，文本输入支持 `hsv()`/`rgb()`/`#RRGGBB` |
| MAX 拦截开关 | 模型编辑 → 上下文预警 | `Model.enforceMaxContext`（默认 true）；关闭后到 MAX 只弹提示，`ChatService` 不再硬拦 |
| Markdown 导入 | 备份 → 从 Markdown 导入 | `data/sync/importer/MarkdownChatImporter.kt`，支持 `**User**:` / `User:` / `## User` 三种标记，代码块内不切分 |
| 消息队列开关 | 设置 → 常规 → 消息队列 | `Settings.enableMessageQueue`（默认 true）；关闭后生成中发送 = 打断当前回复并立即发送 |
| 强制插话 | 生成中输入框右侧按钮 | 仅队列开启时出现；`MessageQueue.enqueueFirst` + cancel 当前 job，生成结束立刻派发队首 |

语言支持 en / zh-CN / zh-TW / ja / ko / ru / de / ar，对应 `res/values-*`。

注意：`Settings` 新增字段必须同时加进 `SettingsFields.SETTINGS_FIELDS` 和 `PreferencesStore` 的 key，
否则 `SettingsFieldsTest` 会失败，数据也不会落盘。

## 上下文门限预设

`data/model/ContextThresholdPresets.kt`：按模型名给 WARN/MAX 预填一份，命中不了的用 131072 / 196608。

| 型号 | WARN | MAX |
|---|---|---|
| Kimi K3 | 262144 | 327680 |
| DeepSeek V4.1 | 172032 | 221184 |
| DeepSeek V4 | 196608 | 262144 |
| DeepSeek V3* | 131072 | 131072 |
| GLM 5.* | 147456 | 221184 |
| Qwen 3.* | 196608 | 262144 |
| GPT 5.* | 196608 | 262144 |
| Claude 4.* | 147456 | 221184 |
| Claude 5.* | 221184 | 327680 |
| Step(fun) 3.* | 147456 | 221184 |
| Step(fun) 5* | 196608 | 327680 |
| Nemotron | 131072 | 196608 |
| Muse | 147456 | 221184 |
| GPT OSS | 65536 | 131072 |
| 其他 | 131072 | 196608 |

- 在 `Settings.normalized()` 里执行：`contextThresholdsAuto` 为 true **且**两个门限都为空时才填
- 用户在模型编辑页手改任一值 → `contextThresholdsAuto = false`，之后不再覆盖；页面上会出现「使用预设」按钮可以恢复
- 主版本号从模型名里取（`claude-3-5-sonnet` 算 3 代、`qwen2.5-72b` 算 2 代），避免把旧型号误判成新代

## SSH 工作区的两个修复（fork）

### 远程目录不可访问不再让"测试连接"整体失败

`SshConnection.probe` 以前直接 `stat` 远程目录，目录没权限时 SFTP 抛错，整个测试连接失败，
界面上只剩一句 `SSH_FX_PERMISSION_DENIED: Permission denied`，看不出连接其实已经通了。
现在目录探测失败只作为结果返回（`SshProbeResult.remoteDirIssue`：不存在 / 不是目录 / 权限不足 / 其他），
连接照样成功，编辑页会说明原因，并在测试成功后给一个"主目录"按钮，一键填回远程用户的 home。

典型场景：连 Termux 时把 App 自己的私有目录（`/data/user/0/...`）填进了远程目录，Termux 的 sshd
以另一个 uid 运行，访问不到；换成 `/data/data/com.termux/files/home` 即可。

### 恢复备份后 SSH 凭据解不开

凭据用 Android Keystore 里不可导出的 AES 密钥加密，换设备、重装或换包名（原版 → fork）恢复备份后
密文一定解不开。以前界面仍显示"已保存，留空保持不变"，一测试连接就报
`Saved SSH credentials cannot be decrypted...`。

现在：

- `BackupManager` 导入时调用 `SshCredentialSanitizer.dropUnreadable()`，把解不开的字段清掉
- 打开编辑页时 `WorkspaceRepository.usableSavedSecrets()` 再校验一次并回写，顺手修好已经导入过的旧数据
- 清掉后界面如实显示"未填写"，并提示"已保存的凭据不可用，请重新填写"
- 保存时不再沿用解不开的密文；没有可用凭据时直接提示，不再去连服务器

## 子代理（Sub-Agent，fork 新增）

三处配合使用，形成一个最小的多代理协作：

1. **助手 → 基础**：打开「设为子代理」，这个助手才会出现在别的助手的派发列表里
2. **助手 → 本地工具**：打开「派发子代理」，主助手拿到 `dispatch_sub_agent` 工具，参数为 `assistant`（子代理名称）和 `task`（自包含的任务描述）。派发能力只看这个开关（以及「当前会话自己是不是子代理会话」），「设为子代理」只表示「能被派发」，不再顺带给出派发工具
   - 开关下方的「允许派发的子代理」可以多选目标：勾选后工具目录与 `SubAgentRunner` 都只认这些助手，不勾选表示不限制（老配置行为不变）。子代理继续派发下一层时用它自己那份限制
3. **聊天页右上角**：任务树按钮，展开当前会话下所有子代理会话，以及每个会话里执行过的工具调用与输出

实现要点：

- 子代理跑在独立会话里，会话带 `parentConversationId`，挂在父会话下面，不占用主对话上下文
- 子代理最多三代（主 → 子 → 孙），超过后工具直接返回提示；不能把任务派发给自己
- 子代理会话就是普通会话：和别的会话一样出现在侧边栏与「历史会话」里，也能被搜索到；打开时侧边栏会跟着切到它自己的助手，`parentConversationId` 只用来画任务树
- 数据库升到 v33，新增 `parent_conversation_id` / `sub_agent_task` / `sub_agent_state` 三列，走 Room 自动迁移
- 子代理会话没有人在旁边点确认，工具调用一律按完全权限直接执行；`ask_user` 没有真人可答，直接返回提示让子代理自己决定
- `wait` 模式：父回合等子代理跑完，结果作为工具结果返回
- `background` 模式：派发后立即返回。子代理每一轮生成结束都把这一轮的结果投回父会话（自己派的孙代理回传结果后触发的新一轮同样回传），父会话正在生成时先缓冲，空闲后把多条结果合并成一条消息投递，不打断当前工作；回传正文带 `finished round N`，父会话据此区分同一个子代理的多条结果
- 中间层子代理会话没有页面在打开时也会继续跑：它收到孙代理回传的结果就直接开下一轮，否则整条子代理链会断在中间这一层
- 回投的那条消息是系统通知，不是用户发言：为了各家 provider 都能带进请求，它存的角色是 USER，但带 `isSubAgentNotice` 标记，聊天页按「子代理任务完成 · 系统自动回传」的卡片渲染，导出 XML 记为 `system`，组装请求时才补一句「不是你输入的内容」

## 备份覆盖范围（fork 备注）

备份包结构：`settings.json` + 数据库快照 + `upload/`、`images/`、`skills/`、`fonts/`、`media_creation/`。

L3Hub 自己的设置都在 `Settings` 里，随 `settings.json` 整份序列化（`encodeDefaults = true`），包括：
消息队列开关、MAX 拦截、上下文预警与门限、主题/调色板、SSH 工作区配置、助手上的子代理开关与本地工具。

`app_language` 不在 `Settings` 里（`attachBaseContext` 要同步读，只能放 SharedPreferences），
所以备份时单独写进 `settings.json` 的 `appLanguageTag` 字段，恢复时在 `applyPendingRestore` 里回写 SharedPreferences。

`files/workspaces/` 不参与备份：工作区文件可能很大，且更换设备后 proot 路径会变，按需自行导出。

## 自动压缩（fork 新增）

助手 → 记忆 → 「自动压缩」：

| 项 | 说明 |
|---|---|
| 触发阈值 | `WARN` 或 `MAX`，取模型上配置的两个上下文门限 |
| 压缩后保留 | 按消息条数或 token 数；token 按字符估算（CJK 约 1 字 1 token，其它约 4 字符 1 token），只数最后一个检查点之后的内容，工具输入输出也算 |
| 触发时机 | 写入用户消息前后各判断一次，工具循环里每步结束后再判断一次（`autoCompressLoop`）；判据取「真实 usage」与「可见内容估算」的较大者——usage 看不到刚追加、还没发出去的消息，估算看不到工具定义 |
| 压缩目标 | 按当前占用的 50%（`AUTO_COMPRESS_TARGET_RATIO`）折算，不按门限压满：压满就贴着线，下一轮加一条消息立刻再次触发；TOKENS 模式的保留量也夹到这个预算以内 |
| 上下文已满 | 到达 MAX 后确认发送会先弹窗，确认后压缩再发送 |
| 效果 | 与手动压缩完全一致：调用同一个压缩请求，在原位置插入 `isContextCheckpoint` 摘要消息 |
| 原文 | 压缩只改变发给模型的上下文。切在一条消息中间时，检查点记下 `contextStartMessageId` / `contextStartPartIndex`，会话里存的原文保持完整，聊天页可以往回翻；被切走的前半段只存在于摘要里 |
| 界面 | 不弹浮窗，复用生成中的加载球与 `processingStatus`，显示「正在压缩……」 |

实现位置：

- `data/model/AutoCompress.kt`：阈值/目标枚举、`resolveContextUsage`（门限判据）、`keepRecentContextForTokens`
- `ai/ui/Message.kt` 的 `limitContext`：真正的请求裁剪点，跳过检查点之前的消息、按条数滞回截断、再按检查点里的 part 切分点丢掉已进摘要的前缀
- `Assistant` 新增 `autoCompress`、`autoCompressThreshold`、`autoCompressTarget`、`autoCompressTargetValue`
- `ChatService.autoCompressIfNeeded`：在 `sendQueuedMessage` 的 job 里、写入用户消息之前执行；`compressConversation` 与它共用 `compressSession`
- 开启自动压缩后，MAX 拦截（`sendMessage` / `dispatchNextQueuedMessage`）先放行，由这一轮生成前的压缩处理；压缩失败只记日志，不阻塞回复

## 品牌与界面调整（fork）

- 应用图标：上游 mipmap 各密度 PNG（`ic_launcher` / `_background` / `_foreground`）整体反色，原版是深底白兔，现在是浅底黑兔；`ic_launcher_monochrome` 保持原样（主题图标靠 alpha 取形，反色会变透明）
- 关于页：标题改为 L3Hub，注明基于 RikkaHub；链接改为本 fork 仓库与 LICENSE，另保留「上游项目」指向 `rikkahub/rikkahub`
- 模型编辑：删掉「上下文长度」整段，上下文窗口只保留 WARN / MAX 两个预警门限
- 界面偏好：新增「上下文预警进度条」开关（`DisplaySetting.showContextMeter`），关闭后输入框上方的进度条整行隐藏
- 思考卡标题：折叠时用思考内容的第一句当标题（`DisplaySetting.showThinkingSummary`，默认开），没有正文或关掉开关就退回「思考了 N 秒」；截句是 `utils/MarkdownUtils.kt` 里的纯函数 `extractThinkingSummary()`，带单测
- 压缩检查点：分隔线默认折叠时露出两行摘要预览，展开可看完整摘要并编辑/删除
- 任务树：工具调用改为等宽字体代码块，输入按 JSON 格式化、shell 输出拆出 exit code 与 stdout，长内容折叠，可逐条展开

## 侧边栏改版（fork）

- 抽屉底部改为 ChatGPT 风格：左侧大胶囊「新聊天」，右侧用户头像；头像点击进入设置，并带一段从抽屉飞向页面顶部的过渡动画
- 顶部用户信息（头像/昵称/问候）移除，昵称和头像改到「设置 → 偏好设置 → 通用 → 个人资料」；设置首页顶部改为紧凑标题栏 + 居中大头像，飞行动画正好落在这里
- 会话列表的日期分组可点击折叠/展开；折叠后只隐藏会话，不再吞掉下一个日期标题，也不留空白间隙
- 去掉「图像生成」入口；助手、收藏、统计三个入口改为「AI 翻译」下面的三行

## 后台运行与视图增强（fork）

- 通知设置新增「请求电池优化白名单」：已加入白名单时显示状态，未加入时点击拉起系统授权（没有弹窗的系统退回电池优化列表页）；对应 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限
- 消息视图双层折叠：最后一段正文之前的块整体折叠成「展开前面的 N 步」，展开后思考/工具仍按上游方式折叠；正文前只有一条工具循环链（合并成一个 ChainOfThought 块）时同样折叠，纯思考块仍交给上游折叠；生成中与含待审批工具时不折叠
- 子代理改为后台执行：`dispatch_sub_agent` 立即返回，子代理跑完后结果自动投递回父会话；父会话正在生成时先缓冲，空闲后合并成一条用户消息投递

## 性能：只出的那个包按 release 方式编译

`app/build.gradle.kts` 里把 debug 变体改成 `isDebuggable = false`，包名（`com.rikkahub.l3hub.wfadnf.debug`）
和 debug 签名都不变，所以还是覆盖安装、数据不丢。非调试包在安装时会被 ART 做 AOT 编译，也不加载调试钩子，
启动和滚动明显快于调试构建，体积也从 57 MB 降到 42 MB。

`optimization { enable = true }`（R8）默认注释掉了：这台机器上 R8 单步就要 8 分钟，改一行代码再出包太慢。
需要最终版时再打开它，或者交给 CI 跑（`tools/build-apk.sh` 只出包，不跑 lint vital）。

另外两处小优化：

- `SshWorkspaceBackend.listFiles` 不再对目标目录多 stat 一次；含软链接的目录（如 `/usr/bin`）
  改成一条 `exec` 一次性问出哪些子项是目录，服务器不允许 exec 时退回逐个 stat
- `Conversation.lastContextTokens()` 流式输出每个 token 都会调用，改成不再复制整个消息列表

## Coding Agent 第一轮：检索工具与只读命令免审批（fork 新增）

- 新增 `workspace_list` / `workspace_glob` / `workspace_grep`。三者都走工作区 backend 的 `listFiles` + `exportAbsoluteFile`，本地 Rootfs 与 SSH 工作区共用同一套实现；遍历会跳过 `.git`、`node_modules`、`build` 等目录，条目数、层数、单文件大小都有上限。
- `workspace_read_file` 增加 `offset` / `limit`（1-based 行号）与返回里的 `totalLines` / `truncated`，大文件可以分段读，不再整文件塞进上下文。
- `workspace_shell` 的只读命令（`ls`、`cat`、`grep`、`rg`、`find`、`git status/diff/log` 等）不再逐条询问。命令替换、重定向、子 shell、后台运行、`find -exec/-delete`、`git` 写操作等仍按原策略要求审批；判定逻辑在 `WorkspaceCommandPolicy`，纯函数，有单测。
- 系统提示里同步说明新工具与免审批范围。

## 玻璃底、选项卡透明度与多色背景（fork）

显示设置 → 「背景特效」选 BLUR / GLASS 后，设置页的开关行、填充色调按钮、选项卡（选项按钮）、
对话页顶栏的图标按钮都带一层毛玻璃底；同一页下面的「玻璃透明度」控制浓度（0%–100%，默认 35%）。

- 全局开关仍然是 `DisplaySetting.enableBlurEffect`：关掉时这些组件**一条 haze 都不挂**，
  也不改透明度，直接回到原来的实色 —— 低端机不该为一点美化付代价
- 没有 haze 源的层级（设置页的背景只有一层纯色）只画半透明底加一道描边：
  纯色背景糊一遍还是同一个颜色，省掉的是白花的 GPU 时间，视觉上没有差别
- 顶栏图标按钮原来读 `blurOpacity`（气泡/输入框用），现在读 `glassOpacity`
- 只给固定层级加效果，**不给列表每一项挂 `hazeSource`**：滚动时每项重糊一次会明显掉帧

改动文件：

| 文件 | 说明 |
|---|---|
| `ui/components/ui/GlassSurface.kt` | 新增：`Modifier.glassSurface`、`glassColor`、`glassSegmentedButtonColors`、`LocalGlassHazeState` |
| `data/datastore/Settings.kt` | `DisplaySetting.glassOpacity`（默认 0.35，跟着 `display_setting` 整份 JSON 走，旧数据缺字段按默认值） |
| `ui/components/ui/CardGroup.kt` | 开关行 / 按钮行 / 表单项的底色改由毛玻璃底铺 |
| `ui/components/ui/ConnectedChoiceButtons.kt` | 选项按钮组底色走玻璃透明度 |
| `ui/components/ui/shadow/ShadowButtons.kt` | 填充色调按钮（`FilledTonalButton`）底色走玻璃透明度 |
| `ui/components/ai/ChatInputHaze.kt` | 加 `alpha` 参数，让顶栏能单独指定浓度 |
| `ui/pages/chat/ChatPage.kt` | 顶栏导航钮 / 模型胶囊 / 动作胶囊改读 `glassOpacity` |
| 五个 `SingleChoiceSegmentedButtonRow` 页面 | 传入 `glassSegmentedButtonColors()` |
| `ui/pages/chat/MeshGradientBackground.kt` | 多色光斑 + 色相慢漂移 |

主色调用 `primary` 的填充按钮保持实色（半透明会掉文字对比度），只改色调按钮和表面类控件。

### 多色背景

`MeshGradientBackground` 从原来的蓝色系改成四个光斑（蓝 / 青 / 紫品红 / 橙琥珀），
色相另走一圈 31 秒的慢时钟来回摆动 ±10~18°，饱和度与明度写死，
所以换色不换亮度，深浅主题都不会忽明忽暗：

| 主题 | 底色 | 光斑明度 / 饱和度 | 光斑 alpha |
|---|---|---|---|
| 深色 | 靛紫 → 近黑 `#191428`→`#07060A` | 0.58–0.66 / 0.52–0.62 | 0.22–0.44 |
| 浅色 | 淡藕荷 → 纯白 `#EAE4F8`→`#FFFFFF` | 1.0 / 0.40–0.60 | 0.34–0.55 |

- 深色压明度加低 alpha，叠在近黑底上不会糊成一片；暖色光斑 alpha 只给 0.22，负责色彩参照不抢戏
- 浅色明度给满但饱和度封顶 0.6，是淡彩不是荧光
- 光斑渐隐从「透明黑」改成「同色 alpha = 0」，边缘不再发灰
- 仍是 20fps 节流；`animate = false`（生成中、滚动中）和切后台时不空转；
  色相漂移和位置漂移共用同一份相位状态，没有新增定时器

## 调色板（fork 新增）

设置 → 偏好设置 → 主题 → 「调色板」：内置预设 + 逐个角色自定义，浅色/深色各存一份，管住全应用的配色。

- 优先级：`dynamicColor`（跟随系统动态取色）> 调色板 > `themeId`（预设主题 / 自定义主题）。
  动态取色开着时调色板整块不生效，页面顶部同一处就能关掉它；默认值仍是上游的「开」，不动老用户
- 四十八个角色全部可改，按五组呈现：主色 / 辅色与第三色 / 表面与背景 / 错误与线条 / 高级。
  「高级」那组（fixed 系列、scrim、surfaceTint）在本项目里引用点都在 1 上下，默认折叠。
  每个字段都可空，**null 表示「没动过、跟着种子派生」**——旧版本存的九色配置照样能读，
  新加的三十九个角色在没被指定时也不会打断原有派生
- 生成方式：`paletteColorScheme()` 用四个彩色种子加中性种子（surface / surfaceVariant）构造 `DynamicScheme`，
  `toColorScheme()` 之后先把表面族交给 `withSurfaceFamily()` 重算，再按用户指定逐个 `copy` 回去
  （高级区直接调容器色时优先）；`onSurfaceVariant` 没被指定时由 `onSurface` 按音调关系派生一档，
  否则改「表面上的文字」只有标题会动
- 表面族由「表层色」接管：`withSurfaceFamily()` 取 surface 的 HCT 色相和彩度（彩度上限 12，
  保持 M3 中性色的观感），并在 scheme 自己算出的音调上**按表面色的深浅整体平移**——
  基准是 M3 的默认表面（浅色 tone 98、深色 tone 6），容器阶梯 94/96/98/100 相对它定。
  只换色相不平移的话，把表面色设成 `#000000`，容器仍是 tone 94 的近白（黑色彩度为 0，
  连色相都没有），而界面上绝大多数容器用的就是 `surfaceContainer*`，看上去就是「改了没反应」。
  `inverseSurface` / `inverseOnSurface`（Snackbar 的反色底）反向平移，避免黑底黑字。
  早先只覆盖 `surface` 一个字段，而应用里 118 处用的是 `surfaceContainer*`，
  它们由中性种子展开、音调钉死，所以浅色下改「表层色」几乎看不出变化。
  「背景色」从此只管自己（聊天页底、Markdown 底），不再偷偷带动整族容器色
- 取色器：`ColorPickerRow`（HSV/RGB 双模式 + 色卡 + 色号输入 + 可选 A 通道滑杆），
  已从 `SettingThemePage.kt` 提到 `ui/components/ui/ColorPickerRow.kt`，两处共用，没有引入新依赖。
  色号输入边输边生效，输入框不会中途被改写（之前每敲一个字符就被 `remember(argb)` 重置，
  粘贴 `#466E7A` 会被立刻改写成 `rgb(70 110 122)`）；失焦只负责把文本规范成标准写法。
  中途会闪一下中间态（`#466` → `#446666`），但保存时拿到的永远是当前输入——
  「保存」按钮不抢焦点，把提交压在失焦上会丢输入
- 正文跟随：`onSurfaceVariant`（全仓 216 处，正文几乎都用它）由 `onSurface` 按 M3 的音调关系
  错开一档推出来（浅色 +20、深色 −10）。不这么做的话，改「表面上的文字颜色」只在标题上看得见，
  正文一动不动，观感就是"没生效"
- 当前生效：调色板页放了一块读 `MaterialTheme.colorScheme` 的色带（背景色外圈、表面色卡片、
  表面变体色方块、两种文字色、主色圆点）。它不读编辑面板里的临时值，保存后不变就是真没进主题
- 色号：`#RRGGBB` / `#AARRGGBB` / `#RGB` 三种写法，解析和格式化在 `ui/theme/ColorHex.kt`，
  纯计算，JVM 单测直接跑。不带 alpha 按不透明处理
- 从色号生成：调色板页顶部填一个色号，`PaletteColors.fromSeed()` 当场展开成深浅两套色卡，
  可以只应用到其中一侧
- 导出 / 导入：`data/export/PaletteSerializer.kt`，外面套项目既有的 `ExportData`（version + type + data），
  导入时也吃裸的 `PaletteSetting` 对象，方便从剪贴板粘一段手写的
- 存盘：`Settings.palette`（`PaletteSetting`），DataStore key `palette`。三个字段都可空：
  `presetId == null` = 不启用，行为与改动前完全一致；`light` / `dark` 为 null 表示该侧用预设种子生成，
  非 null 表示该侧被逐个改过，所以可以只调其中一套

| 预设 | id | 主色种子 | 辅色 | 第三色 | 错误色 |
|---|---|---|---|---|---|
| 默认紫 | `default` | `#6750A4` | `#625B71` | `#7D5260` | `#B3261E` |
| 海洋蓝 | `ocean` | `#116682` | `#4D616C` | `#5D5B7D` | `#BA1A1A` |
| 森林绿 | `forest` | `#4C662B` | `#586249` | `#386663` | `#BA1A1A` |
| 落日橙 | `sunset` | `#8B5000` | `#755843` | `#8C4A60` | `#BA1A1A` |
| 玫瑰 | `rose` | `#8E4955` | `#76565A` | `#785831` | `#BA1A1A` |
| 单色灰 | `monochrome` | `#5C5C5C` | `#5C5C5C` | `#5C5C5C` | `#BA1A1A` |

单色灰单独给中性种子 `#5C5C5C`（其余预设的中性色跟随主色）；错误色保留红，
否则错误提示和普通正文分不开。

改动文件：

| 文件 | 说明 |
|---|---|
| `ui/theme/Palette.kt` | 新增：`PaletteColors` / `PaletteSetting` / `PaletteRole` / `paletteColorScheme()` |
| `ui/theme/PalettePresets.kt` | 新增：六套预设的种子色与展开逻辑 |
| `ui/theme/Theme.kt` | 动态取色之后、`themeId` 之前插入调色板配色 |
| `ui/components/ui/ColorPickerRow.kt` | 新增：从 `SettingThemePage` 抽出的取色行，含色卡与 A 通道 |
| `ui/theme/ColorHex.kt` | 新增：色号解析 / 格式化（纯函数） |
| `data/export/PaletteSerializer.kt` | 新增：调色板导出与导入 |
| `ui/pages/setting/SettingPalettePage.kt` | 新增：预设列表（色块 + 名称）、自定义编辑、恢复默认 |
| `ui/pages/setting/SettingPreferencesThemePage.kt` | 主题分组下加一行「调色板」 |
| `RouteActivity.kt` | `Screen.SettingPalette` 与对应的 `entry` |
| `data/datastore/Settings.kt`、`SettingsFields.kt`、`PreferencesStore.kt` | `palette` 字段、字段表、DataStore key |
| `SettingsFieldsTest` | 样本与 `STORED_KEYS` 补 `palette` |

> `Settings` 加了顶层字段，按本文约定必须同步 `SETTINGS_FIELDS` 和 `STORED_KEYS`，
> 否则 `every settings property has exactly one field` 与 `stored format stays compatible with existing data` 两个用例会挂。

## 全宽组件（fork 新增）

设置 → 偏好设置 → 通用 → 背景特效一栏里的「全宽组件」开关，就在「玻璃透明度」滑杆下面
（`DisplaySetting.fullWidthComponents`，默认关）。
关着的时候每个分支都退回改造前的取值，行为与改动前完全一致。

打开后：

- 气泡不再按内容收缩，左右边缘与消息列表等宽；消息列表的 16dp 左右留白归零
- `MaterialTheme.shapes` 换成 `SquareShapes`（五个档位都是 `RoundedCornerShape(0.dp)`），
  卡片、按钮、对话框、菜单一起变直角；气泡自己的 `uiCornerRadius`、接缝小圆角、输入框内外两层圆角
  都走 `adaptiveShape()`，一起归零
- 贴边的组件（输入框及其外层容器、顶栏两侧按钮组、搜索框）去掉左右留白；让位改用
  `WindowInsets.safeDrawing.only(Horizontal)`，所以刘海、横屏侧边系统栏照样让得开，
  `imePadding()` / `navigationBarsPadding()` 的顺序也没有变
- 设置页的页面留白同样归零（`Modifier.adaptiveHorizontalPadding`），`CardGroup` 分组卡片撑满屏宽

统一入口在 `ui/theme/FullWidthComponents.kt`：`LocalFullWidthComponents` 加
`adaptiveShape()` / `adaptiveCornerRadius()` / `adaptiveHorizontalPadding()` /
`adaptiveHorizontalPaddingValue()` / `fullWidthHorizontalInset()`。主题在 `RikkahubTheme` 里提供这个
CompositionLocal，页面只读它，不各自去判断 `Settings`。

| 文件 | 说明 |
|---|---|
| `ui/theme/FullWidthComponents.kt` | 新增：CompositionLocal、`SquareShapes` 与取值辅助 |
| `ui/theme/Theme.kt` | 提供 CompositionLocal；`shapes` 多一档 `SquareShapes` |
| `data/datastore/Settings.kt` | `DisplaySetting.fullWidthComponents`（默认 false） |
| `ui/pages/chat/ChatList.kt` | 消息列表左右留白归零（保留安全区），搜索框与建议行同步 |
| `ui/components/message/ChatMessage.kt` | 气泡撑满父宽、圆角归零、用户气泡左侧 24dp 归零 |
| `ui/components/ui/ChainOfThought.kt` | 思考/工具卡片撑满父宽、圆角归零 |
| `ui/components/ai/ChatInput.kt` | 输入框贴屏幕边、容器与 TextField 直角、发送/加号按钮直角 |
| `ui/pages/chat/ChatPage.kt` | 顶栏左右留白归零，顶栏胶囊与导航钮直角 |
| `ui/components/ui/CardGroup.kt` | 分组卡片圆角归零（`cardGroupItemShape` 随之变成 `@Composable`） |
| `ui/components/message/ConversationTodoCard.kt`、`ui/pages/chat/ChatDrawer.kt` | 任务卡与抽屉容器圆角归零 |
| 五个 `SettingPreferences*Page.kt`、`SettingProviderPage.kt` | 页面左右留白归零 |
| `res/values{,-zh}/strings.xml` | 开关的标题与说明 |

> 这个开关在 `DisplaySetting` 里，跟着 `display_setting` 整份 JSON 走，
> 所以**不用**改 `SETTINGS_FIELDS` / `PreferencesStore` 的 key —— 只有加在 `Settings` 顶层的字段才要。

默认关闭，打开后仍然保留的内边距（有意为之）：气泡内部文字 10dp、输入框内部的 `inputPadding`、
`CardGroup` 每项内部的 16dp、对话框按钮区。这些是内容离组件边缘的距离，不是组件边缘离屏幕的距离。

## 模型选择弹窗（fork 改版）

所有选模型的入口（聊天输入框、默认模型设置、助手、心跳、记忆整理、图像生成、翻译、连通性测试）共用
`ui/components/ai/ModelList.kt` 的 `ModelListSheet`，弹窗内容换成 `ModelPickerTwoPane` 的双列布局：

- 左栏 88dp：收藏入口 → 分组（各自可折叠，带 ▾）→ 组内供应商 Logo → 「未分组」段。
  长按分组名调整顺序，长按 Logo 进该供应商详情，底部齿轮直接进供应商设置页
- 左上角箭头把整条左栏收成 0 宽，右侧主视图拿满弹窗宽度；收起后同一个箭头移到标题左边
- 标题行右侧是关闭按钮，搜索框占满主视图一整行，带清空按钮。搜索同时匹配**供应商名**、
  模型的 `displayName` 和 `modelId`；命中供应商时连同该供应商的全部模型一起列出，结果卡片会带上供应商名
- 卡片：左侧图标方块，右侧模型名、`WARN x · MAX y`、标签行（供应商覆盖 / 类型 / 模态 / 能力）
- 屏幕宽度 < 360dp 时自动换成紧凑卡片：图标缩小，标签独占一行，名称不再被收藏按钮挤没

> 上游的单列实现（供应商 sticky header + 收藏拖动排序 + 供应商 Badge 横向滚动）在改成双列后
> 就没人调用了，这次随改版一起删掉，`ModelList.kt` 从 1016 行降到 639 行。

| 文件 | 说明 |
|---|---|
| `ui/components/ai/ModelPickerTwoPane.kt` | 重写：可收起的左栏、顶部搜索与关闭、双列布局、窄屏断点、分组排序对话框 |
| `ui/components/ai/ModelList.kt` | `ModelItem` 改成「图标方块 + 名称 / 水位 / 标签」，新增 `compact`、`subtitle` 参数与 `ModelProviderOverrideTag`；删除旧单列实现 |
| `res/values*/strings.xml` | 新增 `model_list_collapse_sidebar`、`model_list_expand_sidebar`、`model_list_no_match` |

## 内置 SSH 服务端（fork 新增）

设置 → Web 服务器 → 「在终端中使用」：打开后可以在系统终端里 `ssh -p 2222 l3hub@127.0.0.1` 直接操作 App。
和 Web 服务器是两个独立开关，但共用同一个前台服务 `WebServerService` 和同一条通知。

- **依赖**：`org.apache.sshd:sshd-core`（版本走 `gradle/libs.versions.toml` 的 `apache-sshd`，该版本此前已被 `:workspace` 的测试用到）。
  `sshd-common` 由它传递引入；SLF4J 输出走已有的 `slf4j-android`，不需要再加 `slf4j-nop`。
- **认证**：只开密码认证，密码就是 Web 服务器的访问密码（`webServerAccessPassword`），**不维护第二份令牌**；
  用户名随便填（示例里写 `l3hub`）。
- **绑定**：默认 `127.0.0.1`，打开「允许局域网访问」才绑 `0.0.0.0`。端口默认 `2222`，和 `8080` 分开。
- **主机密钥**：`filesDir/ssh/hostkey.ser`，第一次启动由 sshd 生成，之后一直复用（RSA，避免 Android 上没有 EdDSA）。
- **通道**：不申请 PTY，按行读命令；shell 请求（`ssh host`）进交互循环，exec 请求（`ssh host conversations`）跑一条就退出。
- **令牌为空时拒绝启动**，通知会被收掉，界面上显示错误。

命令（都直接调 `ChatService` / `ConversationRepository` / `SettingsStore`，不绕 HTTP）：

| 命令 | 作用 |
|---|---|
| `help` | 命令列表 |
| `conversations` | 当前助手的会话列表（id / 更新时间 / 标题） |
| `models` | 已启用提供商的对话模型，`*` 是当前模型 |
| `send <id> <text>` | 发消息，生成在后台继续，用 `tail` 跟 |
| `tail <id>` | 跟会话输出，生成时增量打印，Ctrl+C / 断开结束 |
| `whoami` | 当前账号与助手 |
| `exit` | 关闭会话 |

改动文件：

```
gradle/libs.versions.toml                       无需改动，apache-sshd 已有
app/build.gradle.kts                            implementation(libs.apache.sshd.core)
app/.../ssh/SshCommandParser.kt                 纯函数解析（命令 / 参数缺失 / 未知命令）
app/.../ssh/SshCommandSession.kt                命令实现
app/.../ssh/SshShellCommand.kt                  sshd 的 Command / ShellFactory 实现，按行读写
app/.../ssh/SshServerManager.kt                 Koin 单例：start / stop / isRunning / port / error
app/.../service/WebServerService.kt             两个服务共用前台服务与通知，新增 SSH 相关 action
app/.../di/AppModule.kt                         注册 SshServerManager
app/.../data/datastore/Settings*.kt             三个新字段（默认 false / 2222 / false）
app/.../ui/pages/setting/SettingWebPage.kt      「在终端中使用」卡片
app/.../RikkaHubApp.kt                          只恢复用户自己开过的服务，不会无条件启动
app/src/test/.../ssh/SshCommandParserTest.kt    解析器单测（不依赖 Android）
```

体积：预计 release APK **+1.5–2.5 MB**，debug **+3 MB 左右**。依据是 sshd-core 2.20 与
sshd-common 的 jar 合计约 2.7 MB，R8 会裁掉一部分用不到的算法/解析器但不会全裁；
下次出包时用 `assembleGithubRelease` 前后对比确认。

## 还要做的

1. **签名**：在 `local.properties`（已 gitignore）加四项，之后所有版本都用同一个 keystore，否则无法覆盖升级
   ```properties
   storeFile=/absolute/path/to/your.keystore
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
2. **图标与链接**：`app_name` 已改为 L3Hub，应用图标仍是上游素材；`ui/pages/setting/SettingAboutPage.kt` 里的 GitHub / Discord 链接也仍指向上游
3. **可选：深链和 action 改名**（两版共存时，外部应用触发这些会弹选择器）
   - `AndroidManifest.xml` 的 `rikkahub://` scheme → 你的 scheme，同步改 `res/xml/shortcuts.xml`
   - `AndroidManifest.xml` 与 `RouteActivity.kt` 里的 `me.rerere.rikkahub.action.*`
4. **构建**
   ```bash
   ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleGithubDebug
   ```

## 验证记录

> 下列数据产生于 applicationId 仍为 `com.rikkahub.fork` 时；改名为 `com.rikkahub.l3hub.wfadnf` 后尚未重新出包验证。

```
./gradlew :app:assembleGithubDebug -x :web:buildWebUi   # BUILD SUCCESSFUL
```

产物与体积（同一套代码）：

| 变体 | 体积 |
|---|---|
| `assembleGithubDebug` universal（arm64 + x86_64，未跑 R8） | 55 MB |
| `assembleGithubRelease` arm64-v8a（R8） | 13.9 MB |
| `assembleGithubRelease` x86_64（R8） | 14.0 MB |
| `assembleGithubRelease` universal（R8） | 16.6 MB |

debug 里 dex 有 38 个、release 压缩后只剩 2 个，差的就是 R8。上面 release 的体积不含 web-ui 静态资源（构建时用了 `-x :web:buildWebUi`）。

- `aapt2 dump badging` → `package: name='com.rikkahub.fork'`（debug 为 `com.rikkahub.fork.debug`）
- `apksigner verify` → V2 签名有效
- 合并后 authorities：`com.rikkahub.fork.debug.fileprovider` / `.documents` / `.androidx-startup`，与原版不冲突
- APK 内无 Firebase 类

> `-x :web:buildWebUi` 只是跳过 React 前端的 pnpm 构建；正式出包要去掉这个参数，否则 APK 里没有 web UI 静态资源。

## 发版：交给 GitHub Actions

本机不跑 Gradle（Termux 上编译会把机器拖死），出包和发版都放在 runner 上。
两条线共用 `build-apk.yml`，按 tag 形态分流：

| tag | 变体 | 包名 | Release |
|---|---|---|---|
| `v3.1.1` | `githubRelease` | `com.rikkahub.l3hub.wfadnf` | 正式版（非 prerelease，自动成为 latest） |
| `v3.1.1-preview.102` | `githubDebug` | `com.rikkahub.l3hub.wfadnf.debug` | Pre-release |

两个包是两个独立应用，可以同装在一台手机上，数据互不影响（预览版桌面名是 L3Hub Preview）。

步骤：

1. 改 `app/build.gradle.kts` 的 `versionCode` / `versionName`（每次各 +1，例如 281 / `L.3.1.2.103`），连同改动一起提交
2. 打 tag 推上去：`git tag v3.1.1 && git push origin v3.1.1`（预览版就是 `v3.1.1-preview.102`）。
   手动触发时如果填了 tag，checkout 用的就是这个 tag 的提交，不会拿分支 HEAD 出的包挂到 tag 上；
   `gh release create` 带 `--verify-tag`，tag 不存在直接报错，不会现造一个
3. `build-apk.yml` 自动跑：checkout（含子模块）→ pnpm 装 web-ui → `tools/build-apk.sh`
   （按 `VARIANT` 选 `:app:assembleGithubRelease` / `:app:assembleGithubDebug`，跳过 lint vital）→ 重命名产物 → 建 Release

产物是 `L3Hub-<tag>-arm64-v8a.apk` 加同名 `.sha256`，arm64-v8a，R8 已开。
Release notes 由流程按「上一个 tag..本 tag」的提交自己生成——本仓库不走 PR，
GitHub 自带的 `--generate-notes` 只会给一行 Full Changelog。

### 正式版的签名

正式版密钥不在仓库里，走 repository secrets：

| secret | 内容 |
|---|---|
| `ANDROID_RELEASE_KEYSTORE` | `l3hub-release.jks` 的 base64（单行） |
| `ANDROID_RELEASE_STORE_PASSWORD` | store 口令 |
| `ANDROID_RELEASE_KEY_ALIAS` | `l3hub-release` |
| `ANDROID_RELEASE_KEY_PASSWORD` | key 口令（与 store 相同） |

流程把 keystore 还原到 `$RUNNER_TEMP`，再把 `storeFile` / `storePassword` / `keyAlias` / `keyPassword`
写进 `local.properties`——`app/build.gradle.kts` 的 release 签名只认这个文件。
证书 SHA-256 指纹是 `0E:D1:33:05:52:46:D7:23:18:88:E7:21:97:F2:9F:A6:16:A6:53:67:DE:87:50:42:10:8F:9D:41:7B:58:35:42`，
每次发版都会写进 Release notes，装之前可以核对。这个值也写死在 `build-apk.yml` 的 Verify APK 步骤里：
正式版签名证书对不上就红，免得 secret 被换成别的 key 之后一路绿灯发出去。换密钥要同步改这一行。

密钥丢了就再也发不出能覆盖安装的更新：本机备份在 `~/keystore-backup/`（jks + 口令），务必另外存一份。
预览版不走这套：它用仓库里公开的 `keystore/l3hub-debug.jks`，泄露了也无所谓，
所以 `build-apk.yml` 的还原密钥那步对预览版是直接跳过的（早先那个 `ANDROID_DEBUG_KEYSTORE`
secret 没人读，已经从流程里去掉，仓库设置里可以一并删掉）。

几点：

- `ci.yml` 只跑单测、只跟分支；tag 触发的构建由 `build-apk.yml` 负责，同一份代码不重复跑
- `:terminal` / `:workspace` 带 CMake，配置阶段需要 NDK 28.2.13676358（约 700MB）。两个 workflow 都缓存了这个目录；
  不缓存的话每次现下，下坏过一次（`Error reading Zip content from a SeekableByteChannel`），整轮直接红
- 同一个 tag 重跑不会失败：release 已存在时覆盖资产、刷新说明，prerelease 标记也跟着 tag 走
- 只想要个包不发版：`workflow_dispatch` 触发 `build-apk.yml`，tag 留空，产物在 run 的 artifact 里；
  也可以在 inputs 的 `variant` 里手动指定出哪个变体
- 正式版是 R8 全量混淆出来的，`proguard-rules.pro` 覆盖不到的反射装配（sshd、Koin、serialization 之类）
  只有装到真机才看得出来，发版前先自己装一遍跑核心功能

## 和上游同步

```bash
git remote add upstream https://github.com/rikkahub/rikkahub.git
git fetch upstream
git rebase upstream/master
```

改动尽量放在独立文件里；上面列的文件是冲突热点，能不加就别加。

## 许可证

AGPL-3.0。分发 APK 或对外提供网络服务时，必须一并提供完整源码。
