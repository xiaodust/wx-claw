package com.dust.wxclawbackfront.ai.tools.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class TimeHandler {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String zoneId;

    public TimeHandler(@Value("${wxclaw.ai.time.zone:Asia/Shanghai}") String zoneId) {
        this.zoneId = zoneId;
    }

    public TimeResult now() {
        String zid = zoneId == null || zoneId.isBlank() ? "Asia/Shanghai" : zoneId.trim();
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneId.of(zid));
            String formatted = now.format(DISPLAY_FORMATTER);
            String reply = "现在是 " + formatted + "（" + zid + "）";
            return new TimeResult(zid, now.toString(), formatted, reply, null);
        } catch (Exception ex) {
            return new TimeResult(zid, null, null, null, ex.getMessage());
        }
    }
}

