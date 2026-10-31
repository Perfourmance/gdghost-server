package com.gdghost.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.gdghost.common.error.ErrorLogRecorder;
import com.gdghost.member.Member;
import com.gdghost.member.MemberProfileReader;
import com.gdghost.member.MemberRepository;
import com.gdghost.question.Question;
import com.gdghost.question.QuestionRepository;

/**
 * 벡터를 계산해 저장한다. 트랜잭션 없이 돈다 — 외부 호출 동안 DB 커넥션을 잡지 않고, 실패 기록은 남아야 한다.
 * 실패해도 던지지 않는다. 비어 있는 벡터는 재시도 배치가 채운다.
 */
@Service
public class EmbeddingService {

    public enum Outcome {
        SAVED, SKIPPED, BAD_INPUT, FAILED
    }

    private final EmbeddingClient client;
    private final TextBuilder textBuilder;
    private final EmbeddingStore store;
    private final AiLogWriter aiLog;
    private final QuestionRepository questions;
    private final MemberRepository members;
    private final MemberProfileReader profileReader;
    private final ErrorLogRecorder errorLog;

    public EmbeddingService(EmbeddingClient client, TextBuilder textBuilder, EmbeddingStore store, AiLogWriter aiLog,
            QuestionRepository questions, MemberRepository members, MemberProfileReader profileReader,
            ErrorLogRecorder errorLog) {
        this.client = client;
        this.textBuilder = textBuilder;
        this.store = store;
        this.aiLog = aiLog;
        this.questions = questions;
        this.members = members;
        this.profileReader = profileReader;
        this.errorLog = errorLog;
    }

    public boolean enabled() {
        return client.enabled();
    }

    public String modelId() {
        return client.modelId();
    }

    public Outcome embedQuestion(long questionId) {
        if (!client.enabled() || store.hasQuestion(questionId, client.modelId())) {
            return Outcome.SKIPPED;
        }
        Optional<String> text = questionText(questionId);
        if (text.isEmpty()) {
            return Outcome.SKIPPED;
        }
        Result result = call(AiLogWriter.Kind.EMBED_QUESTION, questionId, text.get());
        if (result.vector() == null) {
            return result.outcome();
        }
        // 호출 중에 글이 바뀌었으면 옛 글의 벡터를 넣지 않는다. 수정 이벤트 또는 배치가 채운다
        if (!text.equals(questionText(questionId))) {
            return Outcome.SKIPPED;
        }
        store.saveQuestion(questionId, result.vector(), client.modelId());
        return Outcome.SAVED;
    }

    /** 조립한 글의 해시 · 모델이 그대로면 부르지 않는다 */
    public Outcome embedMember(long memberId) {
        if (!client.enabled()) {
            return Outcome.SKIPPED;
        }
        Member member = members.findById(memberId).filter(Member::isActive).orElse(null);
        if (member == null) {
            return Outcome.SKIPPED;
        }
        MemberProfileReader.Tags tags = profileReader.tags(memberId);
        String text = textBuilder.memberText(new TextBuilder.MemberSource(tags.interests(), tags.experiences(),
                member.getStatusNote(), member.getBio()));
        if (text.isBlank()) {
            return Outcome.SKIPPED;
        }
        String hash = sha256(text);
        EmbeddingStore.MemberRow current = new EmbeddingStore.MemberRow(client.modelId(), hash);
        if (store.member(memberId).filter(current::equals).isPresent()) {
            return Outcome.SKIPPED;
        }
        Result result = call(AiLogWriter.Kind.EMBED_MEMBER, memberId, text);
        if (result.vector() == null) {
            return result.outcome();
        }
        store.saveMember(memberId, result.vector(), client.modelId(), hash);
        return Outcome.SAVED;
    }

    /** 선배 찾기. 저장하지 않고, 실패하면 비어 있어 규칙 점수로 대체한다. 호출한 부원 id만 남기고 글은 남기지 않는다 */
    public Optional<float[]> embedForSearch(String text, long memberId) {
        if (!client.enabled()) {
            return Optional.empty();
        }
        long startedAt = System.nanoTime();
        try {
            float[] vector = client.embed(List.of(text)).getFirst();
            aiLog.record(AiLogWriter.Kind.FIND_SENIOR, memberId, client.modelId(), latency(startedAt),
                    AiLogWriter.State.SUCCESS);
            return Optional.of(vector);
        } catch (EmbeddingException e) {
            aiLog.record(AiLogWriter.Kind.FIND_SENIOR, memberId, client.modelId(), latency(startedAt),
                    AiLogWriter.State.FALLBACK);
            errorLog.record(e, "embedding " + AiLogWriter.Kind.FIND_SENIOR);
            return Optional.empty();
        }
    }

    private Optional<String> questionText(long questionId) {
        return questions.findById(questionId)
                .filter(question -> !question.isDeleted())
                .map(this::text);
    }

    private String text(Question question) {
        return textBuilder.questionText(new TextBuilder.QuestionSource(question.getCategory(), question.getTitle(),
                question.getBody()));
    }

    private record Result(float[] vector, Outcome outcome) {
    }

    private Result call(AiLogWriter.Kind kind, long targetId, String text) {
        long startedAt = System.nanoTime();
        try {
            List<float[]> vectors = client.embed(List.of(text));
            aiLog.record(kind, targetId, client.modelId(), latency(startedAt), AiLogWriter.State.SUCCESS);
            return new Result(vectors.getFirst(), Outcome.SAVED);
        } catch (EmbeddingException e) {
            aiLog.record(kind, targetId, client.modelId(), latency(startedAt), AiLogWriter.State.FAILED);
            errorLog.record(e, "embedding " + kind);
            return new Result(null, e.isBadInput() ? Outcome.BAD_INPUT : Outcome.FAILED);
        }
    }

    private static int latency(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
