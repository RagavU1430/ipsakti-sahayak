import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { askVoice } from '../api/voice';
import { VoiceChatOverlay } from './VoiceChatOverlay';

const recorderMock = vi.hoisted(() => ({
  start: vi.fn().mockResolvedValue(undefined), stop: vi.fn(), clearError: vi.fn(),
  callback: null as null | ((value: { blob: Blob; durationMs: number; previewUrl: string }) => void),
}));

vi.mock('../hooks/useVoiceRecorder', () => ({
  useVoiceRecorder: (callback: typeof recorderMock.callback) => {
    recorderMock.callback = callback;
    return { isRecording: false, error: null, start: recorderMock.start, stop: recorderMock.stop, clearError: recorderMock.clearError };
  },
}));

vi.mock('../api/voice', () => ({ askVoice: vi.fn(), voiceAudioUrl: vi.fn(() => 'blob:answer') }));

describe('VoiceChatOverlay V2', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true });
    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
    window.HTMLMediaElement.prototype.pause = vi.fn();
  });
  afterEach(cleanup);

  it('stays absent while closed', () => {
    const { container } = render(<VoiceChatOverlay isOpen={false} onClose={() => {}} auth={{}} />);
    expect(container.firstChild).toBeNull();
  });

  it('shows all six supported languages and an explicit start action', () => {
    render(<VoiceChatOverlay isOpen onClose={() => {}} auth={{}} />);
    expect(screen.getAllByRole('radio')).toHaveLength(6);
    expect(screen.getByRole('button', { name: 'Start recording' })).toBeInTheDocument();
  });

  it('starts and stops the single MediaRecorder path', async () => {
    render(<VoiceChatOverlay isOpen onClose={() => {}} auth={{}} />);
    fireEvent.click(screen.getByRole('button', { name: 'Start recording' }));
    await waitFor(() => expect(recorderMock.start).toHaveBeenCalledOnce());
    fireEvent.click(screen.getByRole('button', { name: 'Stop and ask' }));
    expect(recorderMock.stop).toHaveBeenCalledWith(false);
  });

  it('renders transcript, answer metadata, and server-generated audio', async () => {
    vi.mocked(askVoice).mockResolvedValue({
      transcript: 'Hi', language: 'en', jurisdiction: 'INDIA', answer: 'Hello', answerType: 'general_fallback',
      route: 'GENERAL', confidence: null, citations: [], sources: [], abstained: false,
      audioBase64: 'AQID', audioMimeType: 'audio/wav', status: 'SUCCESS', latencyMs: 20,
    });
    render(<VoiceChatOverlay isOpen onClose={() => {}} auth={{ devUserId: 'tester' }} />);
    recorderMock.callback?.({ blob: new Blob(['audio'], { type: 'audio/webm' }), durationMs: 500, previewUrl: 'blob:question' });
    expect(await screen.findByText('Hi')).toBeInTheDocument();
    expect(screen.getByText('Hello')).toBeInTheDocument();
    expect(screen.getByText('GENERAL')).toBeInTheDocument();
    expect(screen.getByText('Answer audio')).toBeInTheDocument();
  });

  it('discards recording when closed', () => {
    const close = vi.fn();
    render(<VoiceChatOverlay isOpen onClose={close} auth={{}} />);
    fireEvent.click(screen.getByRole('button', { name: 'Close voice assistant' }));
    expect(recorderMock.stop).toHaveBeenCalledWith(true);
    expect(close).toHaveBeenCalledOnce();
  });
});
