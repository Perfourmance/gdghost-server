package com.gdghost.upload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AttachmentStore {

    private static final int CHUNK = 500;

    private final NamedParameterJdbcTemplate jdbc;

    AttachmentStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Row(long ownerId, String objectKey, int sortOrder) {
    }

    void insert(UploadPurpose ownerType, long ownerId, List<String> objectKeys) {
        for (int i = 0; i < objectKeys.size(); i++) {
            jdbc.update("""
                    INSERT INTO attachment (owner_type, owner_id, object_key, sort_order)
                    VALUES (:type, :owner, :key, :sort)
                    """, new MapSqlParameterSource()
                    .addValue("type", ownerType.name())
                    .addValue("owner", ownerId)
                    .addValue("key", objectKeys.get(i))
                    .addValue("sort", i));
        }
    }

    void delete(UploadPurpose ownerType, long ownerId) {
        jdbc.update("DELETE FROM attachment WHERE owner_type = :type AND owner_id = :owner",
                new MapSqlParameterSource().addValue("type", ownerType.name()).addValue("owner", ownerId));
    }

    List<Row> find(UploadPurpose ownerType, Collection<Long> ownerIds) {
        return jdbc.query("""
                SELECT owner_id, object_key, sort_order
                FROM attachment
                WHERE owner_type = :type AND owner_id IN (:owners)
                ORDER BY owner_id, sort_order
                """, new MapSqlParameterSource().addValue("type", ownerType.name()).addValue("owners", ownerIds),
                (rs, i) -> new Row(rs.getLong("owner_id"), rs.getString("object_key"), rs.getInt("sort_order")));
    }

    /** 이미 글에 붙은 키만 골라 돌려준다 */
    Set<String> attachedAmong(List<String> objectKeys) {
        Set<String> attached = new HashSet<>();
        for (int i = 0; i < objectKeys.size(); i += CHUNK) {
            List<String> chunk = new ArrayList<>(objectKeys.subList(i, Math.min(i + CHUNK, objectKeys.size())));
            attached.addAll(jdbc.queryForList("SELECT object_key FROM attachment WHERE object_key IN (:keys)",
                    new MapSqlParameterSource("keys", chunk), String.class));
        }
        return attached;
    }

}
