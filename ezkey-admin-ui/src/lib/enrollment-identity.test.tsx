/** @vitest-environment jsdom */

import { readFileSync } from 'node:fs';
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
  ENROLLMENT_IDENTITY_FIELD_ORDER,
  ENROLLMENT_NAME_MAX_LENGTH,
  ENROLLMENT_USER_ID_MAX_LENGTH,
  enrollmentNameField,
  enrollmentUserIdField,
  enrollmentUserIdForCreate,
  isEnrollmentUserIdUnset,
  placeEnrollmentUserIdAfterName,
  showsEmptyEnrollmentUserIdWarning,
} from '@/lib/enrollment-identity';

const EN = {
  label: 'User ID',
  nameHelp: 'Device label shown on the phone.',
  userIdHelp: 'Username in the protected application. The integration uses it to sign the user in.',
  warning: 'Without a User ID, Demo Login, PAM and other integrations cannot sign this user in.',
  notSet: 'Not set',
};

const FR = {
  label: 'Identifiant utilisateur',
  nameHelp: "Libellé de l'appareil, affiché sur le téléphone.",
  userIdHelp: "Nom d'usager dans l'application protégée. L'intégration s'en sert pour connecter l'utilisateur.",
  warning:
    'Sans identifiant utilisateur, Demo Login, PAM et les autres intégrations ne pourront pas connecter cet utilisateur.',
  notSet: 'Non défini',
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
    expect(enEnrollments.identity).toEqual({
      userId: EN.label,
      nameHelp: EN.nameHelp,
      userIdHelp: EN.userIdHelp,
      userIdEmptyWarning: EN.warning,
      userIdNotSet: EN.notSet,
    });
    expect(frEnrollments.identity).toEqual({
      userId: FR.label,
      nameHelp: FR.nameHelp,
      userIdHelp: FR.userIdHelp,
      userIdEmptyWarning: FR.warning,
      userIdNotSet: FR.notSet,
    });
  });
});

describe('enrollment identity rules', () => {
  it('keeps Name before User ID on every enrollment surface', () => {
    expect(ENROLLMENT_IDENTITY_FIELD_ORDER).toEqual(['name', 'userId']);
    const read = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), 'utf8');
    const createPage = read('../pages/enrollments.tsx');
    const detailPage = read('../pages/enrollment-detail.tsx');
    const integrationPage = read('../pages/integration-detail.tsx');
    expect(createPage).toContain('<EnrollmentIdentityFormFields');
    expect(createPage).toContain('placeEnrollmentUserIdAfterName');
    expect(detailPage).toContain('<EnrollmentIdentityFormFields');
    expect(detailPage).toContain('<EnrollmentIdentityReadout');
    expect(integrationPage).toContain('placeEnrollmentUserIdAfterName');
  });

  it('treats a blank User ID as unset and omits it on create', () => {
    expect(isEnrollmentUserIdUnset(null)).toBe(true);
    expect(isEnrollmentUserIdUnset('   ')).toBe(true);
    expect(isEnrollmentUserIdUnset('alice')).toBe(false);
    expect(enrollmentUserIdForCreate('  alice  ')).toBe('alice');
    expect(enrollmentUserIdForCreate('   ')).toBeUndefined();
  });

  it('warns only for an empty User ID on an application integration', () => {
    expect(showsEmptyEnrollmentUserIdWarning({ userIdentifier: '', isSystemIntegration: false })).toBe(true);
    expect(showsEmptyEnrollmentUserIdWarning({ userIdentifier: '  ', isSystemIntegration: false })).toBe(true);
    expect(showsEmptyEnrollmentUserIdWarning({ userIdentifier: 'alice', isSystemIntegration: false })).toBe(false);
    expect(showsEmptyEnrollmentUserIdWarning({ userIdentifier: '', isSystemIntegration: true })).toBe(false);
    expect(showsEmptyEnrollmentUserIdWarning({ userIdentifier: 'alice', isSystemIntegration: true })).toBe(false);
  });

  it('places the User ID column immediately after Name', () => {
    const enrollmentList = placeEnrollmentUserIdAfterName([
      { key: 'enrollmentId' },
      { key: 'enrollmentName' },
      { key: 'status' },
      { key: 'integrationName' },
      { key: 'userIdentifier' },
      { key: 'createdAt' },
    ]);
    expect(enrollmentList.map((column) => column.key)).toEqual([
      'enrollmentId',
      'enrollmentName',
      'userIdentifier',
      'status',
      'integrationName',
      'createdAt',
    ]);

    const integrationList = placeEnrollmentUserIdAfterName([
      { key: 'enrollmentId' },
      { key: 'enrollmentName' },
      { key: 'status' },
      { key: 'userIdentifier' },
      { key: 'createdAt' },
    ]);
    expect(integrationList.map((column) => column.key)).toEqual([
      'enrollmentId',
      'enrollmentName',
      'userIdentifier',
      'status',
      'createdAt',
    ]);
    expect(placeEnrollmentUserIdAfterName(integrationList)).toBe(integrationList);
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
          isSystemIntegration={false}
        />
      </I18nextProvider>,
    );

    expect(follows(screen.getByTestId('enrollment-field-name'), screen.getByTestId('enrollment-field-userId'))).toBe(
      true,
    );
    expect(screen.getByTestId('enrollment-name-help').textContent).toBe(copy.nameHelp);
    expect(screen.getByTestId('enrollment-user-id-help').textContent).toBe(copy.userIdHelp);
    expect(screen.getByText(copy.label).textContent).toBe(copy.label);
    expect(screen.getByRole('alert').textContent).toContain(copy.warning);
    expect(screen.getByLabelText(copy.label, { exact: false })).toHaveProperty('maxLength', ENROLLMENT_USER_ID_MAX_LENGTH);
    expect(screen.getByLabelText(lng === 'en' ? 'Enrollment Name *' : "Nom de l'enrôlement *")).toHaveProperty(
      'maxLength',
      ENROLLMENT_NAME_MAX_LENGTH,
    );
    expect(document.querySelector('.text-error')).toBeNull();
  });

  it('hides the warning on the system integration and when a User ID is set', () => {
    const { rerender } = render(
      <I18nextProvider i18n={i18nFor('en')}>
        <EnrollmentIdentityFormFields
          nameLabel="Enrollment name"
          name={{ id: 'edit-name', value: 'Phone', onChange: () => undefined }}
          userId={{ id: 'edit-user-id', value: '', onChange: () => undefined }}
          userIdentifier=""
          isSystemIntegration
        />
      </I18nextProvider>,
    );
    expect(screen.queryByTestId('enrollment-user-id-warning')).toBeNull();

    rerender(
      <I18nextProvider i18n={i18nFor('en')}>
        <EnrollmentIdentityFormFields
          nameLabel="Enrollment name"
          name={{ id: 'edit-name', value: 'Phone', onChange: () => undefined }}
          userId={{ id: 'edit-user-id', value: 'alice', onChange: () => undefined }}
          userIdentifier="alice"
          isSystemIntegration={false}
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
            name="Marie — iPhone"
            userIdentifier="  "
            isSystemIntegration={false}
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
            name="Marie — iPhone"
            userIdentifier={null}
            isSystemIntegration
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
