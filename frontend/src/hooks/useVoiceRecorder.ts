import { useCallback, useEffect, useRef, useState } from 'react';

interface UseVoiceRecorder {
  /** Browser can capture audio (getUserMedia + MediaRecorder). */
  supported: boolean;
  recording: boolean;
  /** Begin capturing. Prompts for mic permission on first use. */
  start: () => Promise<void>;
  /** Stop and resolve the recorded audio (null if nothing was captured). */
  stop: () => Promise<Blob | null>;
  /** Abort without producing a blob. */
  cancel: () => void;
}

const CANDIDATE_MIME_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];

function pickMimeType(): string | undefined {
  if (typeof MediaRecorder === 'undefined' || !MediaRecorder.isTypeSupported) return undefined;
  return CANDIDATE_MIME_TYPES.find((t) => MediaRecorder.isTypeSupported(t));
}

/**
 * Records a short clip with the standard browser audio APIs — the core cloud
 * voice path (upload to the backend, which transcribes). Independent of the
 * browser SpeechRecognition API (see useVoice, kept as a fallback).
 */
export function useVoiceRecorder(): UseVoiceRecorder {
  const supported =
    typeof navigator !== 'undefined' &&
    !!navigator.mediaDevices?.getUserMedia &&
    typeof MediaRecorder !== 'undefined';

  const [recording, setRecording] = useState(false);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);

  const releaseStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    recorderRef.current = null;
  }, []);

  useEffect(() => releaseStream, [releaseStream]);

  const start = useCallback(async () => {
    if (!supported || recorderRef.current) return;
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const mimeType = pickMimeType();
    const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
    chunksRef.current = [];
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunksRef.current.push(event.data);
    };
    streamRef.current = stream;
    recorderRef.current = recorder;
    recorder.start();
    setRecording(true);
  }, [supported]);

  const stop = useCallback(
    () =>
      new Promise<Blob | null>((resolve) => {
        const recorder = recorderRef.current;
        if (!recorder) {
          resolve(null);
          return;
        }
        recorder.onstop = () => {
          const type = recorder.mimeType || 'audio/webm';
          const blob = new Blob(chunksRef.current, { type });
          chunksRef.current = [];
          releaseStream();
          setRecording(false);
          resolve(blob.size > 0 ? blob : null);
        };
        recorder.stop();
      }),
    [releaseStream]
  );

  const cancel = useCallback(() => {
    const recorder = recorderRef.current;
    if (recorder && recorder.state !== 'inactive') {
      recorder.onstop = null;
      recorder.stop();
    }
    chunksRef.current = [];
    releaseStream();
    setRecording(false);
  }, [releaseStream]);

  return { supported, recording, start, stop, cancel };
}
