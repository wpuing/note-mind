"""知识图谱客户端（规划 · Phase C）。

离线：小模型抽取实体/关系 → Neo4j/Nebula。
在线：封装为 Agent Tool，供 LangGraph 与向量检索并行调用。
权限与会话仍由 Java 网关侧保证；本包不对浏览器暴露。
"""
