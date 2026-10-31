package com.gdghost.share;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ShareTags {

    private ShareTags() {
    }

    /** 앞뒤 공백을 떼고 중복을 뺀다. null 항목은 검증에서 400이 되도록 남긴다 */
    static List<String> normalize(List<String> tags) {
        if (tags == null) {
            return null;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            unique.add(tag == null ? null : tag.strip());
        }
        return new ArrayList<>(unique);
    }

}
