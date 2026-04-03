import { describe, expect, it } from 'vitest';
import { getRelatedDetailsUiState } from '@/lib/related-details-ui';

describe('getRelatedDetailsUiState', () => {
  it('returns the default CTA before expansion', () => {
    expect(getRelatedDetailsUiState({ isExpanded: false, isLoading: false })).toEqual({
      labelKey: 'detail.moreDetails',
      tooltipKey: 'detail.moreDetailsTooltip',
      disableButton: false,
      showCheckIcon: false,
    });
  });

  it('returns the loading state while related records are being fetched', () => {
    expect(getRelatedDetailsUiState({ isExpanded: true, isLoading: true })).toEqual({
      labelKey: 'buttons.loading',
      tooltipKey: 'detail.moreDetailsTooltip',
      disableButton: true,
      showCheckIcon: false,
    });
  });

  it('returns the completed state once related details are already shown', () => {
    expect(getRelatedDetailsUiState({ isExpanded: true, isLoading: false })).toEqual({
      labelKey: 'detail.relatedDetailsShown',
      tooltipKey: 'detail.relatedDetailsShownTooltip',
      disableButton: true,
      showCheckIcon: true,
    });
  });
});
