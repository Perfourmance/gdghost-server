package com.gdghost.member;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;

@Service
public class MyProfileService {

    private final MemberRepository members;
    private final MemberProfileReader profileReader;
    private final ProfileCompletenessReader completenessReader;
    private final Clock clock;

    public MyProfileService(MemberRepository members, MemberProfileReader profileReader,
            ProfileCompletenessReader completenessReader, Clock clock) {
        this.members = members;
        this.profileReader = profileReader;
        this.completenessReader = completenessReader;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyProfileResponse read(long memberId) {
        Member member = find(memberId);
        MemberProfileReader.Tags tags = profileReader.tags(memberId);
        Instant now = clock.instant();
        return new MyProfileResponse(member.getId(), member.getLoginId(), member.getEmail(), member.getName(),
                member.getNickname(), member.getGeneration(), member.getStudentNo(), member.getDepartment(),
                member.getPosition(), member.getStatus(), member.getRole(), tags.interests(), tags.experiences(),
                member.getStatusNote(), member.getStatusNoteUpdatedAt(), member.getBio(),
                profileReader.contacts(memberId), SettingsResponse.of(member),
                member.nicknameChangeAvailableAt(now), member.emailChangeAvailableAt(now),
                completenessReader.read(member));
    }

    @Transactional
    public SettingsResponse changeSettings(long memberId, SettingsRequest request) {
        Member member = find(memberId);
        boolean acceptCoffee = valueOr(request.acceptCoffee(), member.isAcceptCoffee());
        String coffeeMemo = request.coffeeMemo() == null ? member.getCoffeeMemo() : blankToNull(request.coffeeMemo());
        if (acceptCoffee && coffeeMemo == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("coffeeMemo", "커피챗을 받으려면 수락 시 보일 한 줄을 적어 주세요.")));
        }
        member.changeSettings(valueOr(request.acceptQuestion(), member.isAcceptQuestion()), acceptCoffee, coffeeMemo,
                valueOr(request.hiddenFromList(), member.isHiddenFromList()),
                valueOr(request.pushOn(), member.isPushOn()), clock.instant());
        return SettingsResponse.of(member);
    }

    private Member find(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    private static boolean valueOr(Boolean value, boolean current) {
        return value != null ? value : current;
    }

    private static String blankToNull(String value) {
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

}
