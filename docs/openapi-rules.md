# OpenAPI 接口文档注解规则

## 基本要求

- 类级别必须加 `@Schema(description = "xxx")`
- 每个字段必须加 `@Schema(description = "xxx")`
- 所有数值类型字段（`Integer`、`Long`、`int`、`long`）**必须加 `example`**，否则 SpringDoc 会自动填充最大值
- `description` 使用中文

## 字段注解示例

```java
// ✅ 普通字段 — 加 description + example
@Schema(description = "用户名", example = "admin")
private String username;

// ✅ 枚举值字段 — 在 description 中说明取值含义
@Schema(description = "性别（0=未知 1=男 2=女）", example = "1")
private Integer gender;

// ✅ ID 字段（新增不传、修改必传）— 加 nullable = true，不加 requiredMode
@Schema(description = "用户ID（新增不传，修改必传）", example = "1", nullable = true)
private Long userId;

// ✅ 必填字段 — 加 requiredMode
@Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan")
private String username;

// ❌ 错误 — 数值字段缺少 example，会显示为 1073741824
@Schema(description = "状态（0=正常 1=停用）")
private Integer status;

// ❌ 错误 — 新增接口的 ID 字段标为必填，会误导前端
@Schema(description = "用户ID（修改时必填）", example = "1")
private Long userId;
```

## Controller 注解示例

```java
@Tag(name = "用户管理", description = "系统用户增删改查接口")
@RestController
@RequestMapping("/system/user")
public class SysUserController {

    @Operation(summary = "分页查询用户列表")
    @PostMapping("/list")
    public Result<PageResult<UserVO>> list(...) { ... }
}
```

## 请求体规范

**禁止使用 `Map<String, Object>` 作为 `@RequestBody`**，必须定义专用 DTO 类并加 `@Schema` 注解。

理由：`Map` 无法在 Swagger UI 中展示字段结构，前端无法得知需要传哪些参数。

```java
// ❌ 错误 — Map 无法生成接口文档
public Result<Void> resetPwd(@RequestBody Map<String, Object> params) { ... }

// ✅ 正确 — 使用 DTO，字段一目了然
public Result<Void> resetPwd(@Valid @RequestBody ResetPwdDTO dto) { ... }
```
