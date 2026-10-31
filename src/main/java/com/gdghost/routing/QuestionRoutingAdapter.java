package com.gdghost.routing;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.gdghost.common.time.QuietHours;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;
import com.gdghost.question.DirectDelivery;
import com.gdghost.question.Question;
import com.gdghost.question.QuestionRouting;
import com.gdghost.question.RoutingPort;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class QuestionRoutingAdapter implements RoutingPort {

    private static final String WITHDRAWN_NICKNAME = "탈퇴한 부원";

    private final MemberRepository members;
    private final RoutingReader reader;
    private final RoutingWriter writer;
    private final NotificationWriter notifications;
    private final ObjectMapper json;
    private final Clock clock;

    public QuestionRoutingAdapter(MemberRepository members, RoutingReader reader, RoutingWriter writer,
            NotificationWriter notifications, ObjectMapper json, Clock clock) {
        this.members = members;
        this.reader = reader;
        this.writer = writer;
        this.notifications = notifications;
        this.json = json;
        this.clock = clock;
    }

    /** 부원 행을 잠가 하루 3건 확인과 발송을 한 줄로 세운다 */
    @Override
    public DirectDelivery direct(Question question, long targetMemberId) {
        Member target = members.findByIdForUpdate(targetMemberId).orElse(null);
        Instant now = clock.instant();
        if (target == null || !target.isActive() || !target.isAcceptQuestion() || target.isHiddenFromList()
                || reader.receivedToday(List.of(targetMemberId), now).getOrDefault(targetMemberId, 0) >= RoutingReader.DAILY_LIMIT) {
            return DirectDelivery.FALLBACK;
        }
        if (QuietHours.contains(now)) {
            writer.insertDirect(question.getId(), targetMemberId, null);
            return DirectDelivery.SCHEDULED;
        }
        writer.insertDirect(question.getId(), targetMemberId, now);
        notifications.toMember(targetMemberId, NotificationType.ROUTED, "나에게 온 질문이 있어요", question.getTitle(),
                NotificationTarget.question(question.getId()));
        return DirectDelivery.SENT;
    }

    @Override
    public QuestionRouting view(long questionId, boolean anonymous, long viewerId) {
        List<RoutingReader.Delivery> rows = reader.deliveries(questionId);
        if (rows.isEmpty()) {
            return null;
        }
        List<QuestionRouting.Delivery> deliveries = rows.stream()
                .map(row -> new QuestionRouting.Delivery(row.stage(), row.state(),
                        new QuestionRouting.To(row.nickname() != null ? row.nickname() : WITHDRAWN_NICKNAME),
                        anonymous ? List.of() : reasons(row), row.sentAt()))
                .toList();
        QuestionRouting.Mine mine = rows.stream()
                .filter(row -> row.memberId() == viewerId && !"STAFF".equals(row.stage()))
                .findFirst()
                .map(row -> new QuestionRouting.Mine(row.state(), "SENT".equals(row.state())))
                .orElse(null);
        return new QuestionRouting(deliveries, anonymous, mine);
    }

    private List<String> reasons(RoutingReader.Delivery row) {
        if (row.scoreDetail() == null) {
            return List.of();
        }
        JsonNode reasons = json.readTree(row.scoreDetail()).path("reasons");
        return reasons.valueStream().map(JsonNode::asString).toList();
    }

}
