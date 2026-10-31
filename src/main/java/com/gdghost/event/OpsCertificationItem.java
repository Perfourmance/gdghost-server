package com.gdghost.event;

import java.time.Instant;
import java.util.List;

import com.gdghost.question.AuthorRef;
import com.gdghost.upload.Attachment;

public record OpsCertificationItem(long id, EventRef event, AuthorRef author, String body, List<Attachment> photos,
        CertificationState state, String rejectReason, boolean mine, Instant createdAt) {

    public record EventRef(long id, String name) {
    }

}
