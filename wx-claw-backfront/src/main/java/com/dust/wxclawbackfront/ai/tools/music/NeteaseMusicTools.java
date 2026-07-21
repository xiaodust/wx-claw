package com.dust.wxclawbackfront.ai.tools.music;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolProvider;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import com.dust.wxclawbackfront.ai.tools.shared.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网易云音乐工具
 * 提供歌曲搜索和播放链接获取功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(NeteaseMusicClient.class)
public class NeteaseMusicTools implements AiToolProvider {

    private final NeteaseMusicClient musicClient;
    private final AiToolInvocationStore invocationStore;
    private final ILinkMessageSender messageSender;

    @Override
    public Object getTool() {
        return this;
    }

    @Override
    public int getOrder() {
        return 32; // 在搜索工具(30)之后，知识库(35)之前
    }

    /**
     * 搜索歌曲并直接向用户发送音乐链接
     */
    @Tool(name = "music_search",
          description = "搜索歌曲并直接向用户发送音乐链接。当用户想听歌、播放音乐、搜索歌曲时使用。参数keyword为搜索关键词，可以是歌曲名、歌手名、或歌手名+歌曲名的组合。搜索成功后会自动向用户发送可播放的音乐链接。")
    public MusicSearchResult searchSong(
            @ToolParam(description = "搜索关键词，如歌曲名、歌手名、或'歌手名 歌曲名'") String keyword) {
        log.info("AI调用 music_search: keyword={}", keyword);

        if (keyword == null || keyword.isBlank()) {
            return new MusicSearchResult(false, null, "搜索关键词不能为空");
        }

        // 搜索歌曲
        List<NeteaseMusicClient.SongInfo> songs = musicClient.searchSong(keyword, 5);

        if (songs.isEmpty()) {
            invocationStore.add("music_search", "keyword=" + keyword, "未找到相关歌曲");
            return new MusicSearchResult(false, null, "未找到相关歌曲，请换个关键词试试");
        }

        // 优先选择可播放的歌曲
        NeteaseMusicClient.SongInfo bestSong = songs.stream()
                .filter(s -> s.playFlag() && s.visible() && !s.vipFlag())
                .findFirst()
                .orElse(songs.stream()
                        .filter(s -> s.visible())
                        .findFirst()
                        .orElse(songs.get(0)));

        // 获取播放链接
        NeteaseMusicClient.PlayUrlResult playUrl = musicClient.getPlayUrl(bestSong.id(), 320);

        String webUrl = "https://music.163.com/song?id=" + bestSong.id();

        SongDetail detail = new SongDetail(
                bestSong.id(),
                bestSong.name(),
                bestSong.getArtistString(),
                bestSong.albumName(),
                bestSong.getDurationString(),
                bestSong.coverImgUrl(),
                bestSong.playFlag(),
                bestSong.vipFlag(),
                playUrl.isSuccess() ? playUrl.url() : null,
                playUrl.isSuccess() ? null : playUrl.error(),
                webUrl
        );

        // 直接向用户发送音乐链接
        sendMusicToUser(bestSong, detail, webUrl);

        String message;
        if (playUrl.isSuccess()) {
            message = "找到歌曲并已发送: " + bestSong.name() + " - " + bestSong.getArtistString();
        } else {
            message = "找到歌曲，已发送网易云链接: " + bestSong.name() + " - " + bestSong.getArtistString();
        }

        invocationStore.add("music_search", "keyword=" + keyword, message);

        return new MusicSearchResult(true, detail, message);
    }

    /**
     * 向用户发送音乐链接
     */
    private void sendMusicToUser(NeteaseMusicClient.SongInfo song, SongDetail detail, String webUrl) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            log.warn("发送音乐链接失败: 无法获取用户ID");
            return;
        }

        try {
            StringBuilder msg = new StringBuilder();
            msg.append("🎵 ").append(song.name()).append("\n");
            msg.append("歌手: ").append(song.getArtistString()).append("\n");
            msg.append("专辑: ").append(song.albumName()).append("\n\n");

            // 优先发送可直接播放的链接
            if (detail.playUrl() != null) {
                msg.append("▶ 点击播放: ").append(detail.playUrl());
            } else {
                msg.append("🔗 网易云音乐: ").append(webUrl);
            }

            messageSender.sendText(userId, msg.toString());
            log.info("音乐链接已发送: userId={}, song={}", userId, song.name());
        } catch (Exception e) {
            log.error("发送音乐链接失败: userId={}, song={}", userId, song.name(), e);
        }
    }

    /**
     * 歌曲搜索结果
     */
    public record MusicSearchResult(boolean success, SongDetail song, String message) {}

    /**
     * 歌曲详情
     */
    public record SongDetail(
            String id,
            String name,
            String artist,
            String album,
            String duration,
            String coverImgUrl,
            boolean playable,
            boolean vipOnly,
            String playUrl,
            String playError,
            String webUrl
    ) {}
}
