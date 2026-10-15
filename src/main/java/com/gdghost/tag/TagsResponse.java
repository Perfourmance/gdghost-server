package com.gdghost.tag;

import java.util.List;

public record TagsResponse(List<Interest> interests, List<Experience> experiences) {

    public record Interest(long id, String name, int sortOrder, List<Skill> skills) {
    }

    public record Skill(long id, String name) {
    }

    public record Experience(long id, String name, String groupName) {
    }

}
