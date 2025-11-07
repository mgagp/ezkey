import {create} from 'zustand';
import {MockEnrollment} from '../services/api/mock/enrollments';

type EnrollmentStore = {
  selected?: MockEnrollment;
  setSelected: (enrollment: MockEnrollment) => void;
  clear: () => void;
};

export const useEnrollmentStore = create<EnrollmentStore>(set => ({
  selected: undefined,
  setSelected: enrollment => set({selected: enrollment}),
  clear: () => set({selected: undefined}),
}));
