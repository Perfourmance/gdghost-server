package com.gdghost.event;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.security.AuthMember;
import com.gdghost.notice.NoticeCreateRequest;
import com.gdghost.notice.NoticeDetail;
import com.gdghost.notice.OpsNoticeService;

import tools.jackson.databind.ObjectMapper;

/** 운영진의 이벤트 생성 · 수정 · 공지 발행. 삭제는 없다 — 인증 기록이 남아야 하므로 기간을 끝내 종료한다 */
@Service
public class OpsEventService {

    private final EventReader reader;
    private final EventWriter writer;
    private final EventService eventService;
    private final OpsNoticeService notices;
    private final ObjectMapper json;
    private final String webUrl;

    public OpsEventService(EventReader reader, EventWriter writer, EventService eventService,
            OpsNoticeService notices, ObjectMapper json, @Value("${web.url}") String webUrl) {
        this.reader = reader;
        this.writer = writer;
        this.eventService = eventService;
        this.notices = notices;
        this.json = json;
        this.webUrl = webUrl;
    }

    @Transactional
    public EventDetail create(AuthMember me, EventCreateRequest request) {
        long id = writer.insert(me.id(), request.name(), request.startDate(), request.endDate(),
                request.description(), request.requirement(), json.writeValueAsString(request.guideSteps()));
        return eventService.detail(me, id);
    }

    /** 실제로 바뀐 게 있을 때만 수정 시각을 찍는다 */
    @Transactional
    public EventDetail update(AuthMember me, long eventId, EventUpdateRequest request) {
        EventReader.DetailRow event = find(eventId);
        String name = Objects.requireNonNullElse(request.name(), event.name());
        LocalDate startDate = Objects.requireNonNullElse(request.startDate(), event.startDate());
        LocalDate endDate = Objects.requireNonNullElse(request.endDate(), event.endDate());
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("endDate", "종료일은 시작일 이후여야 합니다.")));
        }
        String description = request.description() != null ? request.description() : event.description();
        String requirement = request.requirement() != null ? request.requirement() : event.requirement();
        List<String> steps = request.guideSteps() != null ? request.guideSteps()
                : eventService.guideSteps(event.guideSteps());
        boolean changed = !name.equals(event.name()) || !startDate.equals(event.startDate())
                || !endDate.equals(event.endDate()) || !Objects.equals(description, event.description())
                || !Objects.equals(requirement, event.requirement())
                || !steps.equals(eventService.guideSteps(event.guideSteps()));
        if (changed) {
            writer.update(eventId, name, startDate, endDate, description, requirement,
                    json.writeValueAsString(steps));
        }
        return eventService.detail(me, eventId);
    }

    /** 공지 작성과 같은 길을 탄다(알림 · 디스코드 포함). 마감일은 이벤트 종료일이라 끝난 이벤트는 발행할 수 없다 */
    @Transactional
    public NoticeDetail publishNotice(AuthMember me, long eventId, EventNoticeRequest request) {
        EventReader.DetailRow event = find(eventId);
        return notices.create(me, new NoticeCreateRequest("[이벤트] " + event.name(), body(event),
                request == null ? null : request.isPinned(), request == null ? null : request.isMustRead(),
                event.endDate()));
    }

    private String body(EventReader.DetailRow event) {
        List<String> steps = eventService.guideSteps(event.guideSteps());
        String guide = IntStream.range(0, steps.size())
                .mapToObj(i -> (i + 1) + ". " + steps.get(i))
                .collect(Collectors.joining("\n"));
        return """
                %s

                인증에 필요한 것: %s

                %s

                %s/events/%d""".formatted(event.description(), event.requirement(), guide, webUrl, event.id());
    }

    private EventReader.DetailRow find(long eventId) {
        return reader.find(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "이벤트를 찾을 수 없습니다."));
    }

}
