import { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useChat } from '../hooks/useChat';
import { useSession } from '../hooks/useSession';
import { useVoice } from '../hooks/useVoice';
import { useVoiceRecorder } from '../hooks/useVoiceRecorder';
import { getVoiceCapabilities, type VoiceCapabilities } from '../api/voice';
import VoiceButton from './VoiceButton';
import AudioReplyToggle from './AudioReplyToggle';
import SummaryModal from './SummaryModal';
import type { CapabilityHint, SessionLanguage } from '../types';

// Un-opinionated markdown (react-markdown emits bare <strong>/<ul>/<h1>, no
// Tailwind styling of its own) — these classes give it a look at home inside
// a chat bubble without pulling in the Typography plugin for just this.
// Tables need overflow-x-auto of their own since a chat bubble is narrow
// (max-w-[85%]) and a wide table must scroll, not stretch the bubble.
const MARKDOWN_CLASSES =
  '[&_p]:mb-2 last:[&_p]:mb-0 [&_strong]:font-bold [&_em]:italic ' +
  '[&_ul]:my-2 [&_ul]:list-disc [&_ul]:pl-5 [&_ol]:my-2 [&_ol]:list-decimal [&_ol]:pl-5 [&_li]:mb-1 ' +
  '[&_h1]:mb-2 [&_h1]:mt-1 [&_h1]:text-base [&_h1]:font-bold ' +
  '[&_h2]:mb-2 [&_h2]:mt-1 [&_h2]:text-sm [&_h2]:font-bold ' +
  '[&_h3]:mb-1 [&_h3]:mt-1 [&_h3]:text-sm [&_h3]:font-semibold ' +
  '[&_hr]:my-3 [&_hr]:border-white/10 ' +
  '[&_code]:rounded [&_code]:bg-black/20 [&_code]:px-1 [&_code]:py-0.5 [&_code]:text-xs ' +
  '[&_a]:underline [&_a]:underline-offset-2 ' +
  '[&_table]:my-2 [&_table]:w-full [&_table]:border-collapse [&_table]:text-xs ' +
  '[&_th]:border [&_th]:border-white/15 [&_th]:bg-white/5 [&_th]:px-2 [&_th]:py-1 [&_th]:text-left [&_th]:font-semibold ' +
  '[&_td]:border [&_td]:border-white/15 [&_td]:px-2 [&_td]:py-1 [&_td]:align-top';

// Quick actions are shortcuts for conversational intentions, not a separate
// system: each one posts a natural-language message (localized to the session
// language) plus a capabilityHint on the SAME POST /messages endpoint, so the
// transcript reads naturally and the backend runs that capability directly.
interface QuickAction {
  hint: CapabilityHint;
  label: Record<SessionLanguage, string>;
  text: Record<SessionLanguage, string>;
}

const QUICK_ACTIONS: QuickAction[] = [
  {
    hint: 'quiz',
    label: { en: 'Ask me', es: 'Pregúntame', fr: 'Interroge-moi' },
    text: {
      en: 'Ask me a question about what I just read.',
      es: 'Hazme una pregunta sobre lo que acabo de leer.',
      fr: 'Pose-moi une question sur ce que je viens de lire.'
    }
  },
  {
    hint: 'explain',
    label: { en: 'Explain', es: 'Explica', fr: 'Explique' },
    text: {
      en: "I didn't understand this part — can you explain it?",
      es: 'No entendí esta parte, ¿me la puedes explicar?',
      fr: "Je n'ai pas compris ce passage, peux-tu l'expliquer ?"
    }
  },
  {
    hint: 'summary',
    label: { en: 'Summarize', es: 'Resume', fr: 'Résume' },
    text: {
      en: 'Summarize these pages for me.',
      es: 'Resúmeme estas páginas.',
      fr: 'Résume-moi ces pages.'
    }
  },
  {
    hint: 'mnemonic',
    label: { en: 'Memorize', es: 'Memoriza', fr: 'Mémorise' },
    text: {
      en: 'Help me remember the key ideas from these pages.',
      es: 'Ayúdame a recordar las ideas clave de estas páginas.',
      fr: 'Aide-moi à retenir les idées clés de ces pages.'
    }
  }
];

interface Props {
  sessionId: number;
  onActivity?: () => void;
}

export default function ChatPanel({ sessionId, onActivity }: Props) {
  const { messages, sending, error, send, sendVoice, refresh } = useChat(sessionId, onActivity);
  const { session } = useSession(sessionId);
  const lang: SessionLanguage = session?.language ?? 'en';
  const enabledCapabilities = session?.enabledCapabilities ?? ['quiz', 'summary', 'explain', 'mnemonic'];
  const quickActions = QUICK_ACTIONS.filter((a) => enabledCapabilities.includes(a.hint));
  const summaryEnabled = enabledCapabilities.includes('summary');
  const [text, setText] = useState('');
  const [summaryOpen, setSummaryOpen] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const recorder = useVoiceRecorder();
  const fallbackVoice = useVoice(lang);
  const [voiceCaps, setVoiceCaps] = useState<VoiceCapabilities | null>(null);
  const replyAudioRef = useRef<HTMLAudioElement | null>(null);
  // Per-turn choice, not persisted: whether a voice question gets a spoken
  // reply back, or text-only (cheaper, and better for reading together — see
  // docs/decisions.md ADR-009 addendum). Independent of the mic itself.
  const [wantsAudioReply, setWantsAudioReply] = useState(true);

  useEffect(() => {
    getVoiceCapabilities()
      .then(setVoiceCaps)
      .catch(() => setVoiceCaps({ stt: false, tts: false }));
  }, []);

  // Cloud path when the browser can record AND the backend has an STT provider;
  // otherwise the browser SpeechRecognition fallback (posts a normal VOICE message).
  const cloudVoice = recorder.supported && !!voiceCaps?.stt;
  const voiceSupported = cloudVoice || fallbackVoice.supported;
  const voiceActive = recorder.recording || fallbackVoice.listening;

  const playReply = (audioBase64: string, contentType: string | null) => {
    const audio = new Audio(`data:${contentType ?? 'audio/mpeg'};base64,${audioBase64}`);
    replyAudioRef.current = audio;
    audio.play().catch(() => undefined);
  };

  const onVoicePress = async () => {
    if (cloudVoice) {
      if (recorder.recording) {
        const clip = await recorder.stop();
        if (clip) {
          const result = await sendVoice(clip, undefined, wantsAudioReply);
          if (result?.audioBase64) playReply(result.audioBase64, result.audioContentType);
        }
      } else {
        try {
          await recorder.start();
        } catch {
          // mic permission denied / no device — button returns to idle, nothing to send
        }
      }
      return;
    }
    if (fallbackVoice.listening) {
      fallbackVoice.stop();
      return;
    }
    const transcript = await fallbackVoice.start();
    if (transcript) await send(transcript, 'VOICE');
  };

  const runQuickAction = (action: QuickAction) => send(action.text[lang], 'TEXT', action.hint);

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
        {summaryEnabled && (
          <button
            onClick={() => setSummaryOpen(true)}
            className="flex items-center gap-1.5 rounded-full bg-white/5 px-3 py-1 text-xs font-medium text-white/70 transition hover:bg-white/10 hover:text-white"
          >
            <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h4m1 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            Summary…
          </button>
        )}
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
              className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
                m.speaker === 'USER'
                  ? 'whitespace-pre-line rounded-br-none bg-booki-accent text-white'
                  : 'rounded-bl-none bg-booki-card text-white/90'
              }`}
            >
              {m.speaker === 'BOOKI' ? (
                <div className={MARKDOWN_CLASSES}>
                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    components={{
                      table: ({ ...props }) => (
                        <div className="overflow-x-auto">
                          <table {...props} />
                        </div>
                      )
                    }}
                  >
                    {m.message}
                  </ReactMarkdown>
                </div>
              ) : (
                <p>{m.message}</p>
              )}
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
        {quickActions.length > 0 && (
          <div className="mb-2 flex gap-1.5 overflow-x-auto pb-1">
            {quickActions.map((action) => (
              <button
                key={action.hint}
                onClick={() => runQuickAction(action)}
                disabled={sending}
                className="font-menu shrink-0 rounded-full bg-white/5 px-3 py-1 text-[10px] tracking-wide text-white/70 transition hover:bg-white/10 hover:text-white disabled:opacity-40"
              >
                {action.label[lang]}
              </button>
            ))}
          </div>
        )}
        <div className="flex items-center gap-3 rounded-2xl bg-booki-card px-3 py-2">
          <VoiceButton
            supported={voiceSupported}
            active={voiceActive}
            busy={sending}
            onPress={onVoicePress}
            size="sm"
          />
          {cloudVoice && (
            <AudioReplyToggle enabled={wantsAudioReply} onToggle={() => setWantsAudioReply((v) => !v)} />
          )}
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
