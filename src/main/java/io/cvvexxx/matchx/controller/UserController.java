package io.cvvexxx.matchx.controller;


import io.cvvexxx.matchx.dto.CreateUserRequest;
import io.cvvexxx.matchx.dto.UserDto;
import io.cvvexxx.matchx.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> registerUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.registerUser(request));
    }

}
