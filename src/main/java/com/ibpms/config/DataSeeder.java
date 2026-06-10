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
import java.util.Map;
import java.util.Optional;

/**
 * Bootstraps departments and users for ELECSUR — Empresa Eléctrica.
 *
 * <p>Runs at HIGHEST_PRECEDENCE so DemoDataSeeder (LOWEST) always finds users + departments.
 * All operations are idempotent: a record is inserted only if its email / name does not exist.
 *
 * <p><strong>Migration:</strong> on first run after upgrading from the generic seed
 * (empleadoXX / clienteXX), legacy users are removed automatically so there are no
 * duplicate ghost accounts.
 */
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
        cleanupLegacyDepartments();
        cleanupLegacyUsers();
        seedUsers();
        assignDepartmentsToEmployees();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Departments
    // ─────────────────────────────────────────────────────────────────────────

    private void seedDepartments() {
        record DeptDef(String name, String description) {}

        List<DeptDef> required = List.of(
            new DeptDef("Atencion al Cliente",
                        "Registro de solicitudes y atención al ciudadano"),
            new DeptDef("Departamento Tecnico",
                        "Inspección técnica, mediciones y viabilidad"),
            new DeptDef("Departamento Legal",
                        "Revisión contractual y conformidad legal"),
            new DeptDef("Facturacion",
                        "Gestión de cuentas, cobros y facturación"),
            new DeptDef("Operaciones de Campo",
                        "Instalación, reconexión y trabajos en campo")
        );

        int created = 0;
        for (DeptDef def : required) {
            String canonical = DepartmentServiceImpl.normalize(def.name());
            if (!departmentRepository.existsByName(canonical)) {
                departmentRepository.save(new Department(null, canonical, def.description()));
                created++;
            }
        }
        if (created > 0) System.out.println("[SEED] Departamentos creados: " + created);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy migration — remove old departments
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Removes departments that are no longer part of the ELECSUR vertical.
     * Safe to run repeatedly — only deletes if the department name still exists.
     */
    private void cleanupLegacyDepartments() {
        List<String> legacyNames = List.of("Almacen");
        long removed = legacyNames.stream()
            .map(departmentRepository::findByName)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .peek(departmentRepository::delete)
            .count();
        if (removed > 0) System.out.println("[SEED] Departamentos legacy eliminados: " + removed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy migration — remove old generic accounts
    // ─────────────────────────────────────────────────────────────────────────

    private void cleanupLegacyUsers() {
        List<String> legacyEmails = List.of(
            "empleado01@ibpms.com", "empleado02@ibpms.com", "empleado03@ibpms.com",
            "empleado04@ibpms.com", "empleado05@ibpms.com", "empleado06@ibpms.com",
            "empleado07@ibpms.com", "empleado08@ibpms.com", "empleado09@ibpms.com",
            "empleado10@ibpms.com",
            "cliente01@ibpms.com",  "cliente02@ibpms.com",  "cliente03@ibpms.com"
        );

        long removed = legacyEmails.stream()
            .map(userRepository::findByEmail)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .peek(userRepository::delete)
            .count();

        if (removed > 0) System.out.println("[SEED] Usuarios legacy eliminados: " + removed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Users
    // ─────────────────────────────────────────────────────────────────────────

    private void seedUsers() {
        record UserDef(String username, String email, String rawPassword, SystemRole role) {}

        List<UserDef> required = List.of(
            // ── Administradores ──────────────────────────────────────────────
            new UserDef("admin",            "admin@ibpms.com",                  "Admin123x",    SystemRole.ADMIN_DESIGNER),
            new UserDef("admin02",          "admin02@ibpms.com",                "Admin123x",    SystemRole.ADMIN_DESIGNER),

            // ── Empleados — Atención al Cliente ──────────────────────────────
            new UserDef("carlos.mendoza",   "carlos.mendoza@elecsur.com",       "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("patricia.suarez",  "patricia.suarez@elecsur.com",      "Empleado123x", SystemRole.EMPLOYEE),

            // ── Empleados — Departamento Técnico ─────────────────────────────
            new UserDef("miguel.torres",    "miguel.torres@elecsur.com",        "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("roberto.castillo", "roberto.castillo@elecsur.com",     "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("fernando.vega",    "fernando.vega@elecsur.com",        "Empleado123x", SystemRole.EMPLOYEE),

            // ── Empleados — Departamento Legal ───────────────────────────────
            new UserDef("ana.rodriguez",    "ana.rodriguez@elecsur.com",        "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("diego.morales",    "diego.morales@elecsur.com",        "Empleado123x", SystemRole.EMPLOYEE),

            // ── Empleados — Facturación ───────────────────────────────────────
            new UserDef("gabriela.herrera", "gabriela.herrera@elecsur.com",     "Empleado123x", SystemRole.EMPLOYEE),
            new UserDef("luis.paredes",     "luis.paredes@elecsur.com",         "Empleado123x", SystemRole.EMPLOYEE),

            // ── Empleados — Operaciones de Campo ─────────────────────────────
            new UserDef("jorge.espinoza",   "jorge.espinoza@elecsur.com",       "Empleado123x", SystemRole.EMPLOYEE),

            // ── Clientes ─────────────────────────────────────────────────────
            new UserDef("maria.garcia",     "maria.garcia@gmail.com",           "Cliente123x",  SystemRole.CLIENT),
            new UserDef("juan.perez",       "juan.perez@gmail.com",             "Cliente123x",  SystemRole.CLIENT),
            new UserDef("rosa.villacis",    "rosa.villacis@gmail.com",          "Cliente123x",  SystemRole.CLIENT),
            new UserDef("andres.fuentes",   "andres.fuentes@gmail.com",         "Cliente123x",  SystemRole.CLIENT),
            new UserDef("luisa.tamayo",     "luisa.tamayo@gmail.com",           "Cliente123x",  SystemRole.CLIENT)
        );

        int created = 0;
        for (UserDef def : required) {
            if (!userRepository.existsByEmail(def.email())) {
                User u = new User();
                u.setUsername(def.username());
                u.setEmail(def.email());
                u.setPassword(passwordEncoder.encode(def.rawPassword()));
                u.setRole(def.role());
                userRepository.save(u);
                created++;
            }
        }
        if (created > 0) System.out.println("[SEED] Usuarios creados: " + created);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Department assignment — specific per employee
    // ─────────────────────────────────────────────────────────────────────────

    private void assignDepartmentsToEmployees() {
        // email → canonical department name
        Map<String, String> assignments = Map.ofEntries(
            Map.entry("carlos.mendoza@elecsur.com",   "Atencion Al Cliente"),
            Map.entry("patricia.suarez@elecsur.com",  "Atencion Al Cliente"),
            Map.entry("miguel.torres@elecsur.com",    "Departamento Tecnico"),
            Map.entry("roberto.castillo@elecsur.com", "Departamento Tecnico"),
            Map.entry("fernando.vega@elecsur.com",    "Departamento Tecnico"),
            Map.entry("ana.rodriguez@elecsur.com",    "Departamento Legal"),
            Map.entry("diego.morales@elecsur.com",    "Departamento Legal"),
            Map.entry("gabriela.herrera@elecsur.com", "Facturacion"),
            Map.entry("luis.paredes@elecsur.com",     "Facturacion"),
            Map.entry("jorge.espinoza@elecsur.com",   "Operaciones De Campo")
        );

        int updated = 0;
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            Optional<User> userOpt = userRepository.findByEmail(entry.getKey());
            Optional<Department> deptOpt = departmentRepository.findByName(entry.getValue());
            if (userOpt.isPresent() && deptOpt.isPresent()) {
                User user = userOpt.get();
                if (!deptOpt.get().id().equals(user.getDepartmentId())) {
                    user.setDepartmentId(deptOpt.get().id());
                    userRepository.save(user);
                    updated++;
                }
            }
        }
        if (updated > 0) System.out.println("[SEED] Asignaciones de departamento actualizadas: " + updated);
    }
}
