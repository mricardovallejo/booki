import { useState } from 'react';
import { sendProgressReport, sendQuizReport } from '../api/sessions';
import { downloadReportFile } from '../api/reports';
import type { SentReport } from '../types';

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

export function useSessionReports(sessionId: number) {
  const [sending, setSending] = useState<'progress' | 'quiz' | null>(null);
  const [lastSent, setLastSent] = useState<SentReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  const send = async (type: 'progress' | 'quiz', email: string) => {
    setSending(type);
    setError(null);
    try {
      const report =
        type === 'progress' ? await sendProgressReport(sessionId, email) : await sendQuizReport(sessionId, email);
      setLastSent(report);
      return report;
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { error?: string } } })?.response?.data?.error ||
        'Could not send the report.';
      setError(message);
      return null;
    } finally {
      setSending(null);
    }
  };

  const download = async (report: SentReport) => {
    const blob = await downloadReportFile(report.id);
    triggerDownload(blob, `booki-${report.type}-report-${report.id}.pdf`);
  };

  return { sending, lastSent, error, send, download };
}
