package com.doFast.dofastapp.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserService userService;
    private final String email;
    private final String password;
    private final String nickname;

    public AdminBootstrapRunner(
            UserService userService,
            @Value("${dofast.security.admin-bootstrap.email:}") String email,
            @Value("${dofast.security.admin-bootstrap.password:}") String password,
            @Value("${dofast.security.admin-bootstrap.nickname:doFast Admin}") String nickname
    ) {
        this.userService = userService;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.length() < 12) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_EMAIL and a password of at least 12 characters are both required"
            );
        }

        userService.ensureBootstrapAdmin(email, password, nickname);
        log.info("Administrative account bootstrap is configured for {}", email.trim().toLowerCase());
    }
}
