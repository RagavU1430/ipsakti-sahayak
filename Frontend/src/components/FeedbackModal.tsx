import React, { useState } from 'react';

export interface FeedbackModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const CATEGORIES = [
  'General Feedback',
  'Legal Accuracy',
  'Voice Assistant',
  'Language / Translation',
  'UI & Usability',
  'Bug Report',
];

export function FeedbackModal({ isOpen, onClose }: FeedbackModalProps) {
  const [rating, setRating] = useState<number>(5);
  const [hoverRating, setHoverRating] = useState<number>(0);
  const [category, setCategory] = useState<string>('General Feedback');
  const [comment, setComment] = useState<string>('');
  const [email, setEmail] = useState<string>('');
  const [submitted, setSubmitted] = useState<boolean>(false);

  if (!isOpen) return null;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const feedbackData = {
      rating,
      category,
      comment,
      email,
      timestamp: new Date().toISOString(),
    };

    try {
      const existing = JSON.parse(localStorage.getItem('ipsakti_feedback') || '[]');
      existing.push(feedbackData);
      localStorage.setItem('ipsakti_feedback', JSON.stringify(existing));
    } catch {
      // Ignore
    }

    setSubmitted(true);
    setTimeout(() => {
      setSubmitted(false);
      setComment('');
      setEmail('');
      onClose();
    }, 1800);
  }

  return (
    <div className="feedback-modal-backdrop" role="dialog" aria-modal="true" aria-label="Feedback">
      <div className="feedback-modal-card">
        <header className="feedback-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '24px' }}>💬</span>
            <h2 style={{ margin: 0, fontSize: '1.25rem', color: 'var(--on-surface, #0f172a)' }}>Share Feedback</h2>
          </div>
          <button
            type="button"
            className="feedback-close-btn"
            onClick={onClose}
            aria-label="Close feedback modal"
          >
            ✕
          </button>
        </header>

        {submitted ? (
          <div className="feedback-success-state">
            <span className="success-icon" style={{ fontSize: '42px' }}>🎉</span>
            <h3 style={{ margin: '8px 0', color: '#166534' }}>Thank You!</h3>
            <p style={{ margin: 0, color: '#15803d', fontSize: '0.92rem' }}>
              Your feedback helps improve IP-SAKTI Sahayak for citizens and innovators across India.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="feedback-form">
            {/* Rating Stars */}
            <div className="feedback-field">
              <label style={{ display: 'block', marginBottom: '6px', fontWeight: 600, fontSize: '0.9rem' }}>
                How would you rate your experience?
              </label>
              <div className="star-rating-row">
                {[1, 2, 3, 4, 5].map((star) => (
                  <button
                    key={star}
                    type="button"
                    className={`star-btn ${star <= (hoverRating || rating) ? 'active' : ''}`}
                    onMouseEnter={() => setHoverRating(star)}
                    onMouseLeave={() => setHoverRating(0)}
                    onClick={() => setRating(star)}
                    aria-label={`${star} star`}
                  >
                    ★
                  </button>
                ))}
                <span className="rating-label">
                  {rating === 5 ? 'Excellent' : rating === 4 ? 'Good' : rating === 3 ? 'Average' : rating === 2 ? 'Needs Work' : 'Poor'}
                </span>
              </div>
            </div>

            {/* Category selection */}
            <div className="feedback-field">
              <label style={{ display: 'block', marginBottom: '6px', fontWeight: 600, fontSize: '0.9rem' }}>
                Category
              </label>
              <div className="category-chips">
                {CATEGORIES.map((cat) => (
                  <button
                    key={cat}
                    type="button"
                    className={`cat-chip ${category === cat ? 'active' : ''}`}
                    onClick={() => setCategory(cat)}
                  >
                    {cat}
                  </button>
                ))}
              </div>
            </div>

            {/* Comment Textarea */}
            <div className="feedback-field">
              <label htmlFor="feedback-comment" style={{ display: 'block', marginBottom: '6px', fontWeight: 600, fontSize: '0.9rem' }}>
                Your feedback or suggestion
              </label>
              <textarea
                id="feedback-comment"
                rows={3}
                className="feedback-textarea"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="What worked well? What could we improve?"
                required
              />
            </div>

            {/* Optional Email */}
            <div className="feedback-field">
              <label htmlFor="feedback-email" style={{ display: 'block', marginBottom: '6px', fontWeight: 600, fontSize: '0.9rem' }}>
                Email (optional)
              </label>
              <input
                id="feedback-email"
                type="email"
                className="feedback-input"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@example.com"
              />
            </div>

            <div className="feedback-actions">
              <button type="button" onClick={onClose} className="button secondary" style={{ padding: '8px 16px' }}>
                Cancel
              </button>
              <button type="submit" className="button primary" style={{ padding: '8px 20px' }}>
                Submit Feedback
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
