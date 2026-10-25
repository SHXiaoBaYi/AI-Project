package com.base.admin.service;

import com.base.admin.config.AiProperties;
import com.base.admin.domain.entity.SysAiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 题干向量。优先走已配置的通义兼容接口 text-embedding-v3。
 */
@Slf4j
@Service
public class TrainQaEmbeddingClient {

    private final AiProperties aiProperties;
    private final SysAiProviderService aiProviderService;
    private final ObjectMapper objectMapper;

    public TrainQaEmbeddingClient(AiProperties aiProperties,
                                  SysAiProviderService aiProviderService,
                                  ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.aiProviderService = aiProviderService;
        this.objectMapper = objectMapper;
    }

    public boolean available() {
        return resolve() != null;
    }

    /** 与输入顺序一致。失败返回 null，调用方保留题目、提问时回退旧检索。 */
    public List<float[]> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        Endpoint ep = resolve();
        if (ep == null) {
            return null;
        }
        List<float[]> out = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i += 6) {
            List<String> batch = inputs.subList(i, Math.min(inputs.size(), i + 6));
            List<float[]> part = embedBatch(ep, batch);
            if (part == null || part.size() != batch.size()) {
                return null;
            }
            out.addAll(part);
        }
        return out;
    }

    private List<float[]> embedBatch(Endpoint ep, List<String> batch) {
        List<float[]> withDim = post(ep, batch, true);
        if (withDim != null) {
            return withDim;
        }
        return post(ep, batch, false);
    }

    private List<float[]> post(Endpoint ep, List<String> batch, boolean withDimensions) {
        try {
            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(60));
            RestClient client = RestClient.builder()
                    .baseUrl(ep.baseUrl())
                    .requestFactory(factory)
                    .defaultHeader("Authorization", "Bearer " + ep.apiKey())
                    .build();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", TrainQaVectors.EMBED_MODEL);
            body.put("input", batch);
            body.put("encoding_format", "float");
            if (withDimensions) {
                body.put("dimensions", TrainQaVectors.EMBED_DIM);
            }
            String raw = client.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(raw, batch.size());
        } catch (RestClientResponseException e) {
            log.warn("题干向量接口失败 status={} dim={} body={}",
                    e.getStatusCode().value(), withDimensions, abbreviate(e.getResponseBodyAsString()));
            return null;
        } catch (Exception e) {
            log.warn("题干向量接口失败: {}", e.getMessage());
            return null;
        }
    }

    private List<float[]> parse(String raw, int expect) throws Exception {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        JsonNode root = objectMapper.readTree(raw);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray() || data.size() != expect) {
            log.warn("题干向量返回条数不符 expect={} body={}", expect, abbreviate(raw));
            return null;
        }
        float[][] slots = new float[expect][];
        for (JsonNode row : data) {
            int index = row.path("index").asInt(0);
            JsonNode emb = row.get("embedding");
            if (index < 0 || index >= expect || emb == null || !emb.isArray() || emb.isEmpty()) {
                return null;
            }
            float[] vec = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                vec[i] = (float) emb.get(i).asDouble();
            }
            TrainQaVectors.normalize(vec);
            slots[index] = vec;
        }
        List<float[]> list = new ArrayList<>(expect);
        for (float[] vec : slots) {
            if (vec == null) {
                return null;
            }
            list.add(vec);
        }
        return list;
    }

    private Endpoint resolve() {
        Endpoint tongyi = endpoint(AiProperties.PROVIDER_TONGYI);
        if (tongyi != null) {
            return tongyi;
        }
        String preferred = aiProperties.resolveDefaultProvider();
        if (!AiProperties.PROVIDER_LOCAL.equals(preferred)) {
            return endpoint(preferred);
        }
        return null;
    }

    private Endpoint endpoint(String provider) {
        String p = aiProperties.normalizeProvider(provider);
        if (AiProperties.PROVIDER_LOCAL.equals(p)) {
            return null;
        }
        Optional<SysAiProvider> db = aiProviderService.findReady(p);
        if (db.isPresent()) {
            SysAiProvider row = db.get();
            if (!StringUtils.hasText(row.getApiKey())) {
                return null;
            }
            String base = StringUtils.hasText(row.getBaseUrl())
                    ? trimSlash(row.getBaseUrl())
                    : aiProperties.resolveBaseUrl(p);
            return new Endpoint(base, row.getApiKey().trim());
        }
        if (!aiProperties.isProviderConfigured(p)) {
            return null;
        }
        return new Endpoint(aiProperties.resolveBaseUrl(p), aiProperties.resolveApiKey(p));
    }

    private static String trimSlash(String url) {
        String s = url == null ? "" : url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ');
        return t.length() <= 240 ? t : t.substring(0, 240);
    }

    private record Endpoint(String baseUrl, String apiKey) {}
}
