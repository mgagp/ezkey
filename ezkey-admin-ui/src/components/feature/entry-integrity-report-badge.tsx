import { AlertTriangle, CheckCircle, XCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import type { IntegrityReport } from '@/generated/admin-api/model';
import { resolveEntryIntegrityReportSummaryState } from '@/lib/integrity-investigation-session';

export function EntryIntegrityReportBadge({
  report,
}: {
  report: IntegrityReport;
}) {
  const { t } = useTranslation('audit-logs');
  const state = resolveEntryIntegrityReportSummaryState(report);

  if (state === 'intact') {
    return (
      <Tooltip content={t('integrity.entryReportSummary.intactTooltip')}>
        <Badge variant="success">
          <CheckCircle className="size-3 mr-1" />
          {t('integrity.reportIntact')}
        </Badge>
      </Tooltip>
    );
  }

  if (state === 'allExplained') {
    return (
      <Tooltip content={t('integrity.entryReportSummary.allExplainedTooltip')}>
        <Badge variant="warning">
          <AlertTriangle className="size-3 mr-1" />
          {t('integrity.entryReportSummary.allExplainedLabel')}
        </Badge>
      </Tooltip>
    );
  }

  return (
    <Tooltip content={t('integrity.entryReportSummary.violationTooltip')}>
      <Badge variant="error">
        <XCircle className="size-3 mr-1" />
        {t('integrity.reportViolation')}
      </Badge>
    </Tooltip>
  );
}
