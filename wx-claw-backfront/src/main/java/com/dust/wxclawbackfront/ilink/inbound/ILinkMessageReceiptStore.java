package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ILinkMessageReceiptStore {
    private final JdbcTemplate jdbcTemplate;

    public boolean claim(BotRuntimeKey runtimeKey, WeixinMessage message) {
        if (message.getMessage_id() == null) {
            return true;
        }
        return jdbcTemplate.update("""
                        INSERT IGNORE INTO ilink_message_receipt
                            (tenant_id, bot_id, message_id, from_user_id, create_time_ms, received_at)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                runtimeKey.tenantId(), runtimeKey.botId(), message.getMessage_id(),
                message.getFrom_user_id(), message.getCreate_time_ms()) == 1;
    }

}
