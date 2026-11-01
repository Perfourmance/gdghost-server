package com.gdghost.coffee;

import jakarta.validation.constraints.NotNull;

public record CoffeeChatRespondRequest(@NotNull Action action) {

    public enum Action {
        ACCEPT, DECLINE
    }

}
