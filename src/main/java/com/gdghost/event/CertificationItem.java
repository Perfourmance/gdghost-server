package com.gdghost.event;

import java.time.Instant;
import java.util.List;

import com.gdghost.question.AuthorRef;
import com.gdghost.upload.Attachment;

/** rejectReason은 작성자 본인과 운영진에게만 */
public record CertificationItem(long id, AuthorRef author, String body, List<Attachment> photos,
        CertificationState state, String rejectReason, boolean mine, Instant createdAt) {
}
