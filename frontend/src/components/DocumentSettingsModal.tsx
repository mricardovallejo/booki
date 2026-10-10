import { useEffect, useState } from 'react';
import { detectPagination, updatePaginationOffset } from '../api/documents';
import { getErrorMessage } from '../lib/errors';
import Button from './ui/Button';
import { Field } from './ui/FormField';
import type { Document } from '../types';

interface Props {
  document: Document | null;
  onClose: () => void;
  /** Called with the updated Document after a successful detect/save, so the caller can refresh its list. */
  onUpdated?: (doc: Document) => void;
}

const STATUS_LABEL: Record<Document['paginationStatus'], string> = {
  unknown: 'Not set — BooKI will use the PDF\'s own page numbers.',
  manual: 'Set by hand.',
  auto_detected: 'Detected automatically by BooKI.',
  detection_failed: "BooKI couldn't find printed page numbers in this book — using the PDF's own."
};

/**
 * Lets a reader tell BooKI where a book's own printed page numbering starts
 * (or have BooKI figure it out), so "page 10" can mean the printed page 10
 * instead of the 10th PDF page. See ADR-030.
 */
export default function DocumentSettingsModal({ document, onClose, onUpdated }: Props) {
  const [offsetInput, setOffsetInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (document) {
      // paginationOffset is the physical page of printed page 1; show that directly.
      setOffsetInput(document.paginationOffset != null ? String(document.paginationOffset + 1) : '');
      setError(null);
    }
  }, [document]);

  if (!document) return null;

  const detect = async () => {
    if (document.paginationStatus === 'manual') {
      const proceed = window.confirm(
        'This book has a manually entered page number — detect automatically and replace it?'
      );
      if (!proceed) return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await detectPagination(document.id);
      onUpdated?.(updated);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not detect this book\'s page numbers.'));
    } finally {
      setBusy(false);
    }
  };

  const saveManual = async () => {
    const trimmed = offsetInput.trim();
    const physicalPageOfPageOne = trimmed === '' ? null : Number(trimmed);
    if (physicalPageOfPageOne != null && (!Number.isInteger(physicalPageOfPageOne) || physicalPageOfPageOne < 1)) {
      setError('Enter the PDF page number where this book\'s printed page "1" appears.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await updatePaginationOffset(
        document.id,
        physicalPageOfPageOne == null ? null : physicalPageOfPageOne - 1
      );
      onUpdated?.(updated);
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save this page number.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl bg-booki-surface p-6 shadow-2xl">
        <h2 className="text-2xl font-bold text-white">Book settings</h2>
        <p className="mt-1 text-sm text-booki-muted">{document.title}</p>

        <div className="mt-6 space-y-4">
          <Field label="Printed page numbers">
            <p className="text-xs text-white/60">{STATUS_LABEL[document.paginationStatus]}</p>
            <Button type="button" variant="secondary" onClick={detect} disabled={busy} className="mt-2 w-full">
              {busy ? 'Working…' : 'Detect automatically'}
            </Button>
          </Field>

          <Field label='Or enter it by hand — the PDF page that shows printed page "1"'>
            <input
              type="number"
              min={1}
              max={document.pageCount}
              value={offsetInput}
              onChange={(e) => setOffsetInput(e.target.value)}
              placeholder={`e.g. 7 (of ${document.pageCount})`}
              className="w-full rounded-lg bg-booki-card px-3 py-2 text-white outline-none ring-1 ring-white/10 focus:ring-booki-accent"
            />
            <p className="mt-1 text-xs text-white/50">Leave empty to clear it back to "not set".</p>
          </Field>

          {error && <p className="text-sm text-rose-400">{error}</p>}

          <div className="flex gap-3 pt-2">
            <Button type="button" variant="secondary" onClick={onClose} className="flex-1">
              Close
            </Button>
            <Button type="button" onClick={saveManual} disabled={busy} className="flex-1">
              Save
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
