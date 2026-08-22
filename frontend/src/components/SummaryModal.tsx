import { useState } from 'react';
import { useSummary } from '../hooks/useSummary';
import Button from './ui/Button';
import { Field, Input, TextArea } from './ui/FormField';
import type { SummaryDeliverAs } from '../types';

interface Props {
  sessionId: number;
  open: boolean;
  onClose: () => void;
  onChatGenerated: () => void;
}

export default function SummaryModal({ sessionId, open, onClose, onChatGenerated }: Props) {
  const { generating, error, generate } = useSummary(sessionId);
  const [lengthPages, setLengthPages] = useState(2);
  const [prompt, setPrompt] = useState('');
  const [includeCover, setIncludeCover] = useState(true);
  const [deliverAs, setDeliverAs] = useState<SummaryDeliverAs>('chat');
  const [email, setEmail] = useState('');
  const [done, setDone] = useState(false);

  if (!open) return null;

  const onGenerate = async () => {
    setDone(false);
    const result = await generate({
      lengthPages,
      prompt: prompt.trim() || undefined,
      includeCover,
      deliverAs,
      email: deliverAs === 'pdf' && email.trim() ? email.trim() : undefined
    });
    if (!result) return;
    if (result.deliveredAs === 'chat') {
      onChatGenerated();
      onClose();
    } else {
      setDone(true);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl bg-booki-surface p-6 shadow-2xl">
        <h2 className="text-xl font-bold text-white">Summarize this session</h2>
        <p className="mt-1 text-sm text-booki-muted">
          Combines the book's pages with our discussion so far, in your Master's and your own voice.
        </p>

        <div className="mt-5 space-y-4">
          <Field label="Describe the summary you want (optional)">
            <TextArea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="e.g. Focus on the characters' motivations, and keep it kid-friendly."
              rows={2}
            />
          </Field>

          <Field label={`Length: about ${lengthPages} page${lengthPages > 1 ? 's' : ''}`}>
            <input
              type="range"
              min={1}
              max={10}
              value={lengthPages}
              onChange={(e) => setLengthPages(Number(e.target.value))}
              className="w-full accent-booki-accent"
            />
          </Field>

          <label className="flex items-center gap-2 text-sm text-white/80">
            <input
              type="checkbox"
              checked={includeCover}
              onChange={(e) => setIncludeCover(e.target.checked)}
              className="rounded border-white/20 bg-booki-card"
            />
            Include the book's cover art
          </label>

          <Field label="Deliver as">
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setDeliverAs('chat')}
                className={`rounded-lg py-2 text-xs font-bold transition ${
                  deliverAs === 'chat'
                    ? 'bg-booki-accent text-white'
                    : 'bg-booki-card text-white/70 hover:bg-booki-card-hover'
                }`}
              >
                Message in chat
              </button>
              <button
                type="button"
                onClick={() => setDeliverAs('pdf')}
                className={`rounded-lg py-2 text-xs font-bold transition ${
                  deliverAs === 'pdf'
                    ? 'bg-booki-accent text-white'
                    : 'bg-booki-card text-white/70 hover:bg-booki-card-hover'
                }`}
              >
                Download PDF
              </button>
            </div>
          </Field>

          {deliverAs === 'pdf' && (
            <Field label="Also email it (optional)">
              <Input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@email.com"
              />
            </Field>
          )}

          {error && <p className="text-sm text-rose-400">{error}</p>}
          {done && deliverAs === 'pdf' && (
            <p className="text-sm text-emerald-400">
              PDF generated and downloaded{email.trim() ? ` · sent to ${email.trim()} (simulated)` : ''}.
            </p>
          )}
        </div>

        <div className="mt-6 flex gap-3">
          <Button variant="secondary" onClick={onClose} className="flex-1">
            {done ? 'Close' : 'Cancel'}
          </Button>
          <Button onClick={onGenerate} disabled={generating} className="flex-1">
            {generating ? 'Generating…' : 'Generate'}
          </Button>
        </div>
      </div>
    </div>
  );
}
