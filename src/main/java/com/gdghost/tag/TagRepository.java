package com.gdghost.tag;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByOrderBySortOrderAscIdAsc();

}
