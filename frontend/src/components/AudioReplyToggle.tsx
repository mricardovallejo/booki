interface Props {
  /** Whether a cloud voice reply is currently audible (vs. text-only). */
  enabled: boolean;
  onToggle: () => void;
}

const SPEAKER_PATH = 'M11 5L6 9H2v6h4l5 4V5z';
const WAVES_PATH = 'M15.54 8.46a5 5 0 010 7.07M19.07 4.93a10 10 0 010 14.14';
const MUTE_LINE = 'M23 9l-6 6M17 9l6 6';

/**
 * Per-turn toggle for whether BooKI's spoken reply is synthesized at all —
 * independent from the mic (voice input always transcribes). Off skips the
 * TTS call server-side (real cost/latency savings, not just muted playback),
 * useful when text on screen is preferable to audio (e.g. studying together).
 */
export default function AudioReplyToggle({ enabled, onToggle }: Props) {
  return (
    <button
      onClick={onToggle}
      title={enabled ? 'Spoken replies on — tap to switch to text-only' : 'Text-only replies — tap to hear them spoken'}
      className={`rounded-full p-2 transition ${
        enabled ? 'bg-white/10 text-white hover:bg-white/20' : 'bg-white/5 text-white/40 hover:bg-white/10'
      }`}
    >
      <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" d={SPEAKER_PATH} />
        {enabled ? (
          <path strokeLinecap="round" strokeLinejoin="round" d={WAVES_PATH} />
        ) : (
          <path strokeLinecap="round" strokeLinejoin="round" d={MUTE_LINE} />
        )}
      </svg>
    </button>
  );
}
