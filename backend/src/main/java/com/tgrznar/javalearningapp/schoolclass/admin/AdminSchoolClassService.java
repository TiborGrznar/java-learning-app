package com.tgrznar.javalearningapp.schoolclass.admin;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.school.exception.SchoolInactiveException;
import com.tgrznar.javalearningapp.school.exception.SchoolNotFoundException;
import com.tgrznar.javalearningapp.schoolclass.JoinCodeGenerator;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.schoolclass.dto.ClassResponse;
import com.tgrznar.javalearningapp.schoolclass.dto.CreateClassRequest;
import com.tgrznar.javalearningapp.schoolclass.dto.UpdateClassRequest;
import com.tgrznar.javalearningapp.schoolclass.exception.ClassNameAlreadyExistsException;
import com.tgrznar.javalearningapp.schoolclass.exception.InvalidClassDataException;
import com.tgrznar.javalearningapp.schoolclass.exception.SchoolClassNotFoundException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Class management by an administrator. Classes are never deleted, only deactivated. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSchoolClassService {

    private final SchoolClassRepository classRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final JoinCodeGenerator joinCodeGenerator;

    /** Creates a class in an active school, led by an active teacher of that school, with a new join code. */
    @Transactional
    public ClassResponse create(CreateClassRequest request, Long adminId) {
        School school = requireActiveSchool(request.schoolId());
        User teacher = loadTeacher(request.teacherId(), school);
        String name = request.name().trim();
        if (classRepository.existsBySchoolIdAndNameIgnoreCase(school.getId(), name)) {
            throw new ClassNameAlreadyExistsException();
        }

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchool(school);
        schoolClass.setTeacher(teacher);
        schoolClass.setName(name);
        schoolClass.setJoinCode(joinCodeGenerator.generateUnique());

        SchoolClass saved = saveUniqueName(schoolClass);
        // The join code is a credential of the class, so it is never logged.
        log.info("Class created: id={}, schoolId={}, createdByAdminId={}", saved.getId(), school.getId(), adminId);
        return ClassResponse.from(saved);
    }

    /** Classes matching the filters (a null filter means "any"), ordered by name. */
    @Transactional(readOnly = true)
    public List<ClassResponse> list(Long schoolId, Long teacherId, Boolean active) {
        return classRepository.search(schoolId, teacherId, active).stream().map(ClassResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClassResponse get(Long id) {
        return ClassResponse.from(findOrThrow(id));
    }

    /** Replaces the name and the teacher. The school of a class never changes. */
    @Transactional
    public ClassResponse update(Long id, UpdateClassRequest request, Long adminId) {
        SchoolClass schoolClass = findOrThrow(id);
        School school = schoolClass.getSchool();

        String name = request.name().trim();
        if (classRepository.existsBySchoolIdAndNameIgnoreCaseAndIdNot(school.getId(), name, id)) {
            throw new ClassNameAlreadyExistsException();
        }

        schoolClass.setTeacher(resolveTeacher(schoolClass, request.teacherId()));
        schoolClass.setName(name);

        SchoolClass saved = saveUniqueName(schoolClass);
        log.info("Class updated: id={}, updatedByAdminId={}", saved.getId(), adminId);
        return ClassResponse.from(saved);
    }

    /** Activates or deactivates a class. Deactivating makes its join code stop working, students stay in it. */
    @Transactional
    public ClassResponse setActive(Long id, boolean active, Long adminId) {
        SchoolClass schoolClass = findOrThrow(id);
        if (schoolClass.isActive() == active) {
            return ClassResponse.from(schoolClass);
        }

        if (active) {
            // An active class must never have a deactivated school or an unsuitable teacher.
            if (!schoolClass.getSchool().isActive()) {
                throw new SchoolInactiveException();
            }
            validateTeacher(schoolClass.getTeacher(), schoolClass.getSchool());
        }

        schoolClass.setActive(active);
        SchoolClass saved = classRepository.saveAndFlush(schoolClass);
        log.info("Class active flag changed: id={}, active={}, changedByAdminId={}", saved.getId(), active, adminId);
        return ClassResponse.from(saved);
    }

    /** Replaces the join code. The old code stops working immediately. */
    @Transactional
    public ClassResponse regenerateJoinCode(Long id, Long adminId) {
        SchoolClass schoolClass = findOrThrow(id);
        schoolClass.setJoinCode(joinCodeGenerator.generateUnique());

        SchoolClass saved = classRepository.saveAndFlush(schoolClass);
        log.info("Class join code regenerated: id={}, regeneratedByAdminId={}", saved.getId(), adminId);
        return ClassResponse.from(saved);
    }

    private SchoolClass findOrThrow(Long id) {
        return classRepository.findById(id).orElseThrow(() -> new SchoolClassNotFoundException(id));
    }

    private School requireActiveSchool(Long schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));
        if (!school.isActive()) {
            throw new SchoolInactiveException();
        }
        return school;
    }

    /** Keeps the current teacher when unchanged (even if deactivated since), otherwise requires a suitable one. */
    private User resolveTeacher(SchoolClass schoolClass, Long teacherId) {
        User current = schoolClass.getTeacher();
        if (current.getId().equals(teacherId)) {
            return current;
        }
        return loadTeacher(teacherId, schoolClass.getSchool());
    }

    private User loadTeacher(Long teacherId, School school) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new UserNotFoundException(teacherId));
        validateTeacher(teacher, school);
        return teacher;
    }

    private void validateTeacher(User teacher, School school) {
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new InvalidClassDataException("teacherId", "Vybraný používateľ nie je učiteľ");
        }
        if (!teacher.isActive()) {
            throw new InvalidClassDataException("teacherId", "Vybraný učiteľ je neaktívny");
        }
        if (teacher.getSchool() == null || !teacher.getSchool().getId().equals(school.getId())) {
            throw new InvalidClassDataException("teacherId", "Vybraný učiteľ patrí do inej školy");
        }
    }

    /**
     * Flushes now so a concurrent duplicate name is caught here, not at commit time. The join code cannot be
     * the cause in practice: the generator has just checked it among 32^8 possibilities.
     */
    private SchoolClass saveUniqueName(SchoolClass schoolClass) {
        try {
            return classRepository.saveAndFlush(schoolClass);
        } catch (DataIntegrityViolationException e) {
            throw new ClassNameAlreadyExistsException();
        }
    }
}