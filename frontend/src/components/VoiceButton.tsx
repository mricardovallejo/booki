import { useState } from 'react';
import { useVoice } from '../hooks/useVoice';

interface Props {
  onResult: (transcript: string) => void;
  size?: 'sm' | 'md';
}

export default function VoiceButton({ onResult, size = 'md' }: Props) {
  const [busy, setBusy] = useState(false);
  const { start, stop, listening, supported } = useVoice();

  if (!supported) {
    return (
      <button
        disabled
        className="rounded-full bg-white/10 p-3 text-white/40"
        title="Voz no disponible"
      >
        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-7a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
        </svg>
      </button>
    );
  }

  const handleClick = async () => {
    if (listening) {
      stop();
      return;
    }
    setBusy(true);
    try {
      const transcript = await start();
      if (transcript) {
        onResult(transcript);
      }
    } finally {
      setBusy(false);
    }
  };

  const sizeClasses = size === 'sm' ? 'h-4 w-4' : 'h-5 w-5';
  const buttonSize = size === 'sm' ? 'p-2' : 'p-3';

  return (
    <button
      onClick={handleClick}
      disabled={busy && !listening}
      className={`group relative rounded-full ${buttonSize} transition ${
        listening
          ? 'bg-booki-accent text-white shadow-lg shadow-booki-accent/30'
          : 'bg-white/10 text-white hover:bg-white/20'
      } ${busy && !listening ? 'opacity-60' : ''}`}
      title={listening ? 'Escuchando…' : 'Hablar'}
    >
      {listening && (
        <span className="absolute inset-0 animate-ping rounded-full bg-booki-accent/40" />
      )}
      <svg className={`relative ${sizeClasses}`} fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-7a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
      </svg>
    </button>
  );
}
