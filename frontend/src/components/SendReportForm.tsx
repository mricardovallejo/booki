import { useState } from 'react';
import { useSessionReports } from '../hooks/useSessionReports';
import Button from './ui/Button';
import { Input } from './ui/FormField';

interface Props {
  sessionId: number;
  type: 'progress' | 'quiz';
  label: string;
}

export default function SendReportForm({ sessionId, type, label }: Props) {
  const { sending, lastSent, error, send, download } = useSessionReports(sessionId);
  const [email, setEmail] = useState('');

  const onSend = async () => {
    if (!email.trim()) return;
    await send(type, email.trim());
  };

  return (
    <div className="rounded-xl bg-booki-card p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-booki-muted">{label}</p>
      <p className="mt-1 text-[11px] text-white/40">
        Generates a real PDF. Email delivery is simulated in this demo — you can download it instead.
      </p>
      <div className="mt-2 flex gap-2">
        <Input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="parent@email.com"
          className="flex-1"
        />
        <Button size="sm" onClick={onSend} disabled={sending === type || !email.trim()}>
          {sending === type ? 'Sending…' : 'Send'}
        </Button>
      </div>
      {error && <p className="mt-2 text-xs text-rose-400">{error}</p>}
      {lastSent && lastSent.type === type && (
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-emerald-400">
          <span>Sent to {lastSent.email} (simulated)</span>
          <button onClick={() => download(lastSent)} className="font-bold text-white/80 hover:text-white">
            Download PDF
          </button>
        </div>
      )}
    </div>
  );
}
