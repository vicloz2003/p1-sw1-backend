package com.ibpms.repository;

import com.ibpms.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    List<User> findByEmailContainingIgnoreCase(String email);

    /** Search by name (username) OR email — case-insensitive, both params must receive the same query. */
    List<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email);
    /** All users assigned to a specific department. */
    List<User> findByDepartmentId(String departmentId);
    /** Users whose FCM token is set (mobile device registered). */
    List<User> findByFcmTokenIsNotNull();
}


