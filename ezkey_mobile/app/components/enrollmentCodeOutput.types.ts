/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentCodeOutput.types
 * Description: Platform-neutral contract for the enrollment QR camera output hook.
 *              Android implements it with ML Kit (enrollmentCodeOutput.android.ts);
 *              iOS with VisionCamera's native AVFoundation object output (enrollmentCodeOutput.ios.ts).
 * @since 2026
 */

import type {CameraOutput} from 'react-native-vision-camera';

/**
 * Builds the VisionCamera output that decodes enrollment codes.
 *
 * @param onCodeValue Invoked with each non-empty decoded code value.
 * @returns The output to pass to `<Camera outputs={[...]} />`.
 */
export type UseEnrollmentCodeOutput = (onCodeValue: (value: string) => void) => CameraOutput;
