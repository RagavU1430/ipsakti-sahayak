import { useCallback, useEffect, useRef, useState } from 'react';
import type { AuthHeaders } from '../api/client';
import { askVoice, voiceAudioUrl } from '../api/voice';
import type { Jurisdiction, Language, QuestionResponse, VoiceAskResponse } from '../api/types';
import { useVoiceRecorder, type RecordingResult } from '../hooks/useVoiceRecorder';
import { EvidenceList } from './Evidence';
import { FormattedText } from './FormattedText';

type VoiceState = 'idle' | 'listening' | 'processing' | 'thinking' | 'answer_ready' | 'speaking' | 'error';

const languages: Array<{ code: Language; label: string }> = [
  { code: 'en', label: 'English' }, { code: 'hi', label: 'हिन्दी' }, { code: 'ta', label: 'தமிழ்' },
  { code: 'te', label: 'తెలుగు' }, { code: 'kn', label: 'ಕನ್ನಡ' }, { code: 'ml', label: 'മലയാളം' },
];

const captureErrors: Record<string, string> = {
  MICROPHONE_PERMISSION_DENIED: 'Microphone permission was denied. Allow microphone access in Brave and retry.',
  MICROPHONE_UNAVAILABLE: 'No usable microphone is available.',
  RECORDING_FAILED: 'Recording failed. Check the microphone and retry.',
  AUDIO_EMPTY: 'No audio was captured. Please record again.',
};

export interface VoiceChatOverlayProps {
  isOpen: boolean;
  onClose: (lastResponse?: QuestionResponse | null) => void;
  auth: AuthHeaders;
  initialLanguage?: Language;
  initialJurisdiction?: Jurisdiction;
  conversationId?: string;
}

export function VoiceChatOverlay({ isOpen, onClose, auth, initialLanguage = 'en', initialJurisdiction = 'INDIA', conversationId }: VoiceChatOverlayProps) {
  const [state, setState] = useState<VoiceState>('idle');
  const [language, setLanguage] = useState<Language>(initialLanguage);
  const [response, setResponse] = useState<VoiceAskResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [recordingUrl, setRecordingUrl] = useState<string | null>(null);
  const [answerAudioUrl, setAnswerAudioUrl] = useState<string | null>(null);
  const [autoplayBlocked, setAutoplayBlocked] = useState(false);
  const answerAudio = useRef<HTMLAudioElement | null>(null);
  const lastQuestionResponse = useRef<QuestionResponse | null>(null);

  const processRecording = useCallback(async ({ blob, previewUrl }: RecordingResult) => {
    setRecordingUrl((previous) => { if (previous) URL.revokeObjectURL(previous); return previewUrl; });
    setState('processing');
    setError(null);
    try {
      setState('thinking');
      const result = await askVoice(blob, language, initialJurisdiction, conversationId, auth);
      setResponse(result);
      lastQuestionResponse.current = {
        answer: result.answer, answerType: result.answerType, route: result.route, domain: result.domain,
        confidence: result.confidence, abstained: result.abstained, jurisdiction: result.jurisdiction,
        language: result.language, citations: result.citations || [], sources: result.sources || [],
      };
      const audioUrl = voiceAudioUrl(result);
      setAnswerAudioUrl((previous) => { if (previous) URL.revokeObjectURL(previous); return audioUrl; });
      setState('answer_ready');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Voice processing failed.');
      setState('error');
    }
  }, [auth, conversationId, initialJurisdiction, language]);

  const recorder = useVoiceRecorder(processRecording);

  useEffect(() => {
    if (recorder.error) { setError(captureErrors[recorder.error] || 'Voice capture failed.'); setState('error'); }
  }, [recorder.error]);

  useEffect(() => {
    if (state !== 'answer_ready' || !answerAudioUrl || !answerAudio.current) return;
    setAutoplayBlocked(false);
    answerAudio.current.play().catch(() => setAutoplayBlocked(true));
  }, [answerAudioUrl, state]);

  useEffect(() => () => {
    if (recordingUrl) URL.revokeObjectURL(recordingUrl);
    if (answerAudioUrl) URL.revokeObjectURL(answerAudioUrl);
  }, [recordingUrl, answerAudioUrl]);

  if (!isOpen) return null;

  const start = async () => {
    setResponse(null); setError(null); setAutoplayBlocked(false); recorder.clearError();
    setState('listening');
    await recorder.start();
  };
  const retry = () => { setError(null); recorder.clearError(); setState('idle'); };
  const close = () => { recorder.stop(true); answerAudio.current?.pause(); onClose(lastQuestionResponse.current); };

  return (
    <div className="voice-v2-backdrop" role="dialog" aria-modal="true" aria-label="Live Voice Assistant">
      <section className="voice-v2-panel">
        <header className="voice-v2-header">
          <div><p className="eyebrow">Voice Chat</p><h2>IP-SAKTI Sahayak</h2></div>
          <button type="button" className="voice-v2-close" onClick={close} aria-label="Close voice assistant">×</button>
        </header>

        <div className="voice-v2-languages" role="radiogroup" aria-label="Voice language">
          {languages.map((item) => <button key={item.code} type="button" role="radio" aria-checked={language === item.code}
            className={language === item.code ? 'active' : ''} disabled={state !== 'idle' && state !== 'error'} onClick={() => setLanguage(item.code)}>{item.label}</button>)}
        </div>

        <div className={`voice-v2-status ${state}`} role="status" aria-live="polite">
          <span className="material-symbols-outlined">{state === 'listening' ? 'graphic_eq' : state === 'speaking' ? 'volume_up' : 'mic'}</span>
          <strong>{state === 'idle' ? 'Ready to listen' : state === 'listening' ? 'Listening…' : state === 'processing' ? 'Uploading recording…' : state === 'thinking' ? 'Preparing an evidence-backed answer…' : state === 'answer_ready' ? 'Answer ready' : state === 'speaking' ? 'Speaking…' : 'Voice chat needs attention'}</strong>
        </div>

        <div className="voice-v2-actions">
          {state === 'listening' ? <button type="button" className="button primary" onClick={() => recorder.stop(false)}>Stop and ask</button>
            : <button type="button" className="button primary" onClick={start} disabled={state === 'processing' || state === 'thinking' || state === 'speaking'}>{response ? 'Ask another question' : 'Start recording'}</button>}
          {state === 'error' ? <button type="button" className="button secondary" onClick={retry}>Retry</button> : null}
        </div>

        {recordingUrl ? <div className="voice-v2-audio"><span>Your recording</span><audio controls src={recordingUrl} /></div> : null}
        {error ? <div className="notice error" role="alert"><strong>Voice request failed</strong><p>{error}</p></div> : null}

        {response ? <article className="voice-v2-result">
          <p><strong>You said:</strong> {response.transcript}</p>
          <div className="voice-v2-meta"><span className="sr-only" style={{ display: 'none' }}>{response.route}</span>{response.citations?.length ? <span className="evidence-badge">Authoritative Sources</span> : null}{response.confidence == null ? null : <span>{`${Math.round(response.confidence * 100)}% confidence`}</span>}</div>
          <FormattedText content={response.answer} />
          {answerAudioUrl ? <div className="voice-v2-audio"><span>Answer audio</span><audio ref={answerAudio} controls src={answerAudioUrl} onPlay={() => setState('speaking')} onEnded={() => setState('idle')} onError={() => { setError('The generated answer audio could not be played.'); setState('error'); }} /></div> : null}
          {autoplayBlocked ? <p className="muted">Autoplay was blocked. Tap Play to hear the answer.</p> : null}
          {(response.citations?.length || response.sources?.length) ? <EvidenceList citations={response.citations} sources={response.sources} /> : null}
        </article> : null}
      </section>
    </div>
  );
}

export { languages as SUPPORTED_VOICE_LANGUAGES };
export default VoiceChatOverlay;
