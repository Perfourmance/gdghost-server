package com.gdghost.auth;

/** reason: TAKEN · RESERVED, 쓸 수 있으면 null */
public record NicknameAvailabilityResponse(boolean available, Reason reason) {

    public enum Reason {
        TAKEN, RESERVED
    }

    static NicknameAvailabilityResponse ok() {
        return new NicknameAvailabilityResponse(true, null);
    }

    static NicknameAvailabilityResponse no(Reason reason) {
        return new NicknameAvailabilityResponse(false, reason);
    }

}
