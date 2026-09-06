import { useCallback, useEffect, useRef, useState } from 'react';
import type { Language } from '../api/types';

// Browser-compatible speech recognition interface
interface SpeechRecognitionEventLike extends Event {
  results: SpeechRecognitionResultList;
  resultIndex: number;
}

interface SpeechRecognitionErrorEventLike extends Event {
  error: string;
  message?: string;
}

interface SpeechRecognitionLike extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  onstart: (() => void) | null;
}

declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognitionLike;
    webkitSpeechRecognition?: new () => SpeechRecognitionLike;
  }
}

export const LANGUAGE_LOCALE_MAP: Record<Language, string> = {
  en: 'en-IN',
  hi: 'hi-IN',
  ta: 'ta-IN',
  te: 'te-IN',
  kn: 'kn-IN',
  ml: 'ml-IN',
};

export interface UseSpeechRecognitionOptions {
  language: Language;
  onTranscriptChange?: (text: string) => void;
  onFinalTranscript?: (text: string) => void;
}

export function useSpeechRecognition({
  language,
  onTranscriptChange,
  onFinalTranscript,
}: UseSpeechRecognitionOptions) {
  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [error, setError] = useState<string | null>(null);

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const isSupported = typeof window !== 'undefined' && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);

  const targetLocale = LANGUAGE_LOCALE_MAP[language] || 'en-IN';

  const stopListening = useCallback(() => {
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch {
        // Ignore if already stopped
      }
    }
    setIsListening(false);
  }, []);

  const startListening = useCallback(() => {
    setError(null);
    if (!isSupported) {
      setError('Speech recognition is not supported by your browser. Please use Chrome, Edge, or Safari.');
      return;
    }

    try {
      const SpeechRecognitionClass = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!SpeechRecognitionClass) return;

      if (recognitionRef.current) {
        try {
          recognitionRef.current.abort();
        } catch {
          // Ignore
        }
      }

      const recognition = new SpeechRecognitionClass();
      recognition.continuous = false;
      recognition.interimResults = true;
      recognition.lang = targetLocale;

      recognition.onstart = () => {
        setIsListening(true);
      };

      recognition.onresult = (event: SpeechRecognitionEventLike) => {
        let currentTranscript = '';
        let isFinal = false;

        for (let i = event.resultIndex; i < event.results.length; i++) {
          const item = event.results[i];
          currentTranscript += item[0].transcript;
          if (item.isFinal) {
            isFinal = true;
          }
        }

        setTranscript(currentTranscript);
        if (onTranscriptChange) {
          onTranscriptChange(currentTranscript);
        }

        if (isFinal && onFinalTranscript) {
          onFinalTranscript(currentTranscript);
        }
      };

      recognition.onerror = (event: SpeechRecognitionErrorEventLike) => {
        // 'aborted' is fired when recognition is stopped or re-initialized intentionally; do not show error
        if (event.error === 'aborted') {
          setIsListening(false);
          return;
        }

        if (event.error === 'no-speech') {
          setError('No speech was detected. Please speak into your microphone.');
        } else if (event.error === 'not-allowed') {
          setError('Microphone access was denied. Please allow microphone permissions in your browser settings.');
        } else if (event.error === 'network') {
          setError(
            'Speech service unreachable. If using Brave Browser, enable "Use Google services for speech recognition" in brave://settings/system. Also check your internet connection or ad-blocker.'
          );
        } else {
          setError(`Speech error: ${event.error}`);
        }
        setIsListening(false);
      };

      recognition.onend = () => {
        setIsListening(false);
      };

      recognitionRef.current = recognition;
      recognition.start();
    } catch (err) {
      setError('Could not start speech recognition.');
      setIsListening(false);
    }
  }, [isSupported, targetLocale, onTranscriptChange, onFinalTranscript]);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  // Auto-dismiss error after 8 seconds
  useEffect(() => {
    if (error) {
      const timer = setTimeout(() => setError(null), 8000);
      return () => clearTimeout(timer);
    }
  }, [error]);

  const toggleListening = useCallback(() => {
    if (isListening) {
      stopListening();
    } else {
      startListening();
    }
  }, [isListening, startListening, stopListening]);

  // Clean up on unmount
  useEffect(() => {
    return () => {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.abort();
        } catch {
          // Ignore
        }
      }
    };
  }, []);

  return {
    isListening,
    transcript,
    error,
    isSupported,
    startListening,
    stopListening,
    toggleListening,
    clearError,
    targetLocale,
  };
}
