package com.doFast.dofastapp.chat.repository;

import com.doFast.dofastapp.chat.entity.ChatReadState;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatReadStateRepository extends JpaRepository<ChatReadState, Long> {
    Optional<ChatReadState> findByJobAndUser(Job job, User user);
}
