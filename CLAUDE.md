# CLAUDE.md

## Project Overview

SpringBoot + React + Ant Design enterprise admin template with RBAC permission system.

- **BackEnd/**: Spring Boot 3.4.5 + Java 21 + MyBatis-Plus + MySQL
- **FrontEnd/**: React 19 + Ant Design 6 + Vite 8 + TypeScript 6 + Tailwind CSS 4 + Redux Toolkit

## Code Formatting Rules

**IMPORTANT**: After writing or editing any file under `FrontEnd/`, immediately run:

```bash
cd FrontEnd && npx prettier --write <file_path>
```

This is necessary because AI-generated code does not trigger VS Code's `formatOnSave`. The project has a `.prettierrc` with specific rules (2-space indent, 120 print width, single quotes, Tailwind CSS plugin, etc.) that must be enforced.

Prettier config location: `FrontEnd/.prettierrc`

## Conventions

- Use `@/` path alias (maps to `src/`) in frontend imports
- Frontend pages follow `src/pages/<path>/index.tsx` convention for auto-routing
- Backend follows Controller → Service → Mapper layered architecture
- All API responses use `Result<T>` wrapper: `{code, msg, data}`
- Permissions use string format: `system:user:list`, `system:user:add`, etc.

## OpenAPI 注解规则

项目使用 SpringDoc OpenAPI 生成接口文档，**所有实体、DTO、VO 类必须添加 `@Schema` 注解**。

- 禁止 `Map<String, Object>` 做 `@RequestBody`，必须定义专用 DTO
- 详细示例参考：`docs/openapi-rules.md`

## Tailwind CSS Rules

项目使用 Tailwind CSS 4 作为样式方案，**生成代码时必须使用 Tailwind class**。
