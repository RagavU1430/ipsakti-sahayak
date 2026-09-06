import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MicButton, SpeakButton, VoiceModeToggle } from './VoiceAssistantControls';
import { cleanTextForSpeech } from '../hooks/useSpeechSynthesis';
import { LANGUAGE_LOCALE_MAP } from '../hooks/useSpeechRecognition';

describe('VoiceAssistantControls & Hooks', () => {
  afterEach(() => {
    cleanup();
  });

  describe('Language Support', () => {
    it('supports all 6 official configured languages with accurate BCP-47 locales', () => {
      expect(LANGUAGE_LOCALE_MAP.en).toBe('en-IN');
      expect(LANGUAGE_LOCALE_MAP.hi).toBe('hi-IN');
      expect(LANGUAGE_LOCALE_MAP.ta).toBe('ta-IN');
      expect(LANGUAGE_LOCALE_MAP.te).toBe('te-IN');
      expect(LANGUAGE_LOCALE_MAP.kn).toBe('kn-IN');
      expect(LANGUAGE_LOCALE_MAP.ml).toBe('ml-IN');
    });
  });

  describe('cleanTextForSpeech', () => {
    it('strips markdown asterisks, headers, and URLs for natural voice synthesis', () => {
      const markdown = '### Overview\nHere is **Section 3(p)** of *The Patents Act, 1970*.\nVisit https://ipindia.gov.in for details.';
      const cleaned = cleanTextForSpeech(markdown);

      expect(cleaned).not.toContain('###');
      expect(cleaned).not.toContain('**');
      expect(cleaned).not.toContain('https://');
      expect(cleaned).toContain('Overview');
      expect(cleaned).toContain('Section 3(p)');
      expect(cleaned).toContain('The Patents Act, 1970');
    });
  });

  describe('MicButton', () => {
    it('renders microphone button and triggers toggle on click', () => {
      const handleToggle = vi.fn();
      render(<MicButton isListening={false} onToggle={handleToggle} language="hi" />);

      const btn = screen.getByRole('button', { name: /speak in hindi/i });
      expect(btn).toBeInTheDocument();
      fireEvent.click(btn);
      expect(handleToggle).toHaveBeenCalledTimes(1);
    });

    it('shows listening state with active styling', () => {
      render(<MicButton isListening={true} onToggle={() => {}} language="ta" />);
      const btn = screen.getByRole('button', { name: /stop recording voice/i });
      expect(btn).toHaveClass('listening');
    });
  });

  describe('SpeakButton', () => {
    it('renders listen button and toggles speech', () => {
      const handleToggle = vi.fn();
      render(<SpeakButton isSpeaking={false} onToggle={handleToggle} />);

      const btn = screen.getByRole('button', { name: /listen to answer/i });
      expect(btn).toBeInTheDocument();
      fireEvent.click(btn);
      expect(handleToggle).toHaveBeenCalledTimes(1);
    });

    it('shows stop state when speaking', () => {
      render(<SpeakButton isSpeaking={true} onToggle={() => {}} />);
      expect(screen.getByText('Stop')).toBeInTheDocument();
    });
  });

  describe('VoiceModeToggle', () => {
    it('toggles Speech-to-Speech mode', () => {
      const handleToggle = vi.fn();
      const { rerender } = render(<VoiceModeToggle enabled={false} onToggle={handleToggle} />);

      const btn = screen.getByRole('button', { name: /speech-to-speech off/i });
      expect(btn).toBeInTheDocument();
      fireEvent.click(btn);
      expect(handleToggle).toHaveBeenCalledTimes(1);

      rerender(<VoiceModeToggle enabled={true} onToggle={handleToggle} />);
      expect(screen.getByRole('button', { name: /speech-to-speech on/i })).toBeInTheDocument();
    });
  });
});
