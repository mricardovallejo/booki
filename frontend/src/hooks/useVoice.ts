import { useRef, useState } from 'react';

interface UseVoice {
  supported: boolean;
  listening: boolean;
  start: () => Promise<string | null>;
  stop: () => void;
}

export function useVoice(): UseVoice {
  const [listening, setListening] = useState(false);
  const recognitionRef = useRef<SpeechRecognition | null>(null);
  const resolveRef = useRef<((value: string | null) => void) | null>(null);

  const supported = 'SpeechRecognition' in window || 'webkitSpeechRecognition' in window;

  const start = (): Promise<string | null> => {
    return new Promise((resolve) => {
      if (!supported) {
        resolve(null);
        return;
      }
      resolveRef.current = resolve;
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      const recognition = new SpeechRecognition();
      recognition.lang = 'es-ES';
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;

      recognition.onstart = () => setListening(true);
      recognition.onend = () => {
        setListening(false);
        recognitionRef.current = null;
      };
      recognition.onresult = (event: SpeechRecognitionEvent) => {
        const transcript = event.results[0][0].transcript;
        resolveRef.current?.(transcript);
      };
      recognition.onerror = () => {
        setListening(false);
        resolveRef.current?.(null);
      };

      recognitionRef.current = recognition;
      recognition.start();
    });
  };

  const stop = () => {
    recognitionRef.current?.stop();
    setListening(false);
  };

  return { supported, listening, start, stop };
}
