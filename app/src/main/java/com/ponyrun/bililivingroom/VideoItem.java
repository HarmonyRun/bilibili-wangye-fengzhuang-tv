package com.ponyrun.bililivingroom;

final class VideoItem {
    final String title;
    final String owner;
    final String stats;
    final String duration;
    final String coverUrl;
    final String targetUrl;
    final String avatarUrl;
    final String published;
    /** Optional UI/content kind, mainly used by heterogeneous search results. */
    final String kind;

    VideoItem(String title, String owner, String stats, String duration, String coverUrl, String targetUrl) {
        this(title, owner, stats, duration, coverUrl, targetUrl, "", "", "");
    }

    VideoItem(String title, String owner, String stats, String duration, String coverUrl, String targetUrl,
              String avatarUrl, String published) {
        this(title, owner, stats, duration, coverUrl, targetUrl, avatarUrl, published, "");
    }

    VideoItem(String title, String owner, String stats, String duration, String coverUrl, String targetUrl,
              String avatarUrl, String published, String kind) {
        this.title = title;
        this.owner = owner;
        this.stats = stats;
        this.duration = duration;
        this.coverUrl = coverUrl;
        this.targetUrl = targetUrl;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        this.published = published == null ? "" : published;
        this.kind = kind == null ? "" : kind;
    }
}
