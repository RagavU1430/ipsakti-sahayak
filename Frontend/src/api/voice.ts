import type { AuthHeaders } from './client';
import type { Jurisdiction, Language, VoiceAskResponse, VoiceHealthResponse } from './types';

const baseUrl = (import.meta.env.VITE_BACKEND_BASE_URL || '').replace(/\/$/, '');
const timeoutMs = 120_000;

const messages: Record<string, string> = {
  AUDIO_EMPTY: 'No audio was captured. Please record again.',
  AUDIO_TOO_LARGE: 'The recording is too long. Please ask a shorter question.',
  UNSUPPORTED_AUDIO: 'This browser produced an unsupported audio format.',
  STT_EMPTY: "I couldn't hear clear speech. Please try again closer to the microphone.",
  STT_FAILED: "I couldn't transcribe that recording. Please try again.",
  TTS_FAILED: 'The answer was created, but its audio could not be generated.',
  TTS_UNAVAILABLE: 'Answer audio is temporarily unavailable after the bounded voice-provider fallback.',
  VOICE_PROVIDER_UNAVAILABLE: 'The voice provider is temporarily unavailable.',
  AUTH_REQUIRED: 'Please sign in before adding voice messages to a conversation.',
  INVALID_LANGUAGE: 'Choose one of the six supported languages.',
  TIMEOUT: 'Voice processing took too long. Please retry.',
};

function headers(auth: AuthHeaders): Headers {
  const value = new Headers({ Accept: 'application/json' });
  if (auth.token) value.set('Authorization', `Bearer ${auth.token}`);
  if (auth.devUserId) value.set('X-Dev-User-Id', auth.devUserId);
  else if (!auth.token) value.set('X-Dev-User-Id', 'demo-user');
  return value;
}

export async function checkVoiceHealth(auth: AuthHeaders = {}): Promise<VoiceHealthResponse> {
  const response = await fetch(`${baseUrl}/api/v1/voice/health`, { headers: headers(auth) });
  if (!response.ok) throw new Error('Voice service health check failed.');
  return response.json();
}

export async function askVoice(
  audio: Blob,
  language: Language,
  jurisdiction: Jurisdiction,
  conversationId: string | undefined,
  auth: AuthHeaders
): Promise<VoiceAskResponse> {
  const extension = audio.type.includes('ogg') ? 'ogg' : audio.type.includes('mp4') ? 'm4a' : audio.type.includes('wav') ? 'wav' : 'webm';
  const form = new FormData();
  form.append('audio', audio, `voice-question.${extension}`);
  form.append('language', language);
  form.append('jurisdiction', jurisdiction);
  if (conversationId) form.append('conversationId', conversationId);

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${baseUrl}/api/v1/voice/ask`, {
      method: 'POST', headers: headers(auth), body: form, signal: controller.signal,
    });
    const body = (response.headers.get('content-type') || '').includes('application/json') ? await response.json() : {};
    if (!response.ok) {
      const code = body.code || 'NETWORK_ERROR';
      const error = new Error(messages[code] || body.error || 'Voice request failed.') as Error & { code?: string };
      error.code = code;
      throw error;
    }
    return body as VoiceAskResponse;
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      const timeoutError = new Error(messages.TIMEOUT) as Error & { code?: string };
      timeoutError.code = 'TIMEOUT';
      throw timeoutError;
    }
    if (error instanceof TypeError) {
      const networkError = new Error('The voice service could not be reached. Check your connection and retry.') as Error & { code?: string };
      networkError.code = 'NETWORK_ERROR';
      throw networkError;
    }
    throw error;
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function synthesizeSpeech(text: string, language: Language, auth: AuthHeaders): Promise<string> {
  const requestHeaders = headers(auth);
  requestHeaders.set('Content-Type', 'application/json');
  const response = await fetch(`${baseUrl}/api/v1/voice/synthesize`, {
    method: 'POST',
    headers: requestHeaders,
    body: JSON.stringify({ text, language }),
  });
  if (!response.ok) {
    const body = (response.headers.get('content-type') || '').includes('application/json')
      ? await response.json() : {};
    const code = body.code || 'TTS_FAILED';
    const error = new Error(messages[code] || body.error || 'The selected language audio could not be generated.') as Error & { code?: string };
    error.code = code;
    throw error;
  }
  return URL.createObjectURL(await response.blob());
}

export function voiceAudioUrl(response: VoiceAskResponse): string | null {
  if (!response.audioBase64) return null;
  const binary = window.atob(response.audioBase64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return URL.createObjectURL(new Blob([bytes], { type: response.audioMimeType || 'audio/wav' }));
}
