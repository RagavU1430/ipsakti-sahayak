import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AudioPlayerBar } from './AudioPlayerBar';
import { synthesizeSpeech } from '../api/voice';

vi.mock('../api/voice', () => ({ synthesizeSpeech: vi.fn() }));

describe('AudioPlayerBar', () => {
  beforeEach(() => {
    window.HTMLMediaElement.prototype.play = vi.fn().mockImplementation(() => Promise.resolve());
    window.HTMLMediaElement.prototype.pause = vi.fn();
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders audio player bar with play button, timer, progress bar, and mute button', () => {
    render(
      <AudioPlayerBar
        text="Section 3(p) of the Patents Act, 1970 excludes traditional knowledge from patentability."
        language="en"
      />
    );

    expect(screen.getByRole('button', { name: /play audio/i })).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /mute audio/i })).toBeInTheDocument();
    expect(screen.getByText(/0:00 \/ 0:\d\d/)).toBeInTheDocument();
  });

  it('toggles to pause button when clicked and toggles mute button', () => {
    render(
      <AudioPlayerBar
        text="This is a test message for audio playback."
        language="en"
      />
    );

    const playBtn = screen.getByRole('button', { name: /play audio/i });
    fireEvent.click(playBtn);

    // After clicking play, it should become pause button
    expect(screen.getByRole('button', { name: /pause audio/i })).toBeInTheDocument();

    // Mute button toggle
    const muteBtn = screen.getByRole('button', { name: /mute audio/i });
    fireEvent.click(muteBtn);
    expect(screen.getByRole('button', { name: /unmute audio/i })).toBeInTheDocument();
  });

  it('deduplicates concurrent Indic TTS and reuses generated audio for replay', async () => {
    vi.mocked(synthesizeSpeech).mockResolvedValue('blob:tamil-answer');
    render(<AudioPlayerBar text="வணக்கம்" language="ta" auth={{ devUserId: 'test-user' }} />);

    const play = screen.getByRole('button', { name: /play audio/i });
    fireEvent.click(play);
    fireEvent.click(play);

    await waitFor(() => expect(screen.getByRole('button', { name: /pause audio/i })).toBeInTheDocument());
    expect(synthesizeSpeech).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: /pause audio/i }));
    fireEvent.click(screen.getByRole('button', { name: /play audio/i }));
    await waitFor(() => expect(screen.getByRole('button', { name: /pause audio/i })).toBeInTheDocument());
    expect(synthesizeSpeech).toHaveBeenCalledTimes(1);
  });

  it('shows an explicit TTS failure instead of silently falling back', async () => {
    vi.mocked(synthesizeSpeech).mockRejectedValue(new Error('Answer audio is temporarily unavailable.'));
    render(<AudioPlayerBar text="നമസ്കാരം" language="ml" auth={{ devUserId: 'test-user' }} />);

    fireEvent.click(screen.getByRole('button', { name: /play audio/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('temporarily unavailable');
  });
});
