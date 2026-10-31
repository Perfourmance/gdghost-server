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
    private static final String STAFF_LABEL = "운영진";
    private static final String STAFF_REASON = "1시간 미답변 · 운영진 우선";

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
        if (target == null || !RoutingDelivery.receivable(target)
                || reader.receivedToday(List.of(targetMemberId), now).getOrDefault(targetMemberId, 0)
                        >= RoutingReader.DAILY_LIMIT) {
            return DirectDelivery.FALLBACK;
        }
        if (QuietHours.contains(now)) {
            writer.insertDirect(question.getId(), targetMemberId, null, now);
            return DirectDelivery.SCHEDULED;
        }
        writer.insertDirect(question.getId(), targetMemberId, now, now);
        notifications.toMember(targetMemberId, NotificationType.ROUTED, RoutingDelivery.ROUTED_TITLE,
                question.getTitle(), NotificationTarget.question(question.getId()));
        return DirectDelivery.SENT;
    }

    @Override
    public void answered(long questionId, long memberId) {
        writer.markAnswered(questionId, memberId, clock.instant());
    }

    @Override
    public QuestionRouting view(long questionId, boolean anonymous, long viewerId) {
        List<RoutingReader.Delivery> rows = reader.deliveries(questionId);
        if (rows.isEmpty()) {
            return null;
        }
        List<QuestionRouting.Delivery> deliveries = rows.stream()
                .map(row -> new QuestionRouting.Delivery(row.stage(), state(row, anonymous, viewerId),
                        new QuestionRouting.To(nickname(row)), anonymous ? List.of() : reasons(row), row.sentAt()))
                .toList();
        QuestionRouting.Mine mine = rows.stream()
                .filter(row -> row.memberId() == viewerId && !"STAFF".equals(row.stage()))
                .findFirst()
                .map(row -> new QuestionRouting.Mine(row.state(), "SENT".equals(row.state())))
                .orElse(null);
        return new QuestionRouting(deliveries, anonymous, mine);
    }

    /** 익명 질문에서 '답변함'을 보이면 익명 답변을 쓴 사람이 드러난다. 본인에게만 그대로 보여 준다 */
    private static String state(RoutingReader.Delivery row, boolean anonymous, long viewerId) {
        boolean hide = anonymous && "ANSWERED".equals(row.state()) && row.memberId() != viewerId;
        return hide ? "SENT" : row.state();
    }

    /** 운영진 단계는 채널 알림이라 대표 한 명의 이름을 보이지 않는다 */
    private static String nickname(RoutingReader.Delivery row) {
        if ("STAFF".equals(row.stage())) {
            return STAFF_LABEL;
        }
        return row.nickname() != null ? row.nickname() : WITHDRAWN_NICKNAME;
    }

    private List<String> reasons(RoutingReader.Delivery row) {
        if ("STAFF".equals(row.stage())) {
            return List.of(STAFF_REASON);
        }
        if (row.scoreDetail() == null) {
            return List.of();
        }
        return json.readTree(row.scoreDetail()).path("reasons").valueStream().map(JsonNode::asString).toList();
    }

}
