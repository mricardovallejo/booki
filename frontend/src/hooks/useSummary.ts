import { useCallback, useState } from 'react';
import { streamSummary } from '../api/summaryStream';
import { downloadReportFile } from '../api/reports';
import { getErrorMessage } from '../lib/errors';
import { triggerDownload } from './useSessionReports';
import type { GenerateSummaryRequest, SentReport } from '../types';

/**
 * Writes a summary into the chat as it arrives. The backend saves it as a chat
 * message, so once it is done it comes back through {@code onSaved} like any other.
 */
export function useSummary(sessionId: number, onSaved: () => Promise<unknown> | void) {
  const [generating, setGenerating] = useState(false);
  /** The text so far; stays visible after an interruption, because nothing else holds it. */
  const [streamedText, setStreamedText] = useState<string | null>(null);
  const [addingFigures, setAddingFigures] = useState(false);
  const [cutOff, setCutOff] = useState(false);
  const [interrupted, setInterrupted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<SentReport | null>(null);

  const downloadReport = useCallback(async (target: SentReport) => {
    try {
      const blob = await downloadReportFile(target.id);
      triggerDownload(blob, `booki-summary-${target.id}.pdf`);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not download the PDF.'));
    }
  }, []);

  const generate = useCallback(
    async (payload: GenerateSummaryRequest) => {
      setGenerating(true);
      setStreamedText('');
      setAddingFigures(false);
      setCutOff(false);
      setInterrupted(false);
      setError(null);
      setReport(null);
      try {
        const result = await streamSummary(
          sessionId,
          payload,
          (text) => setStreamedText((current) => (current ?? '') + text),
          () => setAddingFigures(true)
        );
        await onSaved();
        if (!result) {
          setInterrupted(true);
          return;
        }
        setStreamedText(null);
        setCutOff(result.cutOff);
        setError(result.reportError);
        if (result.report) {
          setReport(result.report);
          await downloadReport(result.report);
        }
      } catch (err) {
        setStreamedText(null);
        setError(err instanceof Error ? err.message : 'Could not generate the summary.');
      } finally {
        setGenerating(false);
      }
    },
    [sessionId, onSaved, downloadReport]
  );

  return { generating, streamedText, addingFigures, cutOff, interrupted, error, report, generate, downloadReport };
}
