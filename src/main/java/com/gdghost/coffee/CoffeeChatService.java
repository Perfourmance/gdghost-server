package com.gdghost.coffee;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.page.CursorPage;
import com.gdghost.member.CoffeeRequestReader;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

/** 서비스는 성사까지만 책임진다. 연락 수단은 주고받지 않고, 수락 순간의 메모만 신청자에게 14일 보여준다 */
@Service
public class CoffeeChatService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MIN_PURPOSE = 10;

    private final CoffeeChatRepository chats;
    private final CoffeeChatReader reader;
    private final CoffeeRequestReader requests;
    private final MemberRepository members;
    private final NotificationWriter notifications;
    private final Clock clock;

    public CoffeeChatService(CoffeeChatRepository chats, CoffeeChatReader reader, CoffeeRequestReader requests,
            MemberRepository members, NotificationWriter notifications, Clock clock) {
        this.chats = chats;
        this.reader = reader;
        this.requests = requests;
        this.members = members;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** 받는 사람 행을 잠가 같은 상대에게 동시에 신청해도 하나만 들어간다 */
    @Transactional
    public CoffeeChatItem request(long requesterId, long receiverId, String purpose) {
        String trimmed = purpose.strip();
        if (trimmed.length() < MIN_PURPOSE) {
            throw invalid("purpose", "무엇이 궁금한지 10자 이상 적어 주세요.");
        }
        if (requesterId == receiverId) {
            throw invalid("receiverId", "본인에게는 신청할 수 없습니다.");
        }
        Member receiver = members.findByIdForUpdate(receiverId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!receiver.isAcceptCoffee()) {
            throw new BusinessException(ErrorCode.COFFEE_DISABLED);
        }
        Instant now = clock.instant();
        checkNotBlocked(requests.latest(requesterId, receiverId), now);

        CoffeeChat chat = chats.saveAndFlush(CoffeeChat.request(requesterId, receiverId, trimmed, now));
        notifications.toMember(receiverId, NotificationType.COFFEE_REQUESTED, "커피챗 신청이 왔어요",
                nickname(requesterId) + "님이 커피챗을 신청했어요", NotificationTarget.coffeeChat(chat.getId()));
        return reader.find(requesterId, chat.getId());
    }

    /** 받은 사람만. 수락 후에는 되돌릴 수 없다 */
    @Transactional
    public CoffeeChatItem respond(long memberId, long chatId, CoffeeChatRespondRequest.Action action) {
        CoffeeChat chat = chats.findByIdForUpdate(chatId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (chat.getReceiverId() != memberId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Instant now = clock.instant();
        if (!chat.isRespondable(now)) {
            throw new BusinessException(ErrorCode.COFFEE_ALREADY_RESPONDED);
        }
        String nickname = nickname(memberId);
        NotificationTarget target = NotificationTarget.coffeeChat(chatId);
        if (action == CoffeeChatRespondRequest.Action.ACCEPT) {
            chat.accept(acceptMemo(memberId), now);
            notifications.toMember(chat.getRequesterId(), NotificationType.COFFEE_ACCEPTED, "커피챗이 수락됐어요",
                    nickname + "님의 메모를 확인하고 먼저 연락해 보세요", target);
        } else {
            chat.decline(now);
            notifications.toMember(chat.getRequesterId(), NotificationType.COFFEE_DECLINED, "커피챗 응답이 왔어요",
                    nickname + "님은 이번엔 어렵대요", target);
        }
        chats.flush();
        return reader.find(memberId, chatId);
    }

    @Transactional(readOnly = true)
    public CursorPage<CoffeeChatItem> list(long memberId, CoffeeChatBox box, String cursor, int size) {
        return CursorPage.of(reader.list(memberId, box, afterId(cursor), size + 1), size,
                item -> String.valueOf(item.id()));
    }

    private static void checkNotBlocked(Optional<CoffeeRequestReader.Latest> latest, Instant now) {
        if (latest.isEmpty()) {
            return;
        }
        if (latest.get().isPending()) {
            throw new BusinessException(ErrorCode.COFFEE_PENDING_EXISTS);
        }
        Instant until = latest.get().cooldownUntil(now);
        if (until != null) {
            throw new BusinessException(ErrorCode.COFFEE_COOLDOWN,
                    LocalDate.ofInstant(until, KST) + "부터 다시 신청할 수 있습니다.");
        }
    }

    /** 메모가 없으면 수락해도 신청자가 연락할 방법이 없다 */
    private String acceptMemo(long memberId) {
        String memo = members.findById(memberId).orElseThrow().getCoffeeMemo();
        if (memo == null) {
            throw invalid("coffeeMemo", "수락하기 전에 설정에서 연락받을 한 줄을 적어 주세요.");
        }
        return memo;
    }

    private String nickname(long memberId) {
        return members.findById(memberId).orElseThrow().getNickname();
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

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, null, List.of(new ErrorField(field, message)));
    }

}
