import React from 'react';
import type { Language } from '../api/types';
import { LANGUAGE_LOCALE_MAP } from '../hooks/useSpeechRecognition';

interface MicButtonProps {
  isListening: boolean;
  onToggle: () => void;
  disabled?: boolean;
  language: Language;
  errorMessage?: string | null;
  onClearError?: () => void;
}

export function MicButton({ isListening, onToggle, disabled, language, errorMessage, onClearError }: MicButtonProps) {
  const langNames: Record<Language, string> = {
    en: 'English',
    hi: 'Hindi (हिन्दी)',
    ta: 'Tamil (தமிழ்)',
    te: 'Telugu (తెలుగు)',
    kn: 'Kannada (ಕನ್ನಡ)',
    ml: 'Malayalam (മലയാളം)',
  };

  return (
    <div style={{ position: 'relative', display: 'inline-flex', alignItems: 'center' }}>
      <button
        type="button"
        onClick={onToggle}
        disabled={disabled}
        className={`claude-mic-btn ${isListening ? 'listening' : ''}`}
        aria-label={isListening ? 'Stop recording voice' : `Speak in ${langNames[language]}`}
        title={isListening ? 'Listening... Click to stop' : `Voice input in ${langNames[language]} (${LANGUAGE_LOCALE_MAP[language]})`}
      >
        <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>
          {isListening ? 'mic' : 'mic_none'}
        </span>
        {isListening && (
          <span className="mic-pulse-ring" aria-hidden="true" />
        )}
      </button>

      {errorMessage && (
        <div className="mic-error-tooltip" role="alert">
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
            <span style={{ flex: 1 }}>{errorMessage}</span>
            {onClearError && (
              <button
                type="button"
                onClick={onClearError}
                aria-label="Dismiss error"
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#94a3b8',
                  cursor: 'pointer',
                  fontSize: '14px',
                  padding: 0,
                  lineHeight: 1,
                }}
              >
                ✕
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

interface SpeakButtonProps {
  isSpeaking: boolean;
  onToggle: () => void;
  title?: string;
  size?: 'small' | 'medium';
}

export function SpeakButton({ isSpeaking, onToggle, title = 'Listen to answer', size = 'medium' }: SpeakButtonProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={`speech-readout-btn ${isSpeaking ? 'active' : ''} ${size}`}
      title={isSpeaking ? 'Stop speaking' : title}
      aria-label={isSpeaking ? 'Stop speaking' : title}
    >
      <span className="material-symbols-outlined" style={{ fontSize: size === 'small' ? '18px' : '20px' }}>
        {isSpeaking ? 'volume_up' : 'volume_mute'}
      </span>
      <span>{isSpeaking ? 'Stop' : 'Listen'}</span>
      {isSpeaking && (
        <span className="audio-wave" aria-hidden="true">
          <span className="bar" />
          <span className="bar" />
          <span className="bar" />
        </span>
      )}
    </button>
  );
}

interface VoiceModeToggleProps {
  enabled: boolean;
  onToggle: () => void;
}

export function VoiceModeToggle({ enabled, onToggle }: VoiceModeToggleProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={`voice-mode-pill ${enabled ? 'active' : ''}`}
      title={enabled ? 'Speech-to-Speech active: Answers will be read aloud automatically' : 'Enable Speech-to-Speech mode'}
    >
      <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>
        {enabled ? 'record_voice_over' : 'voice_chat'}
      </span>
      <span>Speech-to-Speech {enabled ? 'ON' : 'OFF'}</span>
    </button>
  );
}
