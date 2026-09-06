import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FeedbackModal } from './FeedbackModal';

describe('FeedbackModal', () => {
  afterEach(() => {
    cleanup();
  });
  it('does not render when isOpen is false', () => {
    const { container } = render(<FeedbackModal isOpen={false} onClose={() => {}} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders modal dialog with star ratings and category chips when open', () => {
    render(<FeedbackModal isOpen={true} onClose={() => {}} />);
    expect(screen.getByRole('dialog', { name: /feedback/i })).toBeInTheDocument();
    expect(screen.getByText('Share Feedback')).toBeInTheDocument();
    expect(screen.getByText('Voice Assistant')).toBeInTheDocument();
  });

  it('allows selecting category and rating, then submits feedback', () => {
    const handleClose = vi.fn();
    render(<FeedbackModal isOpen={true} onClose={handleClose} />);

    // Select category
    const voiceChip = screen.getByText('Voice Assistant');
    fireEvent.click(voiceChip);
    expect(voiceChip).toHaveClass('active');

    // Select 4 star rating
    const star4 = screen.getByRole('button', { name: /4 star/i });
    fireEvent.click(star4);

    // Enter comment
    const textarea = screen.getByLabelText(/your feedback or suggestion/i);
    fireEvent.change(textarea, { target: { value: 'Great multilingual voice recognition!' } });

    // Submit form
    const submitBtn = screen.getByRole('button', { name: /submit feedback/i });
    fireEvent.click(submitBtn);

    expect(screen.getByText('Thank You!')).toBeInTheDocument();
  });
});
