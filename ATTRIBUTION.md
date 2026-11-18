# 开源复用来源

本课程项目以 Atharv Sable 的 [Multi-Client Chat Server in Java](https://github.com/goldentree831120-ui/Multi-Client-Chat-Server-in-Java.) 为基线，固定上游提交 [3f8d11d](https://github.com/goldentree831120-ui/Multi-Client-Chat-Server-in-Java./commit/3f8d11d4eef5dcb4c9e27eeef71f19700334c1eb)。上游源码和许可证按 Git blob 哈希核对，原始版权信息与 MIT 许可全文见 [LICENSE](LICENSE)。

复用上游的 TCP 客户端、服务端与连接处理器，HTTP 指标实现、集成测试和压测工具。课程项目增加了 UTF-8 传输约束、帧大小检查、默认回环绑定、内存密码摘要、Windows 运行入口和独立 TCP 验收测试。开发规约与测试场景位于 [.agents/skills/java-chat-acceptance/SKILL.md](.agents/skills/java-chat-acceptance/SKILL.md)。
