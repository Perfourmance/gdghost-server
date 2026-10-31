package com.gdghost.routing;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.embedding.AiLogWriter;
import com.gdghost.embedding.EmbeddingService;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;
import com.gdghost.question.Question;
import com.gdghost.question.QuestionRepository;
import com.gdghost.question.QuestionState;

/**
 * 한 질문을 보낸다. 질문 행을 잠가 답변 · 삭제와 엇갈리지 않게 하고, 받는 사람 행을 잠가 지목과 하루 3건이 어긋나지 않게 한다.
 * 잠금 순서는 질문 → 부원(id 순).
 */
@Service
public class RoutingDelivery {

    static final String ROUTED_TITLE = "나에게 온 질문이 있어요";

    private final RoutingCandidateService candidates;
    private final MatchingConfig config;
    private final RoutingReader reader;
    private final RoutingWriter writer;
    private final QuestionRepository questions;
    private final MemberRepository members;
    private final NotificationWriter notifications;
    private final DiscordNotifier discord;
    private final AiLogWriter aiLog;
    private final EmbeddingService embeddings;
    private final TransactionTemplate transaction;
    private final String webUrl;
    private final Clock clock;

    public RoutingDelivery(RoutingCandidateService candidates, MatchingConfig config, RoutingReader reader,
            RoutingWriter writer, QuestionRepository questions, MemberRepository members,
            NotificationWriter notifications, DiscordNotifier discord, AiLogWriter aiLog,
            EmbeddingService embeddings, TransactionTemplate transaction, @Value("${web.url}") String webUrl,
            Clock clock) {
        this.candidates = candidates;
        this.config = config;
        this.reader = reader;
        this.writer = writer;
        this.questions = questions;
        this.members = members;
        this.notifications = notifications;
        this.discord = discord;
        this.aiLog = aiLog;
        this.embeddings = embeddings;
        this.transaction = transaction;
        this.webUrl = webUrl;
        this.clock = clock;
    }

    /** @return 알림을 보낸 부원 수. 후보가 없으면 0이고 다음 배치에서 다시 계산한다 */
    int sendToMembers(long questionId) {
        Question question = questions.findById(questionId).orElse(null);
        if (question == null) {
            return 0;
        }
        RoutingCandidateService.Ranked ranked = candidates.rank(question);
        if (ranked.candidates().isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        List<CandidateScorer.Scored> sent = transaction.execute(status -> {
            Question locked = open(questionId).orElse(null);
            if (locked == null) {
                return List.<CandidateScorer.Scored>of();
            }
            List<CandidateScorer.Scored> receivers = lockReceivers(ranked.candidates(), now);
            writer.insertMembers(questionId, receivers, config.version(), now);
            notifications.toMembers(receivers.stream().map(CandidateScorer.Scored::memberId).toList(),
                    NotificationType.ROUTED, ROUTED_TITLE, locked.getTitle(), NotificationTarget.question(questionId),
                    true);
            return receivers;
        });
        // 후보가 없는 질문이 10분마다 쌓이지 않게, 규칙 점수로 실제 보냈을 때만 남긴다
        if (ranked.ruleOnly() && !sent.isEmpty()) {
            aiLog.record(AiLogWriter.Kind.EMBED_QUESTION, questionId, embeddings.modelId(), null,
                    AiLogWriter.State.FALLBACK);
        }
        return sent.size();
    }

    /** 계산 뒤에 받을 수 없게 된 사람은 뺀다 */
    private List<CandidateScorer.Scored> lockReceivers(List<CandidateScorer.Scored> ranked, Instant now) {
        List<Long> ids = new ArrayList<>(ranked.stream().map(CandidateScorer.Scored::memberId).toList());
        ids.sort(Comparator.naturalOrder());
        List<Long> receivable = ids.stream()
                .filter(id -> members.findByIdForUpdate(id).filter(RoutingDelivery::receivable).isPresent())
                .toList();
        Map<Long, Integer> today = reader.receivedToday(receivable, now);
        return ranked.stream()
                .filter(scored -> receivable.contains(scored.memberId())
                        && today.getOrDefault(scored.memberId(), 0) < RoutingReader.DAILY_LIMIT)
                .toList();
    }

    /** 운영진 채널 알림. 활동 중인 운영진이 없으면 보내지 않고 다음 배치에서 다시 본다 */
    boolean sendToStaff(long questionId) {
        Instant now = clock.instant();
        Question question = transaction.execute(status -> {
            Question locked = open(questionId).orElse(null);
            Optional<Long> staff = reader.anyActiveStaff();
            if (locked == null || staff.isEmpty()) {
                return null;
            }
            writer.insertStaff(questionId, staff.get(), now);
            return locked;
        });
        if (question == null) {
            return false;
        }
        discord.send(DiscordChannel.STAFF, new DiscordMessage("1시간 미답변 · " + question.getCategory().label(),
                webUrl + "/questions/" + questionId, DiscordMessage.escape(question.getTitle())));
        return true;
    }

    /** 밤에 지목된 것. 그사이 답이 달렸거나 받을 수 없게 됐으면 취소하고, 답이 없으면 일반 라우팅으로 돌아간다 */
    boolean sendScheduled(RoutingReader.Scheduled direct) {
        Instant now = clock.instant();
        return Boolean.TRUE.equals(transaction.execute(status -> {
            Question question = open(direct.questionId()).orElse(null);
            boolean receivable = question != null
                    && members.findByIdForUpdate(direct.memberId()).filter(RoutingDelivery::receivable).isPresent()
                    && reader.receivedToday(List.of(direct.memberId()), now)
                            .getOrDefault(direct.memberId(), 0) < RoutingReader.DAILY_LIMIT;
            if (!receivable) {
                writer.cancel(direct.id(), now);
                return false;
            }
            writer.markSent(direct.id(), now);
            notifications.toMember(direct.memberId(), NotificationType.ROUTED, ROUTED_TITLE, question.getTitle(),
                    NotificationTarget.question(direct.questionId()));
            return true;
        }));
    }

    private Optional<Question> open(long questionId) {
        return questions.findForUpdate(questionId)
                .filter(question -> !question.isDeleted() && question.getState() == QuestionState.WAITING);
    }

    static boolean receivable(Member member) {
        return member.isActive() && member.isAcceptQuestion() && !member.isHiddenFromList();
    }

}
