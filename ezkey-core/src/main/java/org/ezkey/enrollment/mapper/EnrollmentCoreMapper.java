package org.ezkey.enrollment.mapper;

import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EnrollmentCoreMapper {

    EnrollmentCreateResponse toCreateResponse(Enrollment enrollment);

}
