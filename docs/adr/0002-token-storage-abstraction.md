# ADR-0002: Token 存储抽象为接口（生产加密实现 / 测试内存实现）

- **状态**: Accepted (2026-08-12)
- **相关**: T4（#5 认证）、plan.md token 安全红线
- **决策者**: 用户 + 实施者（grill-with-docs 会话拍板）

## 背景

Token 需加密存储。候选 EncryptedSharedPreferences（security-crypto 1.1.0-alpha06）在 Robolectric 环境可能不可测（alpha 版 + 无真实 AndroidKeyStore）。

## 决策

抽象 `TokenStorage` 接口：

- **生产实现**: EncryptedSharedPreferences（加密）
- **测试实现**: 内存 Map（纯 JVM 单测覆盖存储逻辑）

## 理由

- 单测能覆盖序列化/降级标记/刷新语义
- 未来换 StrongBox/Keystore 实现不影响调用方
- 是 plan.md「token 安全红线」的正式边界：token 只经接口进出，禁止散落

## 范围

- 接口放 `core:github-auth`，生产/测试实现随模块
- 接口职责：读写 access/refresh token + PAT + 会话元数据（降级标记）
