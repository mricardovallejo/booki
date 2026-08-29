import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type { CapabilityHint, Message } from '../types';

export interface VoiceCapabilities {
  /** Server-side speech-to-text is configured (cloud voice path available). */
  stt: boolean;
  /** Server-side text-to-speech is configured (spoken replies available). */
  tts: boolean;
}

export const getVoiceCapabilities = () =>
  api.get<VoiceCapabilities>(ENDPOINTS.voice.capabilities).then((r) => r.data);

export interface VoiceTurnResult {
  userMessage: Message;
  botMessage: Message;
  /** Base64 MP3 of BooKI's spoken reply, or null when TTS is unavailable. */
  audioBase64: string | null;
  audioContentType: string | null;
}

/** One audio-in / (text + optional audio)-out turn through the same conversation pipeline as text. */
export const sendVoiceTurn = (
  sessionId: number,
  audio: Blob,
  capabilityHint?: CapabilityHint,
  wantsAudioReply = true
) => {
  const form = new FormData();
  form.append('audio', audio, 'turn.webm');
  if (capabilityHint) form.append('capabilityHint', capabilityHint);
  form.append('wantsAudioReply', String(wantsAudioReply));
  return api
    .post<VoiceTurnResult>(ENDPOINTS.sessions.voice(sessionId), form, {
      headers: { 'Content-Type': undefined }
    })
    .then((r) => r.data);
};
