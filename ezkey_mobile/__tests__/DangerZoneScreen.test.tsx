import React from 'react';
import {Alert, Text, TouchableOpacity} from 'react-native';
import renderer from 'react-test-renderer';
import {DangerZoneScreen} from '../app/screens/DangerZone';
import {useDeleteEnrollment, useEnrollments} from '../app/hooks/useEnrollments';

jest.mock('../app/hooks/useEnrollments', () => ({
  useEnrollments: jest.fn(),
  useDeleteEnrollment: jest.fn(),
}));

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({goBack: jest.fn()}),
}));

const mockUseEnrollments = jest.mocked(useEnrollments);
const mockUseDeleteEnrollment = jest.mocked(useDeleteEnrollment);

const baseDeleteMutation = {
  isPending: false,
  mutateAsync: jest.fn(),
};

describe('DangerZoneScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseDeleteEnrollment.mockReturnValue(baseDeleteMutation as never);
    mockUseEnrollments.mockReturnValue({
      data: [],
      isLoading: false,
      refetch: jest.fn(),
    } as never);
  });

  it('renders enriched enrollment cards and sorts favorites first', async () => {
    mockUseEnrollments.mockReturnValue({
      data: [
        {
          id: 'older-non-favorite',
          integrationId: 'integration-2',
          integrationName: 'CI Portal',
          tenantName: 'Tenant Blue',
          installation: {
            id: 'https://auth.acme.example',
            authUrl: 'https://auth.acme.example',
            name: 'Acme EU',
            host: 'auth.acme.example',
          },
          enrollmentProofToken: 'token-2',
          createdAt: '2026-03-10T08:00:00.000Z',
          lastActivityAt: '2026-03-12T08:00:00.000Z',
        },
        {
          id: 'favorite-recent',
          integrationId: 'integration-1',
          integrationName: 'Admin Console',
          tenantName: 'Tenant Red',
          installation: {
            id: 'https://login.red.example',
            authUrl: 'https://login.red.example',
            name: 'Ezkey installation',
            host: 'login.red.example',
          },
          enrollmentName: 'Pixel 7 Pro',
          favorited: true,
          enrollmentProofToken: 'token-1',
          createdAt: '2026-04-20T10:00:00.000Z',
          lastActivityAt: new Date().toISOString(),
        },
      ],
      isLoading: false,
      refetch: jest.fn(),
    } as never);

    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<DangerZoneScreen />);
    });

    const labels = tree!.root
      .findAllByType(TouchableOpacity)
      .map(node => node.props.accessibilityLabel)
      .filter((value): value is string => typeof value === 'string' && value.startsWith('Delete enrollment'));

    expect(labels).toEqual([
      'Delete enrollment Pixel 7 Pro',
      'Delete enrollment CI Portal',
    ]);

    const textContent = tree!.root.findAllByType(Text).map(node => node.props.children).flat().join(' ');

    expect(textContent).toContain('Pixel 7 Pro');
    expect(textContent).toContain('Favorite');
    expect(textContent).toContain('Admin Console');
    expect(textContent).toContain('Tenant Red · Ezkey installation · login.red.example');
    expect(textContent).toContain('Last active today');
  });

  it('uses a detailed confirmation message before deletion', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(jest.fn());

    mockUseEnrollments.mockReturnValue({
      data: [
        {
          id: 'favorite-recent',
          integrationId: 'integration-1',
          integrationName: 'Admin Console',
          tenantName: 'Tenant Red',
          installation: {
            id: 'https://login.red.example',
            authUrl: 'https://login.red.example',
            name: 'Ezkey installation',
            host: 'login.red.example',
          },
          enrollmentName: 'Pixel 7 Pro',
          favorited: true,
          enrollmentProofToken: 'token-1',
          createdAt: '2026-04-20T10:00:00.000Z',
          lastActivityAt: new Date().toISOString(),
        },
      ],
      isLoading: false,
      refetch: jest.fn(),
    } as never);

    let tree: renderer.ReactTestRenderer;
    await renderer.act(async () => {
      tree = renderer.create(<DangerZoneScreen />);
    });

    const deleteButton = tree!.root
      .findAllByType(TouchableOpacity)
      .find(node => node.props.accessibilityLabel === 'Delete enrollment Pixel 7 Pro');

    expect(deleteButton).toBeDefined();

    await renderer.act(async () => {
      deleteButton!.props.onPress();
    });

    expect(alertSpy).toHaveBeenCalledTimes(1);

    const [title, message, actions] = alertSpy.mock.calls[0];
    expect(title).toBe('Delete enrollment');
    expect(message).toContain('Remove "Pixel 7 Pro"?');
    expect(message).toContain('Integration: Admin Console');
    expect(message).toContain('Tenant: Tenant Red');
    expect(message).toContain('Installation: Ezkey installation');
    expect(message).toContain('Marked as favorite on this device.');
    expect(message).toContain('Used recently on this device.');
    expect(message).toContain('This will unlink this device and cannot be undone.');
    expect(actions).toEqual(
      expect.arrayContaining([
        expect.objectContaining({text: 'Cancel', style: 'cancel'}),
        expect.objectContaining({text: 'Delete', style: 'destructive'}),
      ]),
    );

    alertSpy.mockRestore();
  });
});