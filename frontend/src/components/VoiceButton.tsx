interface Props {
  /** Voice is available at all (cloud recorder or browser fallback). */
  supported: boolean;
  /** Currently recording / listening. */
  active: boolean;
  /** A turn is in flight (uploading / transcribing / replying). */
  busy?: boolean;
  onPress: () => void;
  size?: 'sm' | 'md';
}

const MIC_PATH =
  'M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-7a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z';

/**
 * Presentational mic button. All voice state lives in ChatPanel, which drives
 * either the cloud recorder (useVoiceRecorder + sendVoice) or the browser
 * fallback (useVoice).
 */
export default function VoiceButton({ supported, active, busy = false, onPress, size = 'md' }: Props) {
  const iconSize = size === 'sm' ? 'h-4 w-4' : 'h-5 w-5';
  const padding = size === 'sm' ? 'p-2' : 'p-3';

  if (!supported) {
    return (
      <button disabled className={`rounded-full bg-white/10 ${padding} text-white/40`} title="Voice unavailable">
        <svg className={iconSize} fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d={MIC_PATH} />
        </svg>
      </button>
    );
  }

  return (
    <button
      onClick={onPress}
      disabled={busy && !active}
      className={`group relative rounded-full ${padding} transition ${
        active
          ? 'bg-booki-accent text-white shadow-lg shadow-booki-accent/30'
          : 'bg-white/10 text-white hover:bg-white/20'
      } ${busy && !active ? 'opacity-60' : ''}`}
      title={active ? 'Stop and send' : 'Speak to BooKI'}
    >
      {active && <span className="absolute inset-0 animate-ping rounded-full bg-booki-accent/40" />}
      <svg className={`relative ${iconSize}`} fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" d={MIC_PATH} />
      </svg>
    </button>
  );
}
