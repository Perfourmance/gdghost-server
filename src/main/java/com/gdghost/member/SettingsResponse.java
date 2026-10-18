package com.gdghost.member;

public record SettingsResponse(boolean acceptQuestion, boolean acceptCoffee, String coffeeMemo,
        boolean hiddenFromList, boolean pushOn) {

    static SettingsResponse of(Member member) {
        return new SettingsResponse(member.isAcceptQuestion(), member.isAcceptCoffee(), member.getCoffeeMemo(),
                member.isHiddenFromList(), member.isPushOn());
    }

}
