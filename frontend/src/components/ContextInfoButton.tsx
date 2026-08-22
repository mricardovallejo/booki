import { useState } from 'react';
import { useSessionContext } from '../hooks/useSessionContext';
import { useSession } from '../hooks/useSession';
import { AI_PROVIDER_LABELS } from '../lib/aiProviders';

interface Props {
  sessionId: number;
}

export default function ContextInfoButton({ sessionId }: Props) {
  const context = useSessionContext(sessionId);
  const { session } = useSession(sessionId);
  const [open, setOpen] = useState(false);

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="rounded-full bg-white/5 p-2 text-white/80 transition hover:bg-white/10 hover:text-white"
        title="What BooKI knows for this session"
      >
        <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </button>
      {open && context && (
        <div className="absolute right-0 top-full z-20 mt-2 w-80 rounded-lg bg-booki-surface p-4 shadow-2xl ring-1 ring-white/10">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-booki-muted">
            What shapes BooKI's answers here
          </p>
          <div className="space-y-3 text-xs">
            <div>
              <p className="font-bold text-white/70">AI model</p>
              <p className="mt-0.5 text-white/60">
                {session ? AI_PROVIDER_LABELS[session.aiProvider] : '…'}
              </p>
            </div>
            <div>
              <p className="font-bold text-white/70">App</p>
              <p className="mt-0.5 text-white/60">{context.appPrompt}</p>
            </div>
            <div>
              <p className="font-bold text-white/70">Profile Master</p>
              <p className="mt-0.5 text-white/60">{context.masterPrompt || 'No Master selected for this session.'}</p>
            </div>
            <div>
              <p className="font-bold text-white/70">Your profile</p>
              <p className="mt-0.5 text-white/60">
                {context.userPrompt || 'Add your preferences from "Edit profile" to personalize this.'}
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
