export interface RelatedDetailsUiState {
  labelKey: 'buttons.loading' | 'detail.moreDetails' | 'detail.relatedDetailsShown';
  tooltipKey: 'detail.moreDetailsTooltip' | 'detail.relatedDetailsShownTooltip';
  disableButton: boolean;
  showCheckIcon: boolean;
}

/**
 * Central UI state for the shared "related details" action.
 * The action is one-shot per panel: after related data is shown, the button
 * becomes a completed status indicator instead of a no-op trigger.
 */
export function getRelatedDetailsUiState({
  isExpanded,
  isLoading,
}: {
  isExpanded: boolean;
  isLoading: boolean;
}): RelatedDetailsUiState {
  if (isLoading) {
    return {
      labelKey: 'buttons.loading',
      tooltipKey: 'detail.moreDetailsTooltip',
      disableButton: true,
      showCheckIcon: false,
    };
  }

  if (isExpanded) {
    return {
      labelKey: 'detail.relatedDetailsShown',
      tooltipKey: 'detail.relatedDetailsShownTooltip',
      disableButton: true,
      showCheckIcon: true,
    };
  }

  return {
    labelKey: 'detail.moreDetails',
    tooltipKey: 'detail.moreDetailsTooltip',
    disableButton: false,
    showCheckIcon: false,
  };
}
