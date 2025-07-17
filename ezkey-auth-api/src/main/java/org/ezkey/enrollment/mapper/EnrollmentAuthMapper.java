package org.ezkey.enrollment.mapper;

import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentConfirmRequest;
import org.ezkey.enrollment.domain.EnrollmentConfirmResponse;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentConfirmRequestDto;
import org.ezkey.enrollment.dto.EnrollmentConfirmResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EnrollmentAuthMapper {

    EnrollmentBindResponseDto toEnrollmentBindResponseDto(EnrollmentBindResponse response);

    EnrollmentConfirmRequest toEnrollmentConfirmRequest(EnrollmentConfirmRequestDto req);

    EnrollmentConfirmResponseDto toEnrollmentConfirmResponseDto(EnrollmentConfirmResponse response);

}
