/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react';
import i18n from 'i18next';
import { I18nextProvider, initReactI18next } from 'react-i18next';
import { afterEach, describe, expect, it } from 'vitest';
import {
  EnrollmentIdentityFormFields,
  EnrollmentIdentityReadout,
  EnrollmentUserIdCell,
} from '@/components/feature/enrollment-identity-fields';
import enEnrollments from '@/locales/en/enrollments.json';
import frEnrollments from '@/locales/fr/enrollments.json';
import {
  ENROLLMENT_NAME_MAX_LENGTH,
  ENROLLMENT_USER_ID_MAX_LENGTH,
  enrollmentIntegrationKind,
  enrollmentNameField,
  enrollmentUserIdField,
  enrollmentUserIdForCreate,
  isEnrollmentUserIdUnset,
  showsEmptyEnrollmentUserIdWarning,
} from '@/lib/enrollment-identity';

const EN = {
  label: 'User ID',
  nameHelp: 'Device label shown on the phone.',
  optional: '(optional)',
  userIdHelp: 'Username in the protected application. Integrations that find the user by username rely on it.',
  userIdHelpSystem: 'Optional reference for this administrator enrollment.',
  warning: 'Without a User ID, an integration that finds the user by username cannot sign this user in.',
  notSet: 'Not set',
  namePlaceholder: "e.g. Marie's iPhone 15",
};

const FR = {
  label: 'Identifiant utilisateur',
  nameHelp: "Libellé de l'appareil, affiché sur le téléphone.",
  optional: '(optionnel)',
  userIdHelp:
    "Nom d'usager dans l'application protégée. Les intégrations qui retrouvent l'utilisateur par son nom d'usager s'en servent.",
  userIdHelpSystem: 'Référence facultative pour cet enrôlement administrateur.',
  warning:
    "Sans identifiant utilisateur, une intégration qui retrouve l'utilisateur par son nom d'usager ne pourra pas le connecter.",
  notSet: 'Non défini',
  namePlaceholder: 'ex. iPhone 15 de Marie',
};

const FORBIDDEN_LABELS = ['User Identifier', 'User identifier', 'ID utilisateur'];

function i18nFor(lng: 'en' | 'fr') {
  const instance = i18n.createInstance();
  void instance.use(initReactI18next).init({
    lng,
    fallbackLng: 'en',
    resources: {
      en: { enrollments: enEnrollments },
      fr: { enrollments: frEnrollments },
    },
  });
  return instance;
}

function follows(earlier: HTMLElement, later: HTMLElement) {
  return (earlier.compareDocumentPosition(later) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;
}

describe('enrollment identity copy', () => {
  it('uses one User ID label in English and French and drops the old variants', () => {
    const localeFiles = import.meta.glob('../locales/**/*.json', {
      eager: true,
      query: '?raw',
      import: 'default',
    }) as Record<string, string>;
    const blob = Object.values(localeFiles).join('\n');
    expect(Object.keys(localeFiles).length).toBeGreaterThan(0);
    for (const forbidden of FORBIDDEN_LABELS) {
      expect(blob).not.toContain(forbidden);
    }
    expect(blob).not.toContain('Internal reference (HR ID');
    expect(blob).not.toContain('Référence interne (ID RH');
    expect(blob).not.toContain('Demo Login, PAM');
    expect(enEnrollments.identity).toEqual({
      userId: EN.label,
      optional: EN.optional,
      nameHelp: EN.nameHelp,
      userIdHelp: EN.userIdHelp,
      userIdHelpSystem: EN.userIdHelpSystem,
      userIdEmptyWarning: EN.warning,
      userIdNotSet: EN.notSet,
    });
    expect(enEnrollments.create.namePlaceholder).toBe(EN.namePlaceholder);
    expect(frEnrollments.identity).toEqual({
      userId: FR.label,
      optional: FR.optional,
      nameHelp: FR.nameHelp,
      userIdHelp: FR.userIdHelp,
      userIdHelpSystem: FR.userIdHelpSystem,
      userIdEmptyWarning: FR.warning,
      userIdNotSet: FR.notSet,
    });
    expect(frEnrollments.create.namePlaceholder).toBe(FR.namePlaceholder);
  });
});

describe('enrollment identity rules', () => {
  it('treats a blank User ID as unset and omits it on create', () => {
    expect(isEnrollmentUserIdUnset(null)).toBe(true);
    expect(isEnrollmentUserIdUnset('   ')).toBe(true);
    expect(isEnrollmentUserIdUnset('alice')).toBe(false);
    expect(enrollmentUserIdForCreate('  alice  ')).toBe('alice');
    expect(enrollmentUserIdForCreate('   ')).toBeUndefined();
  });

  it('warns only for an empty User ID once an application integration is known', () => {
    expect(enrollmentIntegrationKind(undefined)).toBe('none');
    expect(enrollmentIntegrationKind({ isSystemIntegration: true })).toBe('system');
    expect(enrollmentIntegrationKind({ isSystemIntegration: false })).toBe('application');
    expect(enrollmentIntegrationKind({})).toBe('application');

    expect(showsEmptyEnrollmentUserIdWarning('', 'application')).toBe(true);
    expect(showsEmptyEnrollmentUserIdWarning('  ', 'application')).toBe(true);
    expect(showsEmptyEnrollmentUserIdWarning('alice', 'application')).toBe(false);
    expect(showsEmptyEnrollmentUserIdWarning('', 'none')).toBe(false);
    expect(showsEmptyEnrollmentUserIdWarning('', 'system')).toBe(false);
  });

  it('matches the database column widths', () => {
    expect(ENROLLMENT_NAME_MAX_LENGTH).toBe(64);
    expect(ENROLLMENT_USER_ID_MAX_LENGTH).toBe(255);

    const name = enrollmentNameField('required', 'name-too-long');
    expect(name.safeParse('a'.repeat(64)).success).toBe(true);
    const tooLongName = name.safeParse('a'.repeat(65));
    expect(tooLongName.success).toBe(false);
    if (!tooLongName.success) {
      expect(tooLongName.error.issues[0]?.message).toBe('name-too-long');
    }
    expect(name.safeParse('').success).toBe(false);

    const userId = enrollmentUserIdField('user-id-too-long');
    expect(userId.safeParse('').success).toBe(true);
    expect(userId.safeParse('a'.repeat(255)).success).toBe(true);
    const tooLongUserId = userId.safeParse('a'.repeat(256));
    expect(tooLongUserId.success).toBe(false);
    if (!tooLongUserId.success) {
      expect(tooLongUserId.error.issues[0]?.message).toBe('user-id-too-long');
    }
  });
});

describe('enrollment identity fields', () => {
  afterEach(() => {
    cleanup();
  });

  it.each([
    ['en', EN],
    ['fr', FR],
  ] as const)('orders the fields and shows %s help text', (lng, copy) => {
    render(
      <I18nextProvider i18n={i18nFor(lng)}>
        <EnrollmentIdentityFormFields
          nameLabel={lng === 'en' ? 'Enrollment Name *' : "Nom de l'enrôlement *"}
          name={{ id: 'enr-name', value: 'Phone', onChange: () => undefined }}
          userId={{ id: 'enr-uid', value: '', onChange: () => undefined }}
          userIdentifier=""
          integrationKind="application"
        />
      </I18nextProvider>,
    );

    expect(follows(screen.getByTestId('enrollment-field-name'), screen.getByTestId('enrollment-field-userId'))).toBe(
      true,
    );
    expect(screen.getByTestId('enrollment-name-help').textContent).toBe(copy.nameHelp);
    expect(screen.getByTestId('enrollment-user-id-help').textContent).toBe(copy.userIdHelp);
    expect(screen.getByTestId('enrollment-field-userId').textContent).toContain(`${copy.label} ${copy.optional}`);
    expect(screen.getByRole('alert').textContent).toContain(copy.warning);
    expect(screen.getByLabelText(copy.label, { exact: false })).toHaveProperty('maxLength', ENROLLMENT_USER_ID_MAX_LENGTH);
    expect(screen.getByLabelText(lng === 'en' ? 'Enrollment Name *' : "Nom de l'enrôlement *")).toHaveProperty(
      'maxLength',
      ENROLLMENT_NAME_MAX_LENGTH,
    );
    expect(document.querySelector('.text-error')).toBeNull();
  });

  it('hides the warning before an integration is chosen, on the system integration, and when a User ID is set', () => {
    const { rerender } = render(
      <I18nextProvider i18n={i18nFor('en')}>
        <EnrollmentIdentityFormFields
          nameLabel="Enrollment name"
          name={{ id: 'edit-name', value: 'Phone', onChange: () => undefined }}
          userId={{ id: 'edit-user-id', value: '', onChange: () => undefined }}
          userIdentifier=""
          integrationKind="none"
        />
      </I18nextProvider>,
    );
    expect(screen.queryByTestId('enrollment-user-id-warning')).toBeNull();
    expect(screen.getByTestId('enrollment-user-id-help').textContent).toBe(EN.userIdHelp);

    rerender(
      <I18nextProvider i18n={i18nFor('en')}>
        <EnrollmentIdentityFormFields
          nameLabel="Enrollment name"
          name={{ id: 'edit-name', value: 'Phone', onChange: () => undefined }}
          userId={{ id: 'edit-user-id', value: '', onChange: () => undefined }}
          userIdentifier=""
          integrationKind="system"
        />
      </I18nextProvider>,
    );
    expect(screen.queryByTestId('enrollment-user-id-warning')).toBeNull();
    expect(screen.getByTestId('enrollment-user-id-help').textContent).toBe(EN.userIdHelpSystem);

    rerender(
      <I18nextProvider i18n={i18nFor('en')}>
        <EnrollmentIdentityFormFields
          nameLabel="Enrollment name"
          name={{ id: 'edit-name', value: 'Phone', onChange: () => undefined }}
          userId={{ id: 'edit-user-id', value: 'alice', onChange: () => undefined }}
          userIdentifier="alice"
          integrationKind="application"
        />
      </I18nextProvider>,
    );
    expect(screen.queryByTestId('enrollment-user-id-warning')).toBeNull();
  });

  it('shows Not set on the record and warns only for an application integration', () => {
    const { rerender } = render(
      <I18nextProvider i18n={i18nFor('en')}>
        <dl>
          <EnrollmentIdentityReadout
            nameLabel="Name"
            name="iPhone de Marie"
            userIdentifier="  "
            integrationKind="application"
          />
        </dl>
      </I18nextProvider>,
    );
    expect(follows(screen.getByTestId('enrollment-field-name'), screen.getByTestId('enrollment-field-userId'))).toBe(
      true,
    );
    expect(screen.getByTestId('enrollment-user-id-value').textContent).toBe(EN.notSet);
    expect(screen.getByTestId('enrollment-user-id-warning').textContent).toContain(EN.warning);

    rerender(
      <I18nextProvider i18n={i18nFor('fr')}>
        <dl>
          <EnrollmentIdentityReadout
            nameLabel="Nom"
            name="iPhone de Marie"
            userIdentifier={null}
            integrationKind="system"
          />
        </dl>
      </I18nextProvider>,
    );
    expect(screen.getByTestId('enrollment-user-id-value').textContent).toBe(FR.notSet);
    expect(screen.queryByTestId('enrollment-user-id-warning')).toBeNull();
    expect(screen.getByTestId('enrollment-field-userId').textContent).toContain(FR.label);
  });

  it('shows Not set in the list cell and the value when it is present', () => {
    const { rerender } = render(
      <I18nextProvider i18n={i18nFor('fr')}>
        <EnrollmentUserIdCell value={null} />
      </I18nextProvider>,
    );
    expect(screen.getByTestId('enrollment-user-id-cell').textContent).toBe(FR.notSet);

    rerender(
      <I18nextProvider i18n={i18nFor('en')}>
        <EnrollmentUserIdCell value="  alice  " />
      </I18nextProvider>,
    );
    expect(screen.getByTestId('enrollment-user-id-cell').textContent).toBe('alice');
  });
});
