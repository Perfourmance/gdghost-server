package com.gdghost.event;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long eventId;
    private long memberId;
    private String body;

    @Enumerated(EnumType.STRING)
    private CertificationState state;

    private String rejectReason;
    private Long reviewedBy;
    private Instant reviewedAt;
    private Instant createdAt;
    private Instant updatedAt;

    static Certification submit(long eventId, long memberId, String body, Instant now) {
        Certification certification = new Certification();
        certification.eventId = eventId;
        certification.memberId = memberId;
        certification.body = body;
        certification.state = CertificationState.PENDING;
        certification.createdAt = now;
        return certification;
    }

    void approve(long staffId, Instant now) {
        state = CertificationState.APPROVED;
        reviewedBy = staffId;
        reviewedAt = now;
    }

    void reject(long staffId, String reason, Instant now) {
        state = CertificationState.REJECTED;
        rejectReason = reason;
        reviewedBy = staffId;
        reviewedAt = now;
    }

    /** 검토 기록을 비우고 다시 확인 대기로. body가 null이면 그대로 */
    void resubmit(String body, Instant now) {
        if (body != null) {
            this.body = body;
        }
        state = CertificationState.PENDING;
        rejectReason = null;
        reviewedBy = null;
        reviewedAt = null;
        updatedAt = now;
    }

}
