package org.ezkey.enrollment.mapper;

import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EnrollmentAuthMapper {

    EnrollmentBindResponseDto toEnrollmentBindResponseDto(EnrollmentBindResponse response);

    EnrollmentVerifyRequest toEnrollmentVerifyRequest(EnrollmentVerifyRequestDto req);

    EnrollmentVerifyResponseDto toEnrollmentVerifyResponseDto(EnrollmentVerifyResponse response);

}
