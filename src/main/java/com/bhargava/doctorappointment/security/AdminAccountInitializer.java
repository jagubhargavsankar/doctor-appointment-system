package com.bhargava.doctorappointment.security;

import com.bhargava.doctorappointment.entity.AppUser;
import com.bhargava.doctorappointment.enums.UserRole;
import com.bhargava.doctorappointment.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(AppUserRepository appUserRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.email:}") String adminEmail,
                                   @Value("${app.admin.password:}") String adminPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() && adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL and ADMIN_PASSWORD are not set. Admin account was not initialized.");
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException("Set both ADMIN_EMAIL and ADMIN_PASSWORD, or leave both empty.");
        }
        if (adminPassword.length() < 8) {
            throw new IllegalStateException("ADMIN_PASSWORD must contain at least 8 characters.");
        }
        if (!appUserRepository.existsByEmailIgnoreCase(adminEmail)) {
            appUserRepository.save(new AppUser(adminEmail, passwordEncoder.encode(adminPassword), UserRole.ADMIN));
            log.info("Initial admin account created for configured ADMIN_EMAIL.");
        }
    }
}
