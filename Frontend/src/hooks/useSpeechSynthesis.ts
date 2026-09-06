import { useCallback, useEffect, useRef, useState } from 'react';
import type { Language } from '../api/types';
import { LANGUAGE_LOCALE_MAP } from './useSpeechRecognition';

export interface UseSpeechSynthesisOptions {
  defaultLanguage?: Language;
  onStart?: () => void;
  onEnd?: () => void;
  onError?: (err: unknown) => void;
}

/**
 * Strips markdown symbols, asterisks, URLs, and citations from text
 * so SpeechSynthesis produces natural sounding speech.
 */
export function cleanTextForSpeech(text: string): string {
  if (!text) return '';

  return text
    // Remove markdown code blocks
    .replace(/```[\s\S]*?```/g, '')
    // Remove inline code
    .replace(/`([^`]+)`/g, '$1')
    // Remove markdown headers #, ##, etc.
    .replace(/^#{1,6}\s+/gm, '')
    // Remove bold and italic asterisks
    .replace(/\*{1,3}([^*]+)\*{1,3}/g, '$1')
    // Remove strikethroughs
    .replace(/~~([^~]+)~~/g, '$1')
    // Remove links [text](url) -> text
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    // Remove standalone URLs
    .replace(/https?:\/\/\S+/g, '')
    // Clean bullet points
    .replace(/^[-*•]\s+/gm, '')
    // Normalize excessive whitespace
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Standard Web Speech API Synthesis Hook for Multilingual Support (EN, HI, TA, TE, KN, ML).
 * Uses window.speechSynthesis with proper locale mapping (ta-IN, hi-IN, te-IN, kn-IN, ml-IN, en-IN).
 */
export function useSpeechSynthesis({
  defaultLanguage = 'en',
  onStart,
  onEnd,
  onError,
}: UseSpeechSynthesisOptions = {}) {
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [voices, setVoices] = useState<SpeechSynthesisVoice[]>([]);

  const isSupported = typeof window !== 'undefined' && 'speechSynthesis' in window;
  const currentUtteranceRef = useRef<SpeechSynthesisUtterance | null>(null);
  const isCancelledRef = useRef<boolean>(false);

  // Load available system voices
  useEffect(() => {
    if (!isSupported) return;

    const updateVoices = () => {
      const available = window.speechSynthesis.getVoices();
      if (available.length > 0) {
        setVoices(available);
      }
    };

    updateVoices();
    window.speechSynthesis.addEventListener('voiceschanged', updateVoices);
    window.speechSynthesis.onvoiceschanged = updateVoices;

    return () => {
      window.speechSynthesis.removeEventListener('voiceschanged', updateVoices);
    };
  }, [isSupported]);

  // Find best voice for language if available
  const getVoiceForLanguage = useCallback((lang: Language): SpeechSynthesisVoice | null => {
    const availableVoices = voices.length > 0
      ? voices
      : (typeof window !== 'undefined' && 'speechSynthesis' in window ? window.speechSynthesis.getVoices() : []);

    if (!availableVoices.length) return null;

    const targetLocale = (LANGUAGE_LOCALE_MAP[lang] || 'en-IN').toLowerCase();
    const langPrefix = targetLocale.split('-')[0];

    // Search by exact locale, prefix, or language name substring
    const match = availableVoices.find((v) => {
      const vLang = v.lang.toLowerCase().replace('_', '-');
      const vName = v.name.toLowerCase();
      return (
        vLang === targetLocale ||
        vLang.startsWith(langPrefix) ||
        (lang === 'ta' && (vName.includes('tamil') || vName.includes('தமிழ்') || vLang.includes('ta'))) ||
        (lang === 'hi' && (vName.includes('hindi') || vName.includes('हिन्दी') || vLang.includes('hi'))) ||
        (lang === 'te' && (vName.includes('telugu') || vName.includes('తెలుగు') || vLang.includes('te'))) ||
        (lang === 'kn' && (vName.includes('kannada') || vName.includes('ಕನ್ನಡ') || vLang.includes('kn'))) ||
        (lang === 'ml' && (vName.includes('malayalam') || vName.includes('മലയാളം') || vLang.includes('ml')))
      );
    });

    if (match) return match;

    // Fallback to Indian English or default English voice for English queries
    if (lang === 'en') {
      const fallbackIndian = availableVoices.find((v) => v.lang.toLowerCase().includes('en-in'));
      if (fallbackIndian) return fallbackIndian;
      return availableVoices.find((v) => v.lang.toLowerCase().startsWith('en')) || availableVoices[0] || null;
    }

    return null;
  }, [voices]);

  const cancel = useCallback(() => {
    isCancelledRef.current = true;
    if (isSupported) {
      try {
        window.speechSynthesis.cancel();
      } catch {
        // Ignore
      }
    }
    setIsSpeaking(false);
    setIsPaused(false);
    currentUtteranceRef.current = null;
  }, [isSupported]);

  const speak = useCallback((rawText: string, lang: Language = defaultLanguage) => {
    if (!isSupported) return;

    // Cancel any ongoing speech
    cancel();
    isCancelledRef.current = false;

    const text = cleanTextForSpeech(rawText);
    if (!text) return;

    try {
      if (window.speechSynthesis.paused) {
        window.speechSynthesis.resume();
      }

      const utterance = new SpeechSynthesisUtterance(text);
      const targetLocale = LANGUAGE_LOCALE_MAP[lang] || 'en-IN';
      utterance.lang = targetLocale;

      const targetVoice = getVoiceForLanguage(lang);
      if (targetVoice) {
        utterance.voice = targetVoice;
      }

      utterance.rate = 0.95;
      utterance.pitch = 1.0;

      utterance.onstart = () => {
        if (!isCancelledRef.current) {
          setIsSpeaking(true);
          setIsPaused(false);
          if (onStart) onStart();
        }
      };

      utterance.onend = () => {
        if (!isCancelledRef.current) {
          setIsSpeaking(false);
          setIsPaused(false);
          currentUtteranceRef.current = null;
          if (onEnd) onEnd();
        }
      };

      utterance.onerror = (e) => {
        console.warn('[SpeechSynthesis] Utterance error:', e.error, e);
        if (e.error !== 'interrupted' && e.error !== 'canceled') {
          setIsSpeaking(false);
          setIsPaused(false);
          if (!isCancelledRef.current && onError) onError(e);
        }
      };

      currentUtteranceRef.current = utterance;

      setTimeout(() => {
        if (!isCancelledRef.current && typeof window !== 'undefined' && 'speechSynthesis' in window) {
          window.speechSynthesis.speak(utterance);
        }
      }, 15);
    } catch (err) {
      console.error('[SpeechSynthesis] Exception:', err);
      setIsSpeaking(false);
      setIsPaused(false);
      if (onError) onError(err);
    }
  }, [isSupported, defaultLanguage, cancel, getVoiceForLanguage, onStart, onEnd, onError]);

  const pause = useCallback(() => {
    if (!isSupported) return;
    try {
      window.speechSynthesis.pause();
    } catch {
      // Ignore
    }
    setIsPaused(true);
  }, [isSupported]);

  const resume = useCallback(() => {
    if (!isSupported) return;
    try {
      window.speechSynthesis.resume();
    } catch {
      // Ignore
    }
    setIsPaused(false);
  }, [isSupported]);

  // Cancel on unmount
  useEffect(() => {
    return () => {
      cancel();
    };
  }, [cancel]);

  return {
    isSupported,
    isSpeaking,
    isPaused,
    voices,
    speak,
    pause,
    resume,
    cancel,
  };
}
