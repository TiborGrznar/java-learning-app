package com.tgrznar.javalearningapp.school;

import com.tgrznar.javalearningapp.school.dto.SchoolRequest;
import com.tgrznar.javalearningapp.school.dto.SchoolResponse;
import com.tgrznar.javalearningapp.school.exception.SchoolNameAlreadyExistsException;
import com.tgrznar.javalearningapp.school.exception.SchoolNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SchoolService {

    private static final Logger log = LoggerFactory.getLogger(SchoolService.class);

    private final SchoolRepository schoolRepository;

    public SchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @Transactional
    public SchoolResponse create(SchoolRequest request, Long adminId) {
        String name = request.name().strip();
        if (schoolRepository.existsByNameIgnoreCase(name)) {
            throw new SchoolNameAlreadyExistsException();
        }

        School school = new School();
        school.setName(name);
        school.setAddress(request.address().strip());

        School saved = saveAndFlushUnique(school);
        log.info("School created: id={}, createdByAdminId={}", saved.getId(), adminId);
        return SchoolResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SchoolResponse> list(Boolean active) {
        List<School> schools = active == null
                ? schoolRepository.findAllByOrderByNameAsc()
                : schoolRepository.findAllByActiveOrderByNameAsc(active);
        return schools.stream().map(SchoolResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SchoolResponse get(Long id) {
        return SchoolResponse.from(findOrThrow(id));
    }

    @Transactional
    public SchoolResponse update(Long id, SchoolRequest request, Long adminId) {
        School school = findOrThrow(id);
        String name = request.name().strip();
        if (schoolRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new SchoolNameAlreadyExistsException();
        }

        school.setName(name);
        school.setAddress(request.address().strip());

        School saved = saveAndFlushUnique(school);
        log.info("School updated: id={}, updatedByAdminId={}", saved.getId(), adminId);
        return SchoolResponse.from(saved);
    }

    @Transactional
    public SchoolResponse setActive(Long id, boolean active, Long adminId) {
        School school = findOrThrow(id);
        school.setActive(active);
        School saved = schoolRepository.save(school);
        log.info("School active flag changed: id={}, active={}, changedByAdminId={}",
                saved.getId(), active, adminId);
        return SchoolResponse.from(saved);
    }

    private School findOrThrow(Long id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));
    }

    // The UNIQUE index is the real guard against concurrent requests with the same name.
    private School saveAndFlushUnique(School school) {
        try {
            return schoolRepository.saveAndFlush(school);
        } catch (DataIntegrityViolationException e) {
            throw new SchoolNameAlreadyExistsException();
        }
    }
}