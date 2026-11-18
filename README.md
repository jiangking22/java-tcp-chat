# Java TCP 多人聊天室

用 JDK 标准库运行的命令行聊天室。客户端通过 TCP 连接服务器，注册或登录后可以切换房间、向同房间广播、私聊和查看房间成员。工程包含真实 TCP 集成测试和并发压测工具。

## 在 Windows 上运行

需要 JDK 21。将 `JAVA_HOME` 指向 JDK 根目录，或将 JDK 的 `bin` 目录加到 `PATH`。

首先在项目目录编译，然后启动服务器。

```powershell
.\chat.ps1 build
.\chat.ps1 server
```

另开两个或三个终端，进入同一项目目录，各启动一个客户端。

```powershell
.\chat.ps1 client
```

按提示输入不同的单词昵称和实验用密码。直接输入文本即可发送。若 Windows 的脚本执行策略阻止运行，可以对该次调用使用 `powershell -NoProfile -ExecutionPolicy Bypass -File .\chat.ps1 build`，其他动作把末尾的 `build` 换成对应命令。

| 客户端命令 | 用法 |
| --- | --- |
| `/join lab` | 加入 `lab` 房间 |
| `/leave` | 返回默认房间 `general` |
| `/who` | 查看当前房间的在线成员 |
| `/msg alice 你好` | 向在线用户 `alice` 发私信 |
| `quit` | 退出客户端 |

服务端默认绑定 `127.0.0.1:5000`，便于在同一台电脑演示。跨机实验时，在服务端终端用 `$env:CHAT_HOST` 指定可信局域网网卡的 IP，用 `$env:CHAT_PORT` 更改聊天端口。假设服务端配置为 `192.168.1.10:5500`，客户端运行 `.\chat.ps1 client -ServerHost 192.168.1.10 -Port 5500`。指标端口默认在本机的 `9100`，可通过 `$env:METRICS_PORT` 更改。服务端在当前窗口运行，按 Ctrl+C 停止。

## 验证与压测

```powershell
.\chat.ps1 test
```

测试会重新编译，并使用实际的回环 TCP 连接验证登录、多人广播、私聊、房间隔离、中文和表情字符传输、超长输入处理以及断线清理。验收规则见 [实验规约](.agents/skills/java-chat-acceptance/SKILL.md)。

启动服务端后，在另一个终端发起一次 20 客户端、2 房间、总计每秒 10 条消息的 10 秒压测。

```powershell
.\chat.ps1 load -Clients 20 -Rooms 2 -MessagesPerSecond 10 -Seconds 10
```

压测输出包含连接数、发送和接收数量、吞吐和端到端延迟百分位。若更改了聊天端口，为 `load` 加上相应的 `-Port`。服务器运行期间可在本机访问 `http://127.0.0.1:9100/metrics` 查看指标。

本次 Windows 回环验证的原始输出见 [运行记录](evidence/verification.txt)。

## 协议与实验边界

TCP 文本使用 UTF-8，支持 LF 和 CRLF 行结束符。客户端到服务端的每行正文最多 4096 字节，CR 只用于 CRLF 行结束符。遇到超长、非法编码或无效分隔符时，服务器关闭对应连接。密码以加盐的 PBKDF2 摘要保存在服务进程内存中，重启后重新注册。TCP 通信为明文，跨机演示应在可信局域网使用实验专用密码。

复用的源码范围、固定上游版本和本项目改动见 [来源与复用说明](ATTRIBUTION.md)，许可证见 [LICENSE](LICENSE)。
