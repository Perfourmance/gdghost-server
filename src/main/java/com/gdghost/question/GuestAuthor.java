package com.gdghost.question;

/** 비회원 목록의 작성자 — 닉네임이나 label 하나만 */
public sealed interface GuestAuthor {

    record Named(String nickname) implements GuestAuthor {
    }

    record Anonymous(String label) implements GuestAuthor {
    }

    static GuestAuthor of(AuthorRef.Row author, boolean anonymous) {
        if (anonymous) {
            return new Anonymous(AnonLabels.QUESTION_AUTHOR);
        }
        return new Named(author.withdrawn() ? AuthorRef.WITHDRAWN_NICKNAME : author.nickname());
    }

}
