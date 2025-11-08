import {create} from 'zustand';

type EnrollmentStore = {
  selectedId?: string;
  setSelected: (enrollmentId: string) => void;
  clear: () => void;
};

export const useEnrollmentStore = create<EnrollmentStore>(set => ({
  selectedId: undefined,
  setSelected: enrollmentId => set({selectedId: enrollmentId}),
  clear: () => set({selectedId: undefined}),
}));
