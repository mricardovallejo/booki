import { API_BASE, API_BASE_IS_SECURE, ENDPOINTS } from '../config/endpoints';
import { getAuthToken } from '../lib/authToken';
import { handleExpiredSession } from './client';
import type { GenerateSummaryRequest, SentReport } from '../types';

export interface SummaryResult {
  messageId: number;
  /** The model didn't finish; the saved text also ends with a notice saying so. */
  cutOff: boolean;
  report: SentReport | null;
  reportError: string | null;
}

interface StreamEvent {
  event: string;
  data: Record<string, unknown>;
}

function parseFrame(frame: string): StreamEvent | null {
  let event = 'message';
  let data = '';
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) data += line.slice(5).trim();
  }
  return data ? { event, data: JSON.parse(data) } : null;
}

/**
 * Streams a summary. Uses fetch because Axios can't read a response as it arrives
 * in the browser.
 *
 * @returns the outcome, or null if the stream ended without one: something
 * outside BooKI cut it (a timeout, a lost connection), so the text is incomplete
 * @throws Error with a message for the reader, if nothing could be generated
 */
export async function streamSummary(
  sessionId: number,
  payload: GenerateSummaryRequest,
  onDelta: (text: string) => void,
  onFigures: () => void
): Promise<SummaryResult | null> {
  const token = getAuthToken();
  const response = await fetch(`${API_BASE}${ENDPOINTS.sessions.summaryStream(sessionId)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token && API_BASE_IS_SECURE ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload)
  });

  if (response.status === 401) {
    handleExpiredSession();
    throw new Error('Your session has expired.');
  }
  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error || 'Could not generate the summary.');
  }

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) return null;
    buffer += value;

    let boundary = buffer.indexOf('\n\n');
    while (boundary !== -1) {
      const parsed = parseFrame(buffer.slice(0, boundary));
      buffer = buffer.slice(boundary + 2);
      boundary = buffer.indexOf('\n\n');

      if (parsed?.event === 'delta') onDelta(parsed.data.text as string);
      if (parsed?.event === 'figures') onFigures();
      if (parsed?.event === 'done') return parsed.data as unknown as SummaryResult;
      if (parsed?.event === 'error') throw new Error(parsed.data.error as string);
    }
  }
}
