package edu.upb.semillero.controller;

import edu.upb.semillero.repository.dto.request.LoginRequestDto;
import edu.upb.semillero.repository.dto.response.LoginResponseDto;
import edu.upb.semillero.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationService service;

    public AuthenticationController(AuthenticationService service) { this.service = service; }

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return service.login(request);
    }
}
