package com.assistia.controller;

import com.assistia.dto.auth.AuthenticatedUserResponse;
import com.assistia.dto.auth.CreateManagedUserRequest;
import com.assistia.service.UserAdministrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserAdministrationService userAdministrationService;

    public AdminUserController(UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    @PostMapping
    public ResponseEntity<AuthenticatedUserResponse> createUser(
            @Valid @RequestBody CreateManagedUserRequest request) {
        AuthenticatedUserResponse response =
                AuthenticatedUserResponse.from(userAdministrationService.createUser(request));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri()).body(response);
    }

    @GetMapping("/{id}")
    public AuthenticatedUserResponse getUser(@PathVariable Long id) {
        return AuthenticatedUserResponse.from(userAdministrationService.getUser(id));
    }
}
