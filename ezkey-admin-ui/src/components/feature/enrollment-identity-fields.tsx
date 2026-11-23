import type { ChangeEvent, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { UseFormRegisterReturn } from 'react-hook-form';
import { Alert } from '@/components/ui/alert';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  ENROLLMENT_NAME_MAX_LENGTH,
  ENROLLMENT_USER_ID_MAX_LENGTH,
  isEnrollmentUserIdUnset,
  showsEmptyEnrollmentUserIdWarning,
  type EnrollmentIntegrationKind,
} from '@/lib/enrollment-identity';

type IdentityInput = {
  id: string;
  registration?: UseFormRegisterReturn;
  value?: string;
  onChange?: (value: string) => void;
  error?: string;
  className?: string;
  placeholder?: string;
};

/**
 * Name, then User ID, on the create and edit forms. An empty User ID warns
 * once an application integration is known, and still submits.
 */
export function EnrollmentIdentityFormFields({
  nameLabel,
  name,
  userId,
  userIdentifier,
  integrationKind,
}: {
  nameLabel: string;
  name: IdentityInput;
  userId: IdentityInput;
  userIdentifier: string;
  integrationKind: EnrollmentIntegrationKind;
}) {
  const { t } = useTranslation('enrollments');
  const showWarning = showsEmptyEnrollmentUserIdWarning(userIdentifier, integrationKind);
  const nameError =
    name.error
    ?? lengthError(name.value, ENROLLMENT_NAME_MAX_LENGTH, t('validation.nameMax', { n: ENROLLMENT_NAME_MAX_LENGTH }));
  const userIdError =
    userId.error
    ?? lengthError(
      userId.value,
      ENROLLMENT_USER_ID_MAX_LENGTH,
      t('validation.userIdMax', { n: ENROLLMENT_USER_ID_MAX_LENGTH }),
    );
  const userIdDescribedBy = showWarning ? `${userId.id}-help ${userId.id}-warning` : `${userId.id}-help`;

  return (
    <div data-testid="enrollment-identity-fields" className="space-y-4">
      <div data-testid="enrollment-field-name" className="space-y-1">
        <Label htmlFor={name.id}>{nameLabel}</Label>
        <p id={`${name.id}-help`} data-testid="enrollment-name-help" className="text-xs text-fg-muted leading-relaxed">
          {t('identity.nameHelp')}
        </p>
        <Input
          {...inputBinding(name)}
          id={name.id}
          className={name.className}
          placeholder={name.placeholder}
          autoComplete="off"
          aria-describedby={`${name.id}-help`}
          error={nameError}
          maxLength={ENROLLMENT_NAME_MAX_LENGTH}
        />
      </div>
      <div data-testid="enrollment-field-userId" className="space-y-1">
        <Label htmlFor={userId.id}>
          {t('identity.userId')}
          <span className="text-fg-muted font-normal"> {t('identity.optional')}</span>
        </Label>
        <p
          id={`${userId.id}-help`}
          data-testid="enrollment-user-id-help"
          className="text-xs text-fg-muted leading-relaxed"
        >
          {t(integrationKind === 'system' ? 'identity.userIdHelpSystem' : 'identity.userIdHelp')}
        </p>
        <Input
          {...inputBinding(userId)}
          id={userId.id}
          className={userId.className}
          autoComplete="off"
          aria-describedby={userIdDescribedBy}
          error={userIdError}
          maxLength={ENROLLMENT_USER_ID_MAX_LENGTH}
        />
        {showWarning && (
          <Alert variant="warning" id={`${userId.id}-warning`} data-testid="enrollment-user-id-warning">
            {t('identity.userIdEmptyWarning')}
          </Alert>
        )}
      </div>
    </div>
  );
}

/** Name, then User ID, on the enrollment record. An empty User ID reads "Not set". */
export function EnrollmentIdentityReadout({
  nameLabel,
  name,
  userIdentifier,
  integrationKind,
}: {
  nameLabel: string;
  name: string;
  userIdentifier: string | null | undefined;
  integrationKind: EnrollmentIntegrationKind;
}) {
  const { t } = useTranslation('enrollments');
  const unset = isEnrollmentUserIdUnset(userIdentifier);

  return (
    <>
      <IdentityRow label={nameLabel} testId="enrollment-field-name">
        <span className="font-medium">{name}</span>
      </IdentityRow>
      <IdentityRow label={t('identity.userId')} testId="enrollment-field-userId">
        <span className="text-fg-muted break-all" data-testid="enrollment-user-id-value">
          {unset ? t('identity.userIdNotSet') : (userIdentifier ?? '').trim()}
        </span>
        {showsEmptyEnrollmentUserIdWarning(userIdentifier, integrationKind) && (
          <Alert variant="warning" data-testid="enrollment-user-id-warning" className="mt-2">
            {t('identity.userIdEmptyWarning')}
          </Alert>
        )}
      </IdentityRow>
    </>
  );
}

/** List cell. An empty User ID reads "Not set" instead of an em dash. */
export function EnrollmentUserIdCell({ value }: { value: string | null | undefined }) {
  const { t } = useTranslation('enrollments');
  const unset = isEnrollmentUserIdUnset(value);
  const text = unset ? t('identity.userIdNotSet') : (value ?? '').trim();
  return (
    <span
      className="text-xs text-fg-muted max-w-[10rem] truncate block"
      title={unset ? undefined : text}
      data-testid="enrollment-user-id-cell"
    >
      {text}
    </span>
  );
}

function IdentityRow({
  label,
  testId,
  children,
}: {
  label: string;
  testId: string;
  children: ReactNode;
}) {
  return (
    <div className="flex gap-4" data-testid={testId}>
      <dt className="w-36 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">
        {label}
      </dt>
      <dd className="text-sm min-w-0">{children}</dd>
    </div>
  );
}

function inputBinding(field: IdentityInput) {
  if (field.registration) {
    return field.registration;
  }
  return {
    value: field.value ?? '',
    onChange: (event: ChangeEvent<HTMLInputElement>) => field.onChange?.(event.target.value),
  };
}

function lengthError(value: string | undefined, max: number, message: string): string | undefined {
  if (value != null && value.length > max) return message;
  return undefined;
}
