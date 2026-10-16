package com.gdghost.member;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByLoginIdIn(Collection<String> loginIds);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, long id);

}
