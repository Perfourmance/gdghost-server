package com.gdghost.member;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.Role;
import com.gdghost.member.MyProfileResponse.TagItem;

/** 부원 목록 · 프로필 상세 · 활동 기록 */
@Service
public class MemberService {

    private final MemberRepository members;
    private final MemberDirectoryReader directoryReader;
    private final MemberProfileReader profileReader;
    private final MemberActivityReader activityReader;

    public MemberService(MemberRepository members, MemberDirectoryReader directoryReader,
            MemberProfileReader profileReader, MemberActivityReader activityReader) {
        this.members = members;
        this.directoryReader = directoryReader;
        this.profileReader = profileReader;
        this.activityReader = activityReader;
    }

    @Transactional(readOnly = true)
    public CursorPage<MemberSummary> list(Integer generation, MemberStatus status, String cursor, int size) {
        Integer afterGeneration = null;
        String afterNickname = null;
        String raw = CursorPage.decode(cursor);
        if (raw != null) {
            String[] parts = raw.split(":", 2);
            try {
                afterGeneration = Integer.valueOf(parts[0]);
                afterNickname = parts[1];
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                throw CursorPage.invalidCursor();
            }
        }
        return CursorPage.of(directoryReader.list(generation, status, afterGeneration, afterNickname, size + 1),
                size, row -> row.generation() + ":" + row.nickname());
    }

    // TODO coffeeChat 채우기(10단계)
    @Transactional(readOnly = true)
    public MemberProfileView profile(long viewerId, long memberId) {
        Member member = findVisible(memberId);
        boolean self = viewerId == memberId;
        MemberProfileReader.Tags tags = profileReader.tags(memberId);
        List<MemberProfileView.Contact> contacts = profileReader.contacts(memberId).stream()
                .filter(contact -> self || contact.isPublic())
                .map(contact -> new MemberProfileView.Contact(contact.type(), contact.value()))
                .toList();
        return new MemberProfileView(member.getId(), member.getNickname(), member.getName(),
                member.getGeneration(), member.getStudentNo(), member.getDepartment(), member.getPosition(),
                member.getStatus(), member.getRole() == Role.STAFF,
                tags.interests().stream()
                        .map(interest -> new MemberProfileView.Interest(interest.name(),
                                interest.skills().stream().map(TagItem::name).toList()))
                        .toList(),
                tags.experiences().stream().map(TagItem::name).toList(),
                member.getStatusNote(), member.getStatusNoteUpdatedAt(), member.getBio(), contacts,
                member.isAcceptQuestion(), member.isAcceptCoffee(), MemberProfileView.CoffeeChat.NONE,
                activityReader.stats(memberId));
    }

    // TODO FEED(9단계)
    @Transactional(readOnly = true)
    public CursorPage<ActivityItem> activities(long viewerId, long memberId, ActivityType type, String cursor,
            int size) {
        findVisible(memberId);
        boolean self = viewerId == memberId;
        Long afterId = afterId(cursor);
        List<ActivityItem.Row> rows = switch (type) {
            case ANSWER -> activityReader.answers(memberId, self, afterId, size + 1);
            case QUESTION -> activityReader.questions(memberId, self, afterId, size + 1);
            case SHARE -> activityReader.shares(memberId, self, afterId, size + 1);
            case FEED -> List.of();
        };
        CursorPage<ActivityItem.Row> page = CursorPage.of(rows, size, row -> String.valueOf(row.id()));
        return new CursorPage<>(page.items().stream().map(ActivityItem.Row::item).toList(), page.nextCursor(),
                page.hasMore());
    }

    /** 없거나 탈퇴 · 정지한 부원은 404. 목록 숨김은 직접 열면 보인다 */
    private Member findVisible(long memberId) {
        return members.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "부원을 찾을 수 없습니다."));
    }

    private static Long afterId(String cursor) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            throw CursorPage.invalidCursor();
        }
    }

}
