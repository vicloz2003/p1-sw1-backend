package com.ibpms.config;

import com.ibpms.domain.Department;
import com.ibpms.domain.User;
import com.ibpms.domain.enums.SystemRole;
import com.ibpms.repository.DepartmentRepository;
import com.ibpms.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
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
        if (departmentRepository.count() > 0) return;

        List<Department> departments = List.of(
            createDept("Atencion al Cliente",
                "Departamento de atencion y registro de solicitudes"),
            createDept("Departamento Tecnico",
                "Verificacion tecnica y viabilidad"),
            createDept("Departamento Legal",
                "Revision legal y firma de contratos"),
            createDept("Facturacion",
                "Gestion de pagos y facturacion"),
            createDept("Almacen",
                "Gestion de materiales y equipos")
        );
        departmentRepository.saveAll(departments);
        System.out.println("[SEED] Departamentos creados: " + departments.size());
    }

    private void seedUsers() {
        if (userRepository.count() > 0) return;

        String adminPass = passwordEncoder.encode("Admin123x");
        String empPass = passwordEncoder.encode("Empleado123x");
        String clientPass = passwordEncoder.encode("Cliente123x");

        // ADMIN
        User admin = createUser("admin", "admin@ibpms.com",
            adminPass, SystemRole.ADMIN_DESIGNER, null);
        User admin2 = createUser("admin02", "admin02@ibpms.com",
            adminPass, SystemRole.ADMIN_DESIGNER, null);

        // EMPLEADOS — sin departamento asignado (se asignan desde la UI)
        User emp01 = createUser("empleado01", "empleado01@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);
        User emp02 = createUser("empleado02", "empleado02@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);
        User emp03 = createUser("empleado03", "empleado03@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);
        User emp04 = createUser("empleado04", "empleado04@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);
        User emp05 = createUser("empleado05", "empleado05@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);
        User emp06 = createUser("empleado06", "empleado06@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);
        User emp07 = createUser("empleado07", "empleado07@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);
        User emp08 = createUser("empleado08", "empleado08@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);
        User emp09 = createUser("empleado09", "empleado09@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);
        User emp10 = createUser("empleado10", "empleado10@ibpms.com",
            empPass, SystemRole.EMPLOYEE, null);

        // CLIENTES
        User client01 = createUser("cliente01", "cliente01@ibpms.com",
            clientPass, SystemRole.CLIENT, null);
        User client02 = createUser("cliente02", "cliente02@ibpms.com",
            clientPass, SystemRole.CLIENT, null);
        User client03 = createUser("cliente03", "cliente03@ibpms.com",
            clientPass, SystemRole.CLIENT, null);

        userRepository.saveAll(List.of(
            admin, admin2,
            emp01, emp02, emp03, emp04, emp05,
            emp06, emp07, emp08, emp09, emp10,
            client01, client02, client03
        ));
        System.out.println("[SEED] Usuarios creados: 15");
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
