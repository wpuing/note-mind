package com.notemind.application.service.system;

/** 系统配置键（与 t_system_config.config_key / 种子 SQL 对齐） */
public final class SystemConfigKeys {

    /** 工具类禁止实例化 */
    private SystemConfigKeys() {}

    public static final String DEFAULT_KNOWLEDGE_BASE_ID = "default.knowledge_base_id";
    public static final String DEFAULT_EMBEDDING_MODEL_ID = "default.embedding_model_id";
    public static final String DEFAULT_CHUNK_STRATEGY_ID = "default.chunk_strategy_id";
    public static final String DEFAULT_RETRIEVAL_STRATEGY_ID = "default.retrieval_strategy_id";
    public static final String PAGE_MAX_SIZE = "page.max_size";
    public static final String UPLOAD_EXTENSIONS = "upload.extensions";
    public static final String PROMPT_SCENARIOS = "prompt.scenarios";
    public static final String RETRIEVAL_DEFAULT_TOP_K = "retrieval.default_top_k";
    public static final String RETRIEVAL_MAX_TOP_K = "retrieval.max_top_k";
    public static final String LLM_CHAT_TEMPERATURE = "llm.chat_temperature";
    public static final String LLM_JUDGE_TEMPERATURE = "llm.judge_temperature";
    public static final String CHAT_SESSION_LIST_LIMIT = "chat.session_list_limit";
    public static final String CHAT_MESSAGE_LIST_LIMIT = "chat.message_list_limit";
}

