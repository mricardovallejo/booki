import { useEffect, useRef, useState } from 'react';
import { useChat } from '../hooks/useChat';
import VoiceButton from './VoiceButton';
import SummaryModal from './SummaryModal';

interface Props {
  sessionId: number;
  onActivity?: () => void;
}

export default function ChatPanel({ sessionId, onActivity }: Props) {
  const { messages, sending, error, send, refresh } = useChat(sessionId, onActivity);
  const [text, setText] = useState('');
  const [summaryOpen, setSummaryOpen] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const onSend = async () => {
    const value = text;
    setText('');
    await send(value, 'TEXT');
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-end border-b border-white/10 px-5 py-2">
        <button
          onClick={() => setSummaryOpen(true)}
          className="flex items-center gap-1.5 rounded-full bg-white/5 px-3 py-1 text-xs font-medium text-white/70 transition hover:bg-white/10 hover:text-white"
        >
          <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h4m1 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          Summarize
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-4">
        {messages.length === 0 && (
          <div className="flex h-full flex-col items-center justify-center text-center text-booki-muted">
            <div className="mb-3 rounded-full bg-white/5 p-4">
              <svg className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
              </svg>
            </div>
            <p className="max-w-[200px] text-sm">Ask BooKI about what you're reading.</p>
          </div>
        )}

        {messages.map((m) => (
          <div
            key={m.id}
            className={`mb-4 flex ${m.speaker === 'USER' ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className={`max-w-[85%] whitespace-pre-line rounded-2xl px-4 py-3 text-sm leading-relaxed ${
                m.speaker === 'USER'
                  ? 'rounded-br-none bg-booki-accent text-white'
                  : 'rounded-bl-none bg-booki-card text-white/90'
              }`}
            >
              <p>{m.message}</p>
              <span className="mt-2 block text-[10px] opacity-60">
                {m.inputType === 'VOICE' ? 'Voice' : 'Text'}
              </span>
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <div className="border-t border-white/10 p-4">
        {error && <p className="mb-2 text-xs text-rose-400">{error}</p>}
        <div className="flex items-center gap-3 rounded-2xl bg-booki-card px-3 py-2">
          <VoiceButton onResult={(t) => send(t, 'VOICE')} size="sm" />
          <input
            type="text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && onSend()}
            placeholder="Write to BooKI…"
            disabled={sending}
            className="flex-1 bg-transparent py-2 text-sm text-white placeholder-white/40 outline-none"
          />
          <button
            onClick={onSend}
            disabled={sending || !text.trim()}
            className="rounded-full bg-white/10 p-2 text-white transition hover:bg-white/20 disabled:opacity-40"
          >
            {sending ? (
              <span className="block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
            ) : (
              <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
              </svg>
            )}
          </button>
        </div>
      </div>

      <SummaryModal
        sessionId={sessionId}
        open={summaryOpen}
        onClose={() => setSummaryOpen(false)}
        onChatGenerated={refresh}
      />
    </div>
  );
}
