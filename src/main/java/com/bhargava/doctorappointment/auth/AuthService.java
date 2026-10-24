package com.bhargava.doctorappointment.auth;

import com.bhargava.doctorappointment.dto.AuthResponse;
import com.bhargava.doctorappointment.dto.LoginRequest;
import com.bhargava.doctorappointment.dto.RegisterRequest;
import com.bhargava.doctorappointment.dto.RegistrationResponse;
import com.bhargava.doctorappointment.entity.AppUser;
import com.bhargava.doctorappointment.entity.Patient;
import com.bhargava.doctorappointment.enums.UserRole;
import com.bhargava.doctorappointment.repository.AppUserRepository;
import com.bhargava.doctorappointment.repository.PatientRepository;
import com.bhargava.doctorappointment.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AppUserRepository appUserRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository,
                       PatientRepository patientRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegistrationResponse registerPatient(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        AppUser user = new AppUser(email, passwordEncoder.encode(request.getPassword()), UserRole.PATIENT);
        user = appUserRepository.save(user);

        Patient patient = new Patient();
        patient.setName(request.getName().trim());
        patient.setEmail(email);
        patient.setPhone(request.getPhone().trim());
        patient.setUser(user);
        patientRepository.save(patient);

        return new RegistrationResponse("Patient account created. Please log in.", email, UserRole.PATIENT.name());
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, jwtService.getExpirationSeconds(), user.getEmail(), user.getRole().name());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
