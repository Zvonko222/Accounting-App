package com.example.accounting.data.sync.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 同步协议的传输对象（与 ASP.NET Core 服务端约定，字段全部显式命名）。
 *
 * 设计要点：change 里携带整行实体的 JSON（payload），服务端不理解业务字段、
 * 只做原样存储——服务端因此与表结构完全解耦（客户端加字段服务端不用改）。
 */
public final class SyncModels {

    private SyncModels() {
    }

    /** 一条待同步的行变更 */
    public static class Change {
        @SerializedName("table")
        public String table;

        @SerializedName("rowId")
        public String rowId;

        @SerializedName("updatedAt")
        public long updatedAt;

        @SerializedName("isDeleted")
        public boolean isDeleted;

        /** 整行实体（业务字段 + 同步列），服务端原样存储 */
        @SerializedName("payload")
        public Object payload;
    }

    /** 请求：上行一批变更 */
    public static class PushRequest {
        @SerializedName("deviceId")
        public String deviceId;

        @SerializedName("changes")
        public List<Change> changes;
    }

    /** 响应：服务器确认（serverTime 用于推进游标与回填 lastSyncedAt） */
    public static class PushResponse {
        @SerializedName("serverTime")
        public long serverTime;

        @SerializedName("accepted")
        public int accepted;
    }

    /** 请求参数：下行拉取（自上次游标起，含 5 分钟重叠窗） */
    public static class PullRequest {
        @SerializedName("deviceId")
        public String deviceId;

        @SerializedName("since")
        public long since;

        @SerializedName("limit")
        public int limit;
    }

    /** 响应：下行一批变更 */
    public static class PullResponse {
        @SerializedName("serverTime")
        public long serverTime;

        @SerializedName("changes")
        public List<Change> changes;
    }
}
