package com.doFast.dofastapp.user.repository;

import com.doFast.dofastapp.user.entity.UserServiceCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserServiceCategoryRepository extends JpaRepository<UserServiceCategory, Long> {

    @EntityGraph(attributePaths = {"category", "category.parent"})
    List<UserServiceCategory> findAllByUser_IdOrderByCategory_SortOrderAscCategory_NameAsc(Long userId);
}
