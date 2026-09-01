import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from 'react';
import { askQuestion } from '../api/questions';
import type { AuthHeaders } from '../api/client';
import type { Jurisdiction, Language, QuestionResponse } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { LoadingSteps } from '../components/LoadingSteps';
import { QuestionResult } from '../components/ResultCard';

export function AskPage({ auth, signedIn }: { auth: AuthHeaders; signedIn: boolean }) {
  const [question, setQuestion] = useState('');
  const [jurisdiction, setJurisdiction] = useState<Jurisdiction>('INDIA');
  const [language, setLanguage] = useState<Language>('en');
  const [result, setResult] = useState<QuestionResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(Math.max(textareaRef.current.scrollHeight, 46), 180)}px`;
    }
  }, [question]);

  async function submit(event?: FormEvent) {
    if (event) event.preventDefault();
    if (!question.trim() || loading) return;
    setError(null);
    setResult(null);
    setLoading(true);
    try {
      setResult(await askQuestion({ question: question.trim(), jurisdiction, language }, auth));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  }

  return (
    <div className="page narrow-page">
      <div className="page-heading">
        <p className="eyebrow">IP-SAKTI Sahayak</p>
        <h1>Ask an intellectual property question</h1>
        <p>Ask about patents, trademarks, copyright, GI, biodiversity, traditional knowledge, or IP procedures.</p>
      </div>

      <form className="claude-chat-container" onSubmit={submit}>
        <div className="claude-input-box">
          <textarea
            ref={textareaRef}
            className="claude-textarea"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={handleKeyDown}
            required
            rows={1}
            placeholder="Ask anything about patents, trademarks, AYUSH, or IP procedures..."
            aria-label="Question"
          />
          <div className="claude-toolbar">
            <div className="claude-chips">
              <label className="claude-pill-select" title="Select Jurisdiction">
                <span>📍</span>
                <select
                  value={jurisdiction}
                  onChange={(e) => setJurisdiction(e.target.value as Jurisdiction)}
                  aria-label="Jurisdiction"
                >
                  <option value="INDIA">India (IPO / TKDL / NBA)</option>
                  <option value="INTERNATIONAL">International (WIPO / PCT)</option>
                  <option value="AUTO">Auto Detect</option>
                </select>
              </label>

              <label className="claude-pill-select" title="Select Language">
                <span>🌐</span>
                <select
                  value={language}
                  onChange={(e) => setLanguage(e.target.value as Language)}
                  aria-label="Language"
                >
                  <option value="en">English</option>
                  <option value="hi">हिंदी (Hindi)</option>
                  <option value="ta">தமிழ் (Tamil)</option>
                </select>
              </label>
            </div>

            <button
              className={`claude-send-btn ${question.trim() ? 'active' : ''}`}
              disabled={loading || !question.trim()}
              type="submit"
              aria-label="Send question"
              title="Send (Enter)"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="12" y1="19" x2="12" y2="5" />
                <polyline points="5 12 12 5 19 12" />
              </svg>
            </button>
          </div>
        </div>
        <div className="claude-footer-hint">
          <span>Press <strong>Enter ↵</strong> to send, <strong>Shift + Enter</strong> for new line</span>
          {signedIn ? <span className="signed-in-pill">● Signed in (History active)</span> : <span className="muted">Sign in to save history</span>}
        </div>
      </form>

      {!result && !loading && !error ? (
        <div className="suggested-queries">
          <p className="suggested-title">Try asking:</p>
          <div className="suggested-chips">
            <button type="button" onClick={() => setQuestion("Can I patent an Ayurvedic herbal formulation in India?")}>
              🌿 Patenting Ayurvedic formulation
            </button>
            <button type="button" onClick={() => setQuestion("What are the grounds for trademark refusal under Section 9 & 11?")}>
              ™️ Trademark refusal grounds
            </button>
            <button type="button" onClick={() => setQuestion("What is the term of a patent in India and when do renewal fees start?")}>
              📜 Patent duration & renewal
            </button>
            <button type="button" onClick={() => setQuestion("What is your work and how can you help me?")}>
              🤖 What is your work?
            </button>
          </div>
        </div>
      ) : null}

      {loading ? <LoadingSteps /> : null}
      {error ? <ErrorNotice error={error} /> : null}
      {result ? <QuestionResult result={result} /> : null}
    </div>
  );
}
