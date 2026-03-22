/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: EnrollmentUpdateOutcome
 * Description: Result of a successful enrollment PATCH including structured audit payload.
 */

package org.ezkey.admin.service;

import org.ezkey.enrollment.domain.entity.Enrollment;

/**
 * Result of {@link EnrollmentUpdateService#updateEnrollment(int,
 * org.ezkey.admin.dto.request.EnrollmentUpdateRequestDto)}.
 *
 * <p>Carries the persisted enrollment and a JSON string for {@code event_details} on {@code
 * ENROLLMENT_UPDATED} (SOC 2 friendly: who changed what, previous vs new values).
 *
 * @param enrollment the updated enrollment entity
 * @param auditEventDetailsJson valid JSON for audit log {@code event_details}
 * @author Ezkey contributors
 * @since 2025
 */
public record EnrollmentUpdateOutcome(Enrollment enrollment, String auditEventDetailsJson) {}
