package com.doFast.dofastapp.user.repository;

import com.doFast.dofastapp.user.entity.UserServiceCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserServiceCategoryRepository extends JpaRepository<UserServiceCategory, Long> {

    @EntityGraph(attributePaths = {"category", "category.parent"})
    @Query("""
            select relation
            from UserServiceCategory relation
            where relation.user.id = :userId
            order by relation.category.sortOrder asc, relation.category.name asc
            """)
    List<UserServiceCategory> findForUser(@Param("userId") Long userId);

    @Query("""
            select relation.category.id
            from UserServiceCategory relation
            where relation.user.id = :userId
              and relation.category.active = true
              and relation.category.parent is not null
              and relation.category.fulfillmentMode is not null
            order by relation.category.sortOrder asc, relation.category.name asc
            """)
    List<Long> findActiveCategoryIdsForUser(@Param("userId") Long userId);
}
