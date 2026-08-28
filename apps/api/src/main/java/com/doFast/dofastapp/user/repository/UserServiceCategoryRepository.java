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
}
