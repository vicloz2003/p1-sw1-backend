package com.ibpms.service.impl;

import com.ibpms.domain.Department;
import com.ibpms.dto.request.CreateDepartmentRequest;
import com.ibpms.exception.DepartmentAlreadyExistsException;
import com.ibpms.repository.DepartmentRepository;
import com.ibpms.service.api.DepartmentService;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    public List<Department> getAll() {
        return departmentRepository.findAll();
    }

    @Override
    public Department create(CreateDepartmentRequest request) {
        String canonical = normalize(request.name());

        if (departmentRepository.existsByName(canonical)) {
            throw new DepartmentAlreadyExistsException(canonical);
        }

        String description = request.description() != null
                ? request.description().trim()
                : null;

        return departmentRepository.save(new Department(null, canonical, description));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Normalization
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Canonical form used for both persistence and uniqueness checks:
     * <ol>
     *   <li>Trim + collapse internal whitespace</li>
     *   <li>Remove combining diacritical marks (accents) via NFD decomposition</li>
     *   <li>Title Case — each word capitalized, remainder lowercase</li>
     * </ol>
     *
     * Examples:
     * <pre>
     *   "  facturación  "  →  "Facturacion"
     *   "DEPARTAMENTO LEGAL" →  "Departamento Legal"
     *   "Atencion al Cliente" → "Atencion Al Cliente"
     * </pre>
     */
    public static String normalize(String raw) {
        if (raw == null) return null;

        // 1. Trim + collapse whitespace
        String s = raw.trim().replaceAll("\\s+", " ");

        // 2. Remove accents (NFD decomposition + strip combining marks)
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        String plain = nfd.replaceAll("\\p{InCombiningDiacriticalMarks}", "");

        // 3. Title Case
        if (plain.isEmpty()) return plain;
        String[] words = plain.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                  .append(w.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
