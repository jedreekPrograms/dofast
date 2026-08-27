package com.doFast.dofastapp.job.search;

import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
    List<SavedSearch> findAllByUserOrderByUpdatedAtDescIdDesc(User user);
    Optional<SavedSearch> findByIdAndUser(Long id, User user);
    long countByUser(User user);
    boolean existsByUserAndNameIgnoreCase(User user, String name);
    boolean existsByUserAndNameIgnoreCaseAndIdNot(User user, String name, Long id);

    @Query("select s from SavedSearch s join fetch s.user left join fetch s.category c left join fetch c.parent where s.alertsEnabled = true")
    List<SavedSearch> findAllAlertEnabled();
}
