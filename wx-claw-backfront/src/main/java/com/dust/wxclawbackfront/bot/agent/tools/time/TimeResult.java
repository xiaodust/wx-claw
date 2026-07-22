package com.dust.wxclawbackfront.bot.agent.tools.time;

import lombok.Getter;

@Getter
public final class TimeResult {

    private final String zoneId;
    private final String isoTime;
    private final String formattedTime;
    private final String replyText;
    private final String errorMsg;

    public TimeResult(String zoneId, String isoTime, String formattedTime, String replyText, String errorMsg) {
        this.zoneId = zoneId;
        this.isoTime = isoTime;
        this.formattedTime = formattedTime;
        this.replyText = replyText;
        this.errorMsg = errorMsg;
    }
}

