package org.ezkey.enrollment.mapper;

import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EnrollmentCoreMapper {

    EnrollmentCreateResponse toCreateResponse(Enrollment enrollment);

}
