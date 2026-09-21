# Finexy 飞牛 fnOS 原生 fpk

不依赖 Docker，直接以原生进程运行（纯静态 Go 二进制 + SQLite）。本目录只新增文件，不改动上游代码，便于同步 fork。

## 构建

依赖：`go`、`gcc`、`node`/`npm`、`python3`（Pillow，用于生成图标）、[`fnpack`](https://developer.fnnas.com/docs/cli/fnpack/)。

```bash
FNPACK=/path/to/fnpack deploy/fnos/build-fpk.sh          # 完整构建
FNPACK=/path/to/fnpack deploy/fnos/build-fpk.sh --skip-build  # 复用已有 ./ezbookkeeping 与 ./dist
```

产物：`deploy/fnos/out/finexy-<基础版本>-<构建号>-x86.fpk`（仅 x86_64）。包版本为 `<package.json 版本>-<N>`，N 保存在 `deploy/fnos/BUILD_NUMBER`，每次构建自动加 1，请随提交一起保存。

## 安装向导

- 服务端口（默认 8080）
- 外部访问地址（可选；通过域名/穿透访问时填写，如 `https://finexy.example.com`）
- 签名密钥（留空自动随机生成，升级时保留）
- DeepSeek API Key / 模型（可选，留空不启用 AI 文本记账）

配置写入 `${TRIM_PKGVAR}/finexy.env`，数据库、日志、附件均在 `${TRIM_PKGVAR}` 下，升级不丢失。除签名密钥外，安装向导中的设置项都可在“应用设置”中修改，保存后需重启应用生效（DeepSeek Key 留空表示保持当前值）。也可直接编辑该文件后重启应用。

## 与 Docker 版的差异

- 不含 OCR 服务（Python + PaddleOCR 体积大，不适合原生打包）。如需 OCR，另行用 Docker 部署后在 `finexy.env` 中加入 `EBK_OCR_SERVER_URL=http://<host>:8000`。
- MCP 默认开启（`/mcp`，需认证）。

## 手机访问

应用以“端口入口”注册到飞牛桌面，飞牛 App 可从应用列表打开。也可直接使用 Finexy 自带 Android App / PWA 连接服务地址。
