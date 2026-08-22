import { useState } from 'react';
import { generateSummaryAsChat, generateSummaryAsPdf } from '../api/sessions';
import { downloadReportFile } from '../api/reports';
import { getErrorMessage } from '../lib/errors';
import type { GenerateSummaryRequest, SentReport } from '../types';

function triggerDownload(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export function useSummary(sessionId: number) {
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastReport, setLastReport] = useState<SentReport | null>(null);

  const generate = async (payload: GenerateSummaryRequest) => {
    setGenerating(true);
    setError(null);
    setLastReport(null);
    try {
      if (payload.deliverAs === 'chat') {
        await generateSummaryAsChat(sessionId, payload);
        return { deliveredAs: 'chat' as const };
      }
      const report = await generateSummaryAsPdf(sessionId, payload);
      setLastReport(report);
      const blob = await downloadReportFile(report.id);
      triggerDownload(blob, `booki-summary-${report.id}.pdf`);
      return { deliveredAs: 'pdf' as const, report };
    } catch (err) {
      setError(getErrorMessage(err, 'Could not generate the summary.'));
      return null;
    } finally {
      setGenerating(false);
    }
  };

  return { generating, error, lastReport, generate };
}
