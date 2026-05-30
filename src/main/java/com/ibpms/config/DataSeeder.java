package com.ibpms.config;

import com.ibpms.domain.Department;
import com.ibpms.domain.User;
import com.ibpms.domain.enums.SystemRole;
import com.ibpms.repository.DepartmentRepository;
import com.ibpms.repository.UserRepository;
import com.ibpms.service.impl.DepartmentServiceImpl;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      DepartmentRepository departmentRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        seedDepartments();
        seedUsers();
    }

    private void seedDepartments() {
        // Idempotent per record: insert only the departments that don't exist yet.
        // This avoids duplicates regardless of how many times the seeder has run.
        record DeptDef(String name, String description) {}

        List<DeptDef> required = List.of(
            new DeptDef("Atencion al Cliente",  "Departamento de atencion y registro de solicitudes"),
            new DeptDef("Departamento Tecnico", "Verificacion tecnica y viabilidad"),
            new DeptDef("Departamento Legal",   "Revision legal y firma de contratos"),
            new DeptDef("Facturacion",          "Gestion de pagos y facturacion"),
            new DeptDef("Almacen",              "Gestion de materiales y equipos")
        );

        int created = 0;
        for (DeptDef def : required) {
            String canonical = DepartmentServiceImpl.normalize(def.name());
            if (!departmentRepository.existsByName(canonical)) {
                departmentRepository.save(new Department(null, canonical, def.description()));
                created++;
            }
        }
        if (created > 0) {
            System.out.println("[SEED] Departamentos creados: " + created);
        }
    }

    private void seedUsers() {
        // Idempotent per record: insert only the users whose email doesn't exist yet.
        record UserDef(String username, String email, String rawPassword, SystemRole role) {}

        List<UserDef> required = List.of(
            new UserDef("admin",      "admin@ibpms.com",       "Admin123x",    SystemRole.ADMIN_DESIGNER),
            new UserDef("admin02",    "admin02@ibpms.com",     "Admin123x",    SystemRole.ADMIN_DESIGNER),
            new UserDef("empleado01", "empleado01@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("empleado02", "empleado02@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("empleado03", "empleado03@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("empleado04", "empleado04@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("empleado05", "empleado05@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("empleado06", "empleado06@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("empleado07", "empleado07@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("empleado08", "empleado08@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("empleado09", "empleado09@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("empleado10", "empleado10@ibpms.com",  "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("cliente01",  "cliente01@ibpms.com",   "Cliente123x",  SystemRole.CLIENT),
            new UserDef("cliente02",  "cliente02@ibpms.com",   "Cliente123x",  SystemRole.CLIENT),
            new UserDef("cliente03",  "cliente03@ibpms.com",   "Cliente123x",  SystemRole.CLIENT)
        );

        int created = 0;
        for (UserDef def : required) {
            if (!userRepository.existsByEmail(def.email())) {
                userRepository.save(
                    createUser(def.username(), def.email(),
                               passwordEncoder.encode(def.rawPassword()),
                               def.role(), null));
                created++;
            }
        }
        if (created > 0) {
            System.out.println("[SEED] Usuarios creados: " + created);
        }
    }

    private User createUser(String username, String email,
                            String password, SystemRole role,
                            String departmentId) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        user.setRole(role);
        user.setDepartmentId(departmentId);
        return user;
    }

    private Department createDept(String name, String description) {
        return new Department(null, name, description);
    }
}
