package com.example.accounting.data.sync;

import com.example.accounting.data.sync.dto.SyncModels;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 同步 HTTP 客户端（OkHttp + Gson，Phase 9 起启用）。
 *
 * 只封装两个调用的"通信"部分：URL 拼接、超时、密钥头、JSON 编解码。
 * 不做任何业务判断——成功/失败原样上抛，由 SyncEngine 决定状态流转。
 */
public class SyncApi {

    /** 与服务端约定的访问密钥头（局域网部署的最低限度的防滥用） */
    public static final String KEY_HEADER = "X-Sync-Key";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new GsonBuilder().create();

    /** 上行推送。返回服务器确认；HTTP 非 2xx 或响应无法解析时抛 IOException */
    public SyncModels.PushResponse push(String baseUrl, String key,
                                        SyncModels.PushRequest request) throws IOException {
        String body = gson.toJson(request);
        Request httpRequest = new Request.Builder()
                .url(joinUrl(baseUrl, "/api/sync/push"))
                .header(KEY_HEADER, key)
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = client.newCall(httpRequest).execute()) {
            String text = readBody(response);
            return gson.fromJson(text, SyncModels.PushResponse.class);
        }
    }

    /** 下行拉取。返回服务器变更列表 */
    public SyncModels.PullResponse pull(String baseUrl, String key,
                                        SyncModels.PullRequest request) throws IOException {
        String url = joinUrl(baseUrl, "/api/sync/pull")
                + "?deviceId=" + java.net.URLEncoder.encode(request.deviceId, "UTF-8")
                + "&since=" + request.since
                + "&limit=" + request.limit;
        Request httpRequest = new Request.Builder()
                .url(url)
                .header(KEY_HEADER, key)
                .get()
                .build();

        try (Response response = client.newCall(httpRequest).execute()) {
            String text = readBody(response);
            return gson.fromJson(text, SyncModels.PullResponse.class);
        }
    }

    private String readBody(Response response) throws IOException {
        try (ResponseBody body = response.body()) {
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + "：" + text);
            }
            return text;
        }
    }

    private static String joinUrl(String baseUrl, String path) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + path;
    }
}
