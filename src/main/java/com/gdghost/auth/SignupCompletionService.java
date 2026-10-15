package com.gdghost.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.invite.InviteCode;
import com.gdghost.invite.InviteCodeRepository;
import com.gdghost.invite.InviteCodeState;
import com.gdghost.member.Member;
import com.gdghost.member.MemberProfile;
import com.gdghost.member.MemberRepository;
import com.gdghost.member.MemberTagWriter;
import com.gdghost.member.Nicknames;
import com.gdghost.tag.TagService;

@Service
public class SignupCompletionService {

    // 8–64자(BCrypt 72바이트 한계 안), 영문 · 숫자 · 특수문자 모두, 공백 없음
    private static final Pattern PASSWORD_RULE =
            Pattern.compile("(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,64}");

    private final SignupSessionStore sessions;
    private final InviteCodeRepository inviteCodes;
    private final MemberRepository members;
    private final MemberTagWriter memberTags;
    private final TagService tagService;
    private final PasswordEncoder passwordEncoder;
    private final LoginTokenService loginTokens;
    private final MeReader meReader;
    private final Clock clock;

    public SignupCompletionService(SignupSessionStore sessions, InviteCodeRepository inviteCodes,
            MemberRepository members, MemberTagWriter memberTags, TagService tagService,
            PasswordEncoder passwordEncoder, LoginTokenService loginTokens, MeReader meReader, Clock clock) {
        this.sessions = sessions;
        this.inviteCodes = inviteCodes;
        this.members = members;
        this.memberTags = memberTags;
        this.tagService = tagService;
        this.passwordEncoder = passwordEncoder;
        this.loginTokens = loginTokens;
        this.meReader = meReader;
        this.clock = clock;
    }

    /**
     * 한 트랜잭션: 초대 코드 잠금 → 유효 확인 → used_count + 1 → member · member_tag → login_token.
     * 기수 · 메일은 요청이 아니라 signupToken에서 꺼낸다.
     * @param previousToken 이 기기에 이미 있던 로그인 토큰 — 로그인과 같이 지운다(기기당 한 행)
     */
    @Transactional
    public SignupResult signup(SignupRequest request, String userAgent, String previousToken) {
        SignupSession session = sessions.get(request.signupToken());
        if (session.verifiedEmail() == null) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_EXPIRED, "이메일 인증을 먼저 마쳐 주세요.");
        }
        if (!PASSWORD_RULE.matcher(request.password()).matches()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY, "비밀번호는 8자 이상, 영문 · 숫자 · 특수문자를 모두 넣어 주세요.");
        }
        SignupProfileRequest profile = request.profile();
        String nickname = profile.nickname().strip();
        if (Nicknames.isReserved(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_RESERVED, "'익명'으로 시작하는 닉네임은 쓸 수 없습니다.");
        }
        List<Long> skillIds = orEmpty(profile.skillTagIds());
        List<Long> experienceIds = orEmpty(profile.experienceTagIds());
        List<Long> tagIds = tagService.validateSelection("profile", profile.interestTagIds(), skillIds, experienceIds);

        Instant now = clock.instant();
        InviteCode inviteCode = inviteCodes.findByIdForUpdate(session.inviteCodeId())
                .filter(code -> code.state(now) == InviteCodeState.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_INVALID, "초대 코드를 더 쓸 수 없습니다."));
        checkNotTaken(request.loginId(), nickname, session.verifiedEmail());
        inviteCode.use();

        Member member = Member.join(request.loginId(), passwordEncoder.encode(request.password()),
                session.verifiedEmail(), inviteCode.getGeneration(), inviteCode.getId(),
                new MemberProfile(profile.name().strip(), nickname, profile.position(), profile.studentNo(),
                        profile.department().strip(), profile.status(), blankToNull(profile.statusNote()),
                        blankToNull(profile.bio())),
                now);
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw taken(e);
        }
        memberTags.add(member.getId(), tagIds);
        if (previousToken != null) {
            loginTokens.revoke(previousToken);
        }
        String loginToken = loginTokens.issue(member.getId(), true, userAgent);
        // TODO 커밋 후 부원 임베딩 생성 요청(7단계)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessions.close(request.signupToken());
            }
        });

        return new SignupResult(loginToken, meReader.read(member));
    }

    // 대소문자만 다른 아이디도 같은 것으로 본다 (DB 정렬 규칙이 대소문자 무시)
    private void checkNotTaken(String loginId, String nickname, String email) {
        if (members.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.LOGIN_ID_TAKEN, "이미 쓰는 아이디입니다.");
        }
        if (members.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN, "이미 쓰는 닉네임입니다.");
        }
        if (members.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED, "이미 가입된 이메일입니다.");
        }
    }

    /** 위 확인과 저장 사이에 다른 사람이 먼저 가입한 경우 — UNIQUE 위반을 같은 409로 */
    private static RuntimeException taken(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains("uk_member_login_id")) {
            return new BusinessException(ErrorCode.LOGIN_ID_TAKEN, "이미 쓰는 아이디입니다.");
        }
        if (message.contains("uk_member_nickname")) {
            return new BusinessException(ErrorCode.NICKNAME_TAKEN, "이미 쓰는 닉네임입니다.");
        }
        if (message.contains("uk_member_email")) {
            return new BusinessException(ErrorCode.EMAIL_ALREADY_USED, "이미 가입된 이메일입니다.");
        }
        return e;
    }

    private static List<Long> orEmpty(List<Long> ids) {
        return ids != null ? ids : List.of();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

}
