package com.gdghost.embedding;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Workers AI bge-m3. 시간 초과 3초 · 1회 재시도 */
public class WorkersAiEmbeddingClient implements EmbeddingClient {

    public static final String BASE_URL = "https://api.cloudflare.com/client/v4";
    static final String MODEL = "@cf/baai/bge-m3";

    private static final Logger log = LoggerFactory.getLogger(WorkersAiEmbeddingClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final int DIMENSION = 1024;

    private final String baseUrl;
    private final String accountId;
    private final String apiToken;
    private final Duration retryDelay;
    private final RestClient client;

    public WorkersAiEmbeddingClient(String baseUrl, String accountId, String apiToken, Duration retryDelay) {
        this.baseUrl = baseUrl;
        this.accountId = accountId;
        this.apiToken = apiToken;
        this.retryDelay = retryDelay;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean enabled() {
        return !accountId.isBlank() && !apiToken.isBlank();
    }

    @Override
    public String modelId() {
        return MODEL;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        // 아주 긴 글 하나 때문에 영영 실패하지 않게 모델 한도에서 자른다
        Map<String, Object> body = Map.of("text", texts, "truncate_inputs", true);
        Response response;
        try {
            response = post(body);
        } catch (RestClientException first) {
            if (isBadInput(first)) {
                throw failure(first);
            }
            log.warn("임베딩 실패, 한 번 더 시도: {}", reason(first));
            pause();
            try {
                response = post(body);
            } catch (RestClientException second) {
                // 계정 ID가 든 주소가 있는 원래 예외는 넘기지 않는다
                throw failure(second);
            }
        }
        return vectors(response, texts.size());
    }

    private Response post(Map<String, Object> body) {
        return client.post()
                // 모델 이름의 /는 경로 구분자 — 변수로 넘기면 %2F로 인코딩돼 400이 난다
                .uri(baseUrl + "/accounts/{account}/ai/run/" + MODEL, accountId)
                .header("Authorization", "Bearer " + apiToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Response.class);
    }

    private static List<float[]> vectors(Response response, int expected) {
        if (response == null || !response.success() || response.result() == null
                || response.result().data() == null) {
            throw new EmbeddingException("임베딩 응답에 벡터가 없음", false);
        }
        List<float[]> vectors = List.of(response.result().data());
        if (vectors.size() != expected || vectors.stream().anyMatch(vector -> vector.length != DIMENSION)) {
            throw new EmbeddingException("임베딩 응답의 개수 또는 차원이 다름", false);
        }
        return vectors;
    }

    private static EmbeddingException failure(RestClientException e) {
        return new EmbeddingException("임베딩 실패: " + reason(e), isBadInput(e));
    }

    private static boolean isBadInput(RestClientException e) {
        return e instanceof RestClientResponseException response
                && (response.getStatusCode().value() == 400 || response.getStatusCode().value() == 413);
    }

    private static String reason(RestClientException e) {
        return e instanceof RestClientResponseException response
                ? "HTTP " + response.getStatusCode().value()
                : e.getClass().getSimpleName();
    }

    private void pause() {
        try {
            Thread.sleep(retryDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(boolean success, Result result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(float[][] data) {
    }

}
