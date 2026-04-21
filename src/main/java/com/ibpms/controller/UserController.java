package com.ibpms.controller;

import com.ibpms.dto.request.AssignDepartmentRequest;
import com.ibpms.dto.response.UserResponse;
import com.ibpms.service.api.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userService.getAll());
    }

    @PatchMapping("/{id}/department")
    @PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
    public ResponseEntity<UserResponse> assignDepartment(
            @PathVariable String id,
            @Valid @RequestBody AssignDepartmentRequest request) {
        return ResponseEntity.ok(userService.assignDepartment(id, request));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('ADMIN_DESIGNER', 'EMPLOYEE')")
    public ResponseEntity<List<UserResponse>> search(
            @RequestParam String email) {
        return ResponseEntity.ok(userService.searchByEmail(email));
    }
}

