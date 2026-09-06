import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AudioPlayerBar } from './AudioPlayerBar';

describe('AudioPlayerBar', () => {
  beforeEach(() => {
    window.HTMLMediaElement.prototype.play = vi.fn().mockImplementation(() => Promise.resolve());
    window.HTMLMediaElement.prototype.pause = vi.fn();
  });

  afterEach(() => {
    cleanup();
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
});
