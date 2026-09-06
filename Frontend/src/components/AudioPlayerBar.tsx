import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { AuthHeaders } from '../api/client';
import { synthesizeSpeech } from '../api/voice';
import type { Language } from '../api/types';
import { cleanTextForSpeech, useSpeechSynthesis } from '../hooks/useSpeechSynthesis';

export interface AudioPlayerBarProps {
  text: string;
  language?: Language;
  className?: string;
  estimatedSeconds?: number;
  label?: string;
  auth?: AuthHeaders;
}

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

export function AudioPlayerBar({
  text,
  language = 'en',
  className = '',
  estimatedSeconds: overrideSeconds,
  label = 'Audio playback',
  auth = {},
}: AudioPlayerBarProps) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const timerRef = useRef<number | null>(null);
  const serverAudioRef = useRef<HTMLAudioElement | null>(null);
  const serverAudioUrlRef = useRef<string | null>(null);
  const [isGeneratingAudio, setIsGeneratingAudio] = useState(false);

  const cleanedText = useMemo(() => cleanTextForSpeech(text), [text]);

  const totalSeconds = useMemo(() => {
    if (overrideSeconds && overrideSeconds > 0) return overrideSeconds;
    if (!cleanedText) return 5;

    // Different languages have different character-to-speech ratios.
    // Indian languages use fewer words (longer compound words / agglutinative)
    // so we estimate by character count with per-language rates.
    const charCount = cleanedText.length;
    const wordCount = cleanedText.split(/\s+/).filter(Boolean).length;

    // Characters-per-second spoken rate varies by script:
    const langRates: Record<string, number> = {
      en: 14,   // ~14 chars/sec for English
      hi: 10,   // Devanagari - ~10 chars/sec
      ta: 8,    // Tamil script - ~8 chars/sec (complex syllables)
      te: 9,    // Telugu script - ~9 chars/sec
      kn: 9,    // Kannada script - ~9 chars/sec
      ml: 8,    // Malayalam script - ~8 chars/sec (long words)
    };

    const rate = langRates[language] || 10;
    const estimateByChars = Math.ceil(charCount / rate);
    const estimateByWords = Math.ceil(wordCount / 2.0);

    // Use the larger of the two estimates for safety
    return Math.max(5, Math.max(estimateByChars, estimateByWords));
  }, [overrideSeconds, cleanedText, language]);

  const { speak, cancel } = useSpeechSynthesis({
    defaultLanguage: language,
    onEnd: () => {
      setIsPlaying(false);
      setElapsed(0);
    },
    onError: () => {
      setIsPlaying(false);
      setElapsed(0);
    },
  });

  const stopPlayback = useCallback(() => {
    cancel();
    serverAudioRef.current?.pause();
    setIsPlaying(false);
    setElapsed(0);
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }, [cancel]);

  // Reset playback if text or language changes
  useEffect(() => {
    stopPlayback();
    if (serverAudioUrlRef.current) {
      URL.revokeObjectURL(serverAudioUrlRef.current);
      serverAudioUrlRef.current = null;
    }
  }, [text, language, stopPlayback]);

  useEffect(() => () => {
    serverAudioRef.current?.pause();
    if (serverAudioUrlRef.current) URL.revokeObjectURL(serverAudioUrlRef.current);
  }, []);

  // Timer loop when playing
  useEffect(() => {
    if (isPlaying) {
      timerRef.current = window.setInterval(() => {
        setElapsed((prev) => {
          if (prev >= totalSeconds) {
            stopPlayback();
            return 0;
          }
          return prev + 1;
        });
      }, 1000);
    } else {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    }

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [isPlaying, totalSeconds, stopPlayback]);

  async function handleTogglePlay() {
    if (!cleanedText) return;

    if (isPlaying) {
      stopPlayback();
    } else if (language !== 'en' && auth) {
      setIsGeneratingAudio(true);
      try {
        const url = await synthesizeSpeech(cleanedText, language, auth);
        if (serverAudioUrlRef.current) URL.revokeObjectURL(serverAudioUrlRef.current);
        serverAudioUrlRef.current = url;
        const audio = new Audio(url);
        serverAudioRef.current = audio;
        audio.onended = () => {
          setIsPlaying(false);
          setElapsed(0);
        };
        audio.onerror = () => {
          setIsPlaying(false);
          setElapsed(0);
        };
        await audio.play();
        setIsPlaying(true);
      } catch {
        setIsPlaying(false);
      } finally {
        setIsGeneratingAudio(false);
      }
    } else {
      setIsPlaying(true);
      if (!isMuted) {
        speak(cleanedText, language);
      }
    }
  }

  function handleToggleMute() {
    if (!isMuted && isPlaying) {
      cancel();
      serverAudioRef.current?.pause();
      setIsPlaying(false);
    }
    setIsMuted((prev) => !prev);
  }

  function handleProgressBarClick(e: React.MouseEvent<HTMLDivElement>) {
    if (!isPlaying) {
      handleTogglePlay();
      return;
    }
    const rect = e.currentTarget.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const percent = Math.max(0, Math.min(1, clickX / rect.width));
    setElapsed(Math.round(percent * totalSeconds));
  }

  const progressPercent = Math.min(100, Math.max(0, Math.round((elapsed / (totalSeconds || 1)) * 100)));

  return (
    <div
      className={`interactive-audio-strip ${className}`}
      role="region"
      aria-label={label}
    >
      <button
        type="button"
        className="audio-play-btn"
        onClick={handleTogglePlay}
        title={isGeneratingAudio ? 'Preparing audio' : isPlaying ? 'Pause audio (Space)' : 'Play audio (Space)'}
        aria-label={isGeneratingAudio ? 'Preparing audio' : isPlaying ? 'Pause audio' : 'Play audio'}
        disabled={isGeneratingAudio}
      >
        <span className="material-symbols-outlined">
          {isPlaying ? 'pause' : 'play_arrow'}
        </span>
      </button>

      <span className="audio-timer" aria-live="off">
        {formatTime(elapsed)} / {formatTime(totalSeconds)}
      </span>

      <div
        className="audio-track-progress"
        onClick={handleProgressBarClick}
        title="Playback progress"
        role="progressbar"
        aria-valuenow={elapsed}
        aria-valuemin={0}
        aria-valuemax={totalSeconds}
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === ' ' || e.key === 'Enter') {
            e.preventDefault();
            handleTogglePlay();
          }
        }}
      >
        <div
          className="audio-track-bar"
          style={{ width: `${isPlaying ? progressPercent : 0}%` }}
        />
      </div>

      <button
        type="button"
        className="audio-mute-btn"
        onClick={handleToggleMute}
        title={isMuted ? 'Unmute audio' : 'Mute audio'}
        aria-label={isMuted ? 'Unmute audio' : 'Mute audio'}
      >
        <span className="material-symbols-outlined">
          {isMuted ? 'volume_off' : 'volume_up'}
        </span>
      </button>
    </div>
  );
}
