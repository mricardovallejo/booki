import { useEffect, useState } from 'react';
import { useActivityRange } from '../context/ActivityRangeContext';
import { useAuth } from '../context/AuthContext';
import Button from './ui/Button';
import { Field, Input, TextArea } from './ui/FormField';
import type { GenerateSummaryRequest } from '../types';

const MAX_SUMMARY_PAGES = 50;

interface Props {
  open: boolean;
  onClose: () => void;
  onGenerate: (payload: GenerateSummaryRequest) => void;
}

export default function SummaryModal({ open, onClose, onGenerate }: Props) {
  const { range, pinned } = useActivityRange();
  const { user } = useAuth();
  const [prompt, setPrompt] = useState('');
  const [wantsPdf, setWantsPdf] = useState(false);
  const [includeCover, setIncludeCover] = useState(true);
  const [email, setEmail] = useState('');

  useEffect(() => {
    if (user?.effectiveReportEmail) setEmail((prev) => prev || user.effectiveReportEmail || '');
  }, [user]);

  if (!open) return null;

  const pageCount = range ? range.end - range.start + 1 : 0;
  // Refuse a degenerate, never-adjusted 1-page range (right when a session
  // starts) rather than silently summarizing a single page nobody chose.
  const rangeTooSmall = range != null && !pinned && pageCount < 2;
  const rangeTooLarge = pageCount > MAX_SUMMARY_PAGES;

  const onSubmit = () => {
    if (!range) return;
    onGenerate({
      startPage: range.start,
      endPage: range.end,
      prompt: prompt.trim() || undefined,
      includeCover,
      deliverAs: wantsPdf ? 'pdf' : 'chat',
      email: wantsPdf && email.trim() ? email.trim() : undefined
    });
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl bg-booki-surface p-6 shadow-2xl">
        <h2 className="text-xl font-bold text-white">Summarize these pages</h2>
        <p className="mt-1 text-sm text-booki-muted">
          A condensed summary that keeps every key idea, written in the chat so you can keep discussing it.
        </p>

        <div className="mt-5 space-y-4">
          <Field label="Anything to focus on? (optional)">
            <TextArea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="e.g. Focus on the characters' motivations, and keep it kid-friendly."
              rows={2}
            />
          </Field>

          {range && (
            <p className="text-xs text-white/50">
              Covers pages {range.start}–{range.end} ({pageCount} pages) — the activity range set above the PDF.
            </p>
          )}

          <label className="flex items-center gap-2 text-sm text-white/80">
            <input
              type="checkbox"
              checked={wantsPdf}
              onChange={(e) => setWantsPdf(e.target.checked)}
              className="rounded border-white/20 bg-booki-card"
            />
            Also create a PDF
          </label>

          {wantsPdf && (
            <>
              <label className="flex items-center gap-2 text-sm text-white/80">
                <input
                  type="checkbox"
                  checked={includeCover}
                  onChange={(e) => setIncludeCover(e.target.checked)}
                  className="rounded border-white/20 bg-booki-card"
                />
                Include the book's cover art
              </label>
              <Field label="Also email it (optional)">
                <Input
                  type="text"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@email.com; someone-else@email.com"
                />
              </Field>
            </>
          )}

          {rangeTooSmall && (
            <p className="text-xs text-amber-400">Adjust the page range above before generating a summary.</p>
          )}
          {rangeTooLarge && (
            <p className="text-xs text-amber-400">
              A summary can cover up to {MAX_SUMMARY_PAGES} pages at a time. Choose a smaller range above.
            </p>
          )}
        </div>

        <div className="mt-6 flex gap-3">
          <Button variant="secondary" onClick={onClose} className="flex-1">
            Cancel
          </Button>
          <Button onClick={onSubmit} disabled={!range || rangeTooSmall || rangeTooLarge} className="flex-1">
            Generate
          </Button>
        </div>
      </div>
    </div>
  );
}
