# 在千问办公中安装本 Skill

本目录是给**千问办公（QwenWork）**用的技能包，不是 Cursor / Claude Code 专用配置。

官方说明：[千问办公 · 技能](https://help.aliyun.com/zh/qwenwork/skills)、[连接器](https://qwenwork.cn/docs/features/connectors)

## 一、先接通钉钉

1. 打开千问办公（钉钉内入口或独立客户端均可）。
2. 左侧 **扩展 → 连接器 → 市场**，找到 **钉钉**，安装并完成「登录钉钉账号」授权。
3. 在 **已安装** 中确认钉钉连接器已开启。
4. **新建一个对话任务**（刚改连接器后，旧对话可能读不到工具）。

未连接钉钉时，本 Skill 无法真正发起 OA。

## 二、安装本 Skill（任选一种）

### 方式 A：扩展里上传（推荐）

1. 将本文件夹打成 zip（zip **根目录**需能看到 `SKILL.md`，不要多包一层无关目录）。
2. 千问办公：**扩展 → 技能 → 安装技能**，上传 zip（或上传 `SKILL.md` 及 `references/`、`INSTALL.md` 等辅助文件）。
3. 安装成功后，在「我的安装」或输入 `/` 应能看到 `dingtalk-qwen-oa-bulletin`。

文件夹应类似：

```text
dingtalk-qwen-oa-bulletin/
├── SKILL.md                 # 必填
├── INSTALL.md
├── config.example.yaml
└── references/
    ├── form-mapping.md
    └── examples.md
```

### 方式 B：对话里让千问办公安装

若技能已放到可访问的 Git / 网盘地址，在新对话中发送：

```text
请帮我把 <本技能目录或仓库地址> 安装到 ~/.qwenworkcn/skills/ 目录
```

### 方式 C：企业空间共享

企业开通企业空间后，有权限的成员可将本技能上传到**企业空间**，供组织内同事安装（不会自动发到公共广场）。

### 方式 D：本机目录（桌面端）

将整个 `dingtalk-qwen-oa-bulletin` 文件夹复制到：

```text
~/.qwenworkcn/skills/dingtalk-qwen-oa-bulletin/
```

Windows 示例：`C:\Users\<你>\.qwenworkcn\skills\dingtalk-qwen-oa-bulletin\`

## 三、配置本企业模板（建议）

1. 复制 `config.example.yaml` 为 `config.local.yaml`（或直接改 `references/form-mapping.md`）。
2. 填入本企业「行政通报」的 `processCode`。
3. 首次成功拉到 Schema 后，把各控件的真实 `name` 填进 `references/form-mapping.md` 的「本企业映射」表，后续提单更稳。

获取 processCode：在已连接钉钉的对话中让助手「列出我可发起的审批表单并找出行政通报」。

## 四、验证是否生效

新建对话，任选其一：

```text
帮我提一个行政通报审批，标题测试，正文：技能安装验证，先不要真正提交，只做到摘要确认。
```

或输入 `/`，选择 `dingtalk-qwen-oa-bulletin`。

预期：助手按 Skill 追问/映射字段，并在真正发起前给出确认摘要；未授权钉钉时应提示先连接器登录。

## 五、与本仓库的关系

本技能包放在项目 `.claude/skills/dingtalk-qwen-oa-bulletin/` 仅便于版本管理与分发。  
**使用时安装进千问办公**；不要指望只放在 Cursor 项目里就会在钉钉千问办公里生效。
