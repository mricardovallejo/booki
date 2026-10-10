import { useEffect, useState } from 'react';
import { useSessionReports } from '../hooks/useSessionReports';
import { useAuth } from '../context/AuthContext';
import Button from './ui/Button';
import { Input } from './ui/FormField';

interface Props {
  sessionId: number;
  type: 'progress' | 'quiz';
  label: string;
}

export default function SendReportForm({ sessionId, type, label }: Props) {
  const { sending, lastSent, error, send, download } = useSessionReports(sessionId);
  const { user } = useAuth();
  const [email, setEmail] = useState('');
  useEffect(() => {
    if (user?.effectiveReportEmail) setEmail((prev) => prev || user.effectiveReportEmail || '');
  }, [user]);

  const onSend = async () => {
    if (!email.trim()) return;
    await send(type, email.trim());
  };

  return (
    <div className="rounded-xl bg-booki-card p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-booki-muted">{label}</p>
      <p className="mt-1 text-[11px] text-white/40">
        Generates a real PDF and emails it to the address(es) below, if this server has email delivery
        configured — you can always download it instead. Separate several with ";".
      </p>
      <div className="mt-2 flex gap-2">
        <Input
          type="text"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="parent@email.com; tutor@email.com"
          className="flex-1"
        />
        <Button size="sm" onClick={onSend} disabled={sending === type || !email.trim()}>
          {sending === type ? 'Sending…' : 'Send'}
        </Button>
      </div>
      {error && <p className="mt-2 text-xs text-rose-400">{error}</p>}
      {lastSent && lastSent.type === type && (
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-emerald-400">
          <span className={lastSent.simulated ? 'text-amber-400' : undefined}>
            {lastSent.simulated
              ? lastSent.emailError
                ? `Could not email ${lastSent.email}: ${lastSent.emailError}`
                : `Not actually emailed to ${lastSent.email} — email isn't configured on this server`
              : `Sent to ${lastSent.email}`}
          </span>
          <button onClick={() => download(lastSent)} className="font-bold text-white/80 hover:text-white">
            Download PDF
          </button>
        </div>
      )}
    </div>
  );
}
