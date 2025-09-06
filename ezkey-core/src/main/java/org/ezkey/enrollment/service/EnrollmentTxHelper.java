package org.ezkey.enrollment.service;

import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentTxHelper {

    private final EnrollmentRepository enrollmentRepository;

    public EnrollmentTxHelper(EnrollmentRepository enrollmentRepository){
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInvalidAndClear(Integer enrollmentId) {
        Enrollment e = enrollmentRepository.findById(enrollmentId).orElse(null);
        if (e == null){
            return;
        }
        if (e.getStatus() == EnrollmentStatus.VERIFIED){
            return;
        }
        e.setStatus(EnrollmentStatus.INVALID);
        e.setEnrollmentChallenge(null);
        e.setActive(false);
        enrollmentRepository.save(e);
    }
}