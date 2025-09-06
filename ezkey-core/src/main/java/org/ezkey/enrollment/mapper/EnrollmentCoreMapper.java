package org.ezkey.enrollment.mapper;

import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EnrollmentCoreMapper {

    EnrollmentCreateResponse toCreateResponse(Enrollment enrollment);

    @Mapping(source = "status", target = "enrollmentStatus")
    @Mapping(source = "active", target = "enrollmentActive")
    EnrollmentResponse toResponse(Enrollment enrollment);

}
