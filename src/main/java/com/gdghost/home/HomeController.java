package com.gdghost.home;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;
import com.gdghost.notice.NoticeService;
import com.gdghost.question.QuestionService;
import com.gdghost.schedule.ScheduleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "홈")
@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    static final int RECENT_QUESTIONS = 3;
    static final int UPCOMING_SCHEDULES = 3;

    private final QuestionService questionService;
    private final NoticeService noticeService;
    private final ScheduleService scheduleService;

    public HomeController(QuestionService questionService, NoticeService noticeService,
            ScheduleService scheduleService) {
        this.questionService = questionService;
        this.noticeService = noticeService;
        this.scheduleService = scheduleService;
    }

    @Operation(summary = "홈", description = "비회원도 부른다. 비회원은 GuestHomeResponse(질문은 제목 · 작성자 · 답변 수까지). "
            + "고정 공지는 최신 5개(비회원은 id · 제목만), unconfirmedMustReadCount는 마감일이 없거나 오늘(한국 날짜) 이후인 "
            + "필독 가운데 내가 확인하지 않은 수. 최근 질문 3개. 다가오는 일정 3개는 마감이 가까운 순(지난 일정 제외). 공유(9단계)는 그 전까지 빈 배열.")
    @GetMapping
    public Object home(@AuthenticationPrincipal AuthMember me) {
        // TODO 인기 공유(9)
        List<?> recentQuestions = questionService.list(me, null, false, false, null, RECENT_QUESTIONS).items();
        List<?> upcomingSchedules = scheduleService.upcoming(me, UPCOMING_SCHEDULES);
        if (me == null) {
            return new GuestHomeResponse(noticeService.guestPinned(), recentQuestions, upcomingSchedules, List.of());
        }
        return new HomeResponse(noticeService.pinned(me.id()), noticeService.unconfirmedMustReadCount(me.id()),
                recentQuestions, upcomingSchedules, List.of());
    }

}
