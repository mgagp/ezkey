package org.ezkey.enrollment.mapper;

import org.ezkey.enrollment.domain.DevicePrivateKeyStorageTier;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EnrollmentCoreMapper {

  EnrollmentCreateResponse toCreateResponse(Enrollment enrollment);

  @Mapping(source = "status", target = "enrollmentStatus")
  @Mapping(source = "active", target = "enrollmentActive")
  EnrollmentResponse toResponse(Enrollment enrollment);

  /**
   * Converts an EnrollmentStatus enum to its string name representation.
   *
   * @param status the enrollment status enum
   * @return the string name of the status, or null if status is null
   */
  default String enrollmentStatusToString(EnrollmentStatus status) {
    if (status == null) {
      return null;
    }
    return status.name();
  }

  default String devicePrivateKeyStorageTierToString(DevicePrivateKeyStorageTier tier) {
    if (tier == null) {
      return null;
    }
    return tier.name();
  }
}
