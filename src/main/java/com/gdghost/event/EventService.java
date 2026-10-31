package com.gdghost.event;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.Role;
import com.gdghost.question.AuthorRef;
import com.gdghost.upload.Attachment;
import com.gdghost.upload.AttachmentService;
import com.gdghost.upload.UploadPurpose;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class EventService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int RANKING_SIZE = 10;
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final EventReader reader;
    private final AttachmentService attachments;
    private final ObjectMapper json;
    private final Clock clock;

    public EventService(EventReader reader, AttachmentService attachments, ObjectMapper json, Clock clock) {
        this.reader = reader;
        this.attachments = attachments;
        this.json = json;
        this.clock = clock;
    }

    /** 진행 중(마감 가까운 순) → 예정(시작 가까운 순) → 지난 이벤트(최근 순) */
    @Transactional(readOnly = true)
    public EventListResponse list(long viewerId, EventState state) {
        LocalDate today = today();
        List<EventSummary> items = reader.list(viewerId).stream()
                .map(row -> new EventSummary(row.id(), row.name(), row.startDate(), row.endDate(),
                        EventState.of(row.startDate(), row.endDate(), today), row.participantCount(),
                        row.approvedCount(), new EventSummary.My(row.myApprovedCount(), row.myPendingCount())))
                .filter(event -> state == null || event.state() == state)
                .sorted(ORDER)
                .toList();
        return new EventListResponse(items);
    }

    private static final Comparator<EventSummary> ORDER = Comparator
            .comparingInt((EventSummary event) -> switch (event.state()) {
                case ONGOING -> 0;
                case UPCOMING -> 1;
                case ENDED -> 2;
            })
            .thenComparingLong(event -> switch (event.state()) {
                case ONGOING -> event.endDate().toEpochDay();
                case UPCOMING -> event.startDate().toEpochDay();
                case ENDED -> -event.endDate().toEpochDay();
            })
            .thenComparingLong(EventSummary::id);

    @Transactional(readOnly = true)
    public EventDetail detail(AuthMember viewer, long eventId) {
        EventReader.DetailRow event = reader.find(eventId).orElseThrow(EventService::notFound);
        EventDetail.My my = new EventDetail.My(
                reader.count(eventId, viewer.id(), CertificationState.APPROVED),
                reader.count(eventId, viewer.id(), CertificationState.PENDING),
                reader.rejected(eventId, viewer.id()));
        Long pending = viewer.role() == Role.STAFF ? reader.count(eventId, null, CertificationState.PENDING) : null;
        return new EventDetail(event.id(), event.name(), event.description(), event.requirement(),
                guideSteps(event.guideSteps()), event.startDate(), event.endDate(),
                EventState.of(event.startDate(), event.endDate(), today()), my, ranking(eventId), pending);
    }

    /** 사진 주소는 여기서 고른 글에만 발급한다 — 남의 대기 · 반려 인증 사진이 부원에게 나가지 않게 */
    @Transactional(readOnly = true)
    public CursorPage<CertificationItem> certifications(AuthMember viewer, long eventId, String cursor, int size) {
        if (!reader.exists(eventId)) {
            throw notFound();
        }
        boolean staff = viewer.role() == Role.STAFF;
        List<EventReader.CertificationRow> rows = reader.certifications(eventId, viewer.id(), staff, before(cursor),
                size + 1);
        List<EventReader.CertificationRow> page = rows.subList(0, Math.min(size, rows.size()));
        Map<Long, List<Attachment>> photos = attachments.photos(UploadPurpose.CERT,
                page.stream().map(EventReader.CertificationRow::id).toList());
        List<CertificationItem> items = new ArrayList<>(rows.size());
        for (EventReader.CertificationRow row : rows) {
            boolean mine = row.author().id() == viewer.id();
            items.add(new CertificationItem(row.id(), AuthorRef.of(row.author(), false, null, viewer.id()), row.body(),
                    photos.getOrDefault(row.id(), List.of()), row.state(),
                    mine || staff ? row.rejectReason() : null, mine, row.createdAt()));
        }
        return CursorPage.of(items, size, item -> Long.toString(item.id()));
    }

    private List<EventDetail.Rank> ranking(long eventId) {
        List<EventReader.RankRow> rows = reader.ranking(eventId, RANKING_SIZE);
        List<EventDetail.Rank> ranks = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            EventReader.RankRow row = rows.get(i);
            boolean tied = i > 0 && rows.get(i - 1).approved() == row.approved();
            ranks.add(new EventDetail.Rank(tied ? ranks.get(i - 1).rank() : i + 1, row.member(), row.approved()));
        }
        return ranks;
    }

    List<String> guideSteps(String raw) {
        return raw == null ? List.of() : json.readValue(raw, STRINGS);
    }

    private static Long before(String cursor) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw CursorPage.invalidCursor();
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "이벤트를 찾을 수 없습니다.");
    }

}
