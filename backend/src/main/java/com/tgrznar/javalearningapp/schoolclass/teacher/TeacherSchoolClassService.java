package com.tgrznar.javalearningapp.schoolclass.teacher;

import com.tgrznar.javalearningapp.schoolclass.JoinCodeGenerator;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.schoolclass.dto.ClassResponse;
import com.tgrznar.javalearningapp.schoolclass.exception.SchoolClassNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A teacher's view of his own classes. Every operation is limited to classes led by the calling teacher;
 * a class of someone else is reported as not found, so the existence of other classes is not revealed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherSchoolClassService {

    private final SchoolClassRepository classRepository;
    private final JoinCodeGenerator joinCodeGenerator;

    /** Own classes (a null filter means "any"), ordered by name. */
    @Transactional(readOnly = true)
    public List<ClassResponse> list(Long teacherId, Boolean active) {
        return classRepository.search(null, teacherId, active).stream().map(ClassResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClassResponse get(Long id, Long teacherId) {
        return ClassResponse.from(findOwnOrThrow(id, teacherId));
    }

    /** Replaces the join code of an own class. The old code stops working immediately. */
    @Transactional
    public ClassResponse regenerateJoinCode(Long id, Long teacherId) {
        SchoolClass schoolClass = findOwnOrThrow(id, teacherId);
        schoolClass.setJoinCode(joinCodeGenerator.generateUnique());

        SchoolClass saved = classRepository.saveAndFlush(schoolClass);
        // The join code is a credential of the class, so it is never logged.
        log.info("Class join code regenerated: id={}, regeneratedByTeacherId={}", saved.getId(), teacherId);
        return ClassResponse.from(saved);
    }

    private SchoolClass findOwnOrThrow(Long id, Long teacherId) {
        return classRepository.findByIdAndTeacherId(id, teacherId)
                .orElseThrow(() -> new SchoolClassNotFoundException(id));
    }
}