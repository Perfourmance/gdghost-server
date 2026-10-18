package com.gdghost.member;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.member.MyProfileResponse.Contact;
import com.gdghost.member.MyProfileResponse.Interest;
import com.gdghost.member.MyProfileResponse.TagItem;
import com.gdghost.tag.TagService;

@Service
public class MyProfileService {

    private static final Pattern PHONE = Pattern.compile("[0-9-]+");

    private final MemberRepository members;
    private final MemberProfileReader profileReader;
    private final ProfileCompletenessReader completenessReader;
    private final MemberTagWriter tagWriter;
    private final MemberContactWriter contactWriter;
    private final TagService tagService;
    private final Clock clock;

    public MyProfileService(MemberRepository members, MemberProfileReader profileReader,
            ProfileCompletenessReader completenessReader, MemberTagWriter tagWriter,
            MemberContactWriter contactWriter, TagService tagService, Clock clock) {
        this.members = members;
        this.profileReader = profileReader;
        this.completenessReader = completenessReader;
        this.tagWriter = tagWriter;
        this.contactWriter = contactWriter;
        this.tagService = tagService;
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

    // TODO 매칭 재료가 바뀌면 커밋 후 부원 임베딩 재계산 요청(7단계)
    @Transactional
    public MyProfileResponse updateProfile(long memberId, ProfileUpdateRequest request) {
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        Instant now = clock.instant();
        if (request.nickname() != null) {
            changeNickname(member, request.nickname().strip(), now);
        }
        if (request.hasTagChange()) {
            tagWriter.replace(memberId, tagSelection(memberId, request));
        }
        member.changeProfile(
                request.department() != null ? request.department().strip() : member.getDepartment(),
                valueOr(request.position(), member.getPosition()),
                valueOr(request.status(), member.getStatus()),
                request.statusNote() != null ? blankToNull(request.statusNote()) : member.getStatusNote(),
                request.bio() != null ? blankToNull(request.bio()) : member.getBio(),
                now);
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 중복 확인과 저장 사이에 다른 사람이 같은 닉네임을 가져간 경우
            throw nicknameTaken();
        }
        return read(memberId);
    }

    @Transactional
    public ContactsResponse replaceContacts(long memberId, ContactsRequest request) {
        members.findByIdForUpdate(memberId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        List<Contact> contacts = new ArrayList<>();
        List<ErrorField> errors = new ArrayList<>();
        Set<ContactType> types = new HashSet<>();
        for (int i = 0; i < request.contacts().size(); i++) {
            ContactsRequest.Item item = request.contacts().get(i);
            String value = item.value().strip();
            if (!types.add(item.type())) {
                errors.add(new ErrorField("contacts[" + i + "].type", "같은 종류는 하나만 저장할 수 있습니다."));
            }
            if (item.type() == ContactType.PHONE && !PHONE.matcher(value).matches()) {
                errors.add(new ErrorField("contacts[" + i + "].value", "전화번호는 숫자와 하이픈만 쓸 수 있습니다."));
            }
            contacts.add(new Contact(item.type(), value, item.publicOrDefault()));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null, errors);
        }
        contactWriter.replace(memberId, contacts);
        return new ContactsResponse(profileReader.contacts(memberId));
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

    private void changeNickname(Member member, String nickname, Instant now) {
        if (nickname.equals(member.getNickname())) {
            return;
        }
        if (Nicknames.isReserved(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_RESERVED, "'익명'으로 시작하는 닉네임은 쓸 수 없습니다.");
        }
        member.checkNicknameChangeable(now);
        // 엔티티를 바꾸기 전에 확인한다 — 바꾼 뒤 쿼리를 날리면 자동 flush로 UNIQUE 위반이 먼저 난다
        if (members.existsByNicknameAndIdNot(nickname, member.getId())) {
            throw nicknameTaken();
        }
        member.changeNickname(nickname, now);
    }

    /**
     * 보낸 종류만 교체해 최종 선택을 검사한다.
     * 세부 기술을 안 보내면 남은 관심 분야의 것만 유지 — 빠진 분야의 세부 기술은 같이 지운다.
     */
    private List<Long> tagSelection(long memberId, ProfileUpdateRequest request) {
        MemberProfileReader.Tags current = profileReader.tags(memberId);
        List<Long> interestIds = request.interestTagIds() != null ? request.interestTagIds()
                : current.interests().stream().map(Interest::id).toList();
        List<Long> skillIds = request.skillTagIds() != null ? request.skillTagIds()
                : current.interests().stream()
                        .filter(interest -> interestIds.contains(interest.id()))
                        .flatMap(interest -> interest.skills().stream().map(TagItem::id))
                        .toList();
        List<Long> experienceIds = request.experienceTagIds() != null ? request.experienceTagIds()
                : current.experiences().stream().map(TagItem::id).toList();
        return tagService.validateSelection("profile", interestIds, skillIds, experienceIds);
    }

    private static BusinessException nicknameTaken() {
        return new BusinessException(ErrorCode.NICKNAME_TAKEN, "이미 쓰는 닉네임입니다.");
    }

    private Member find(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    private static <T> T valueOr(T value, T current) {
        return value != null ? value : current;
    }

    private static String blankToNull(String value) {
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

}
