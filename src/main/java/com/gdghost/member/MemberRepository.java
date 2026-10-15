package com.gdghost.member;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByLoginIdIn(Collection<String> loginIds);

    boolean existsByEmail(String email);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, long id);

}
