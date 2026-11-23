import { Fragment, type ChangeEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { UseFormRegisterReturn } from 'react-hook-form';
import { Alert } from '@/components/ui/alert';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  ENROLLMENT_IDENTITY_FIELD_ORDER,
  ENROLLMENT_NAME_MAX_LENGTH,
  ENROLLMENT_USER_ID_MAX_LENGTH,
  isEnrollmentUserIdUnset,
  showsEmptyEnrollmentUserIdWarning,
  type EnrollmentIdentityField,
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
 * Name, then User ID, on the create and edit forms. Help text explains the two
 * fields. An empty User ID warns on application integrations and still submits.
 */
export function EnrollmentIdentityFormFields({
  nameLabel,
  name,
  userId,
  userIdentifier,
  isSystemIntegration = false,
  userIdOptional,
}: {
  nameLabel: string;
  name: IdentityInput;
  userId: IdentityInput;
  userIdentifier: string;
  isSystemIntegration?: boolean;
  userIdOptional?: string;
}) {
  const { t } = useTranslation('enrollments');
  const showWarning = showsEmptyEnrollmentUserIdWarning({ userIdentifier, isSystemIntegration });
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

  const fields: Record<EnrollmentIdentityField, ReactNode> = {
    name: (
      <>
        <Label htmlFor={name.id}>{nameLabel}</Label>
        <p
          id={`${name.id}-help`}
          data-testid="enrollment-name-help"
          className="text-xs text-fg-muted leading-relaxed"
        >
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
      </>
    ),
    userId: (
      <>
        <Label htmlFor={userId.id}>
          {t('identity.userId')}
          {userIdOptional ? (
            <span className="text-fg-muted font-normal"> {userIdOptional}</span>
          ) : null}
        </Label>
        <p
          id={`${userId.id}-help`}
          data-testid="enrollment-user-id-help"
          className="text-xs text-fg-muted leading-relaxed"
        >
          {t('identity.userIdHelp')}
        </p>
        <Input
          {...inputBinding(userId)}
          id={userId.id}
          className={userId.className}
          autoComplete="off"
          aria-describedby={showWarning ? `${userId.id}-help ${userId.id}-warning` : `${userId.id}-help`}
          error={userIdError}
          maxLength={ENROLLMENT_USER_ID_MAX_LENGTH}
        />
        {showWarning && (
          <Alert variant="warning" id={`${userId.id}-warning`} data-testid="enrollment-user-id-warning">
            {t('identity.userIdEmptyWarning')}
          </Alert>
        )}
      </>
    ),
  };

  return (
    <div data-testid="enrollment-identity-fields" className="space-y-4">
      {ENROLLMENT_IDENTITY_FIELD_ORDER.map((key) => (
        <div key={key} data-testid={`enrollment-field-${key}`} className="space-y-1">
          {fields[key]}
        </div>
      ))}
    </div>
  );
}

/** Name, then User ID, on the enrollment record. An empty User ID reads "Not set". */
export function EnrollmentIdentityReadout({
  nameLabel,
  name,
  userIdentifier,
  isSystemIntegration = false,
}: {
  nameLabel: string;
  name: string;
  userIdentifier: string | null | undefined;
  isSystemIntegration?: boolean;
}) {
  const { t } = useTranslation('enrollments');
  const unset = isEnrollmentUserIdUnset(userIdentifier);
  const showWarning = showsEmptyEnrollmentUserIdWarning({ userIdentifier, isSystemIntegration });

  const fields: Record<EnrollmentIdentityField, ReactNode> = {
    name: (
      <IdentityRow label={nameLabel} testId="enrollment-field-name">
        <span className="font-medium">{name}</span>
        <p data-testid="enrollment-name-help" className="text-xs text-fg-muted mt-0.5 leading-relaxed">
          {t('identity.nameHelp')}
        </p>
      </IdentityRow>
    ),
    userId: (
      <IdentityRow label={t('identity.userId')} testId="enrollment-field-userId">
        <span className="text-fg-muted break-all" data-testid="enrollment-user-id-value">
          {unset ? t('identity.userIdNotSet') : (userIdentifier ?? '').trim()}
        </span>
        <p data-testid="enrollment-user-id-help" className="text-xs text-fg-muted mt-0.5 leading-relaxed">
          {t('identity.userIdHelp')}
        </p>
        {showWarning && (
          <Alert variant="warning" data-testid="enrollment-user-id-warning" className="mt-2">
            {t('identity.userIdEmptyWarning')}
          </Alert>
        )}
      </IdentityRow>
    ),
  };

  return (
    <>
      {ENROLLMENT_IDENTITY_FIELD_ORDER.map((key) => (
        <Fragment key={key}>{fields[key]}</Fragment>
      ))}
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
