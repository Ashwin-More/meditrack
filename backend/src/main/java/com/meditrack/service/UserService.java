package com.meditrack.service;

import com.meditrack.dto.CreateUserRequest;
import com.meditrack.dto.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
    List<UserResponse> getAllUsers();
    List<UserResponse> getPendingUsers();
    UserResponse approveUser(Integer userId);
    UserResponse rejectUser(Integer userId);
    UserResponse disableUser(Integer userId);
    UserResponse enableUser(Integer userId);
    UserResponse getCurrentUser(String username);
    UserResponse updateProfile(String username, String firstName, String lastName, String phone);
    void changePassword(String username, String currentPassword, String newPassword);
    void resendVerification(Integer userId);
}
