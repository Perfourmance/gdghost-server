package com.gdghost.search;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.search.FullText;
import com.gdghost.common.security.AuthMember;
import com.gdghost.member.MemberService;
import com.gdghost.question.QuestionService;
import com.gdghost.schedule.ScheduleService;
import com.gdghost.share.ShareService;

@Service
public class SearchService {

    private static final int QUERY_MIN = 2;
    private static final int QUERY_MAX = 30;
    private static final int TOP = 5;

    private final MemberService members;
    private final QuestionService questions;
    private final ShareService shares;
    private final ScheduleService schedules;

    public SearchService(MemberService members, QuestionService questions, ShareService shares,
            ScheduleService schedules) {
        this.members = members;
        this.questions = questions;
        this.shares = shares;
        this.schedules = schedules;
    }

    /** ALL이면 종류별 상위 5개와 건수, 아니면 그 종류의 페이지 */
    @Transactional(readOnly = true)
    public Object search(AuthMember me, String q, SearchType type, String cursor, int size) {
        String phrase = FullText.phrase(valid(q));
        return switch (type) {
            case ALL -> new SearchAllResponse(
                    new SearchAllResponse.Group(members.search(phrase, null, TOP).items(), members.searchCount(phrase)),
                    new SearchAllResponse.Group(questions.search(me, phrase, null, TOP).items(),
                            questions.searchCount(phrase)),
                    new SearchAllResponse.Group(shares.search(me.id(), phrase, null, TOP).items(),
                            shares.searchCount(phrase)),
                    new SearchAllResponse.Group(schedules.search(me, phrase, null, TOP).items(),
                            schedules.searchCount(phrase)));
            case MEMBER -> members.search(phrase, cursor, size);
            case QUESTION -> questions.search(me, phrase, cursor, size);
            case SHARE -> shares.search(me.id(), phrase, cursor, size);
            case SCHEDULE -> schedules.search(me, phrase, cursor, size);
        };
    }

    private static String valid(String q) {
        String query = q.strip();
        int length = query.codePointCount(0, query.length());
        if (length < QUERY_MIN || length > QUERY_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("q", "검색어는 2–30자로 입력해 주세요.")));
        }
        return query;
    }

}
