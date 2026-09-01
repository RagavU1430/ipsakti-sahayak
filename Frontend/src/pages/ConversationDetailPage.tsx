import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import type { AuthHeaders } from '../api/client';
import { askInConversation, deleteConversation, getConversation } from '../api/conversations';
import type { ConversationDetail, Jurisdiction, Language } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { EvidenceList, formatConfidence } from '../components/Evidence';
import { JurisdictionSelect, LanguageSelect, TextArea } from '../components/FormControls';
import { LoadingSteps } from '../components/LoadingSteps';

export function ConversationDetailPage({ auth }: { auth: AuthHeaders }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const [conversation, setConversation] = useState<ConversationDetail | null>(null);
  const [question, setQuestion] = useState('');
  const [jurisdiction, setJurisdiction] = useState<Jurisdiction>('INDIA');
  const [language, setLanguage] = useState<Language>('en');
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    if (!id) return;
    let mounted = true;
    getConversation(id, auth)
      .then((detail) => { if (mounted) setConversation(detail); })
      .catch((err) => { if (mounted) setError(err); })
      .finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [auth, id]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!id || !question.trim()) return;
    setSending(true);
    setError(null);
    try {
      await askInConversation(id, question, jurisdiction, language, auth);
      setQuestion('');
      setConversation(await getConversation(id, auth));
    } catch (err) {
      setError(err);
    } finally {
      setSending(false);
    }
  }

  async function removeConversation() {
    if (!id || !confirm('Delete this conversation?')) return;
    await deleteConversation(id, auth);
    navigate('/history');
  }

  return (
    <div className="page narrow-page">
      <Link className="text-link" to="/history">← Back to history</Link>
      {loading ? <LoadingSteps label="Loading conversation" /> : null}
      {error ? <ErrorNotice error={error} /> : null}
      {conversation ? (
        <>
          <div className="page-heading split-heading">
            <div>
              <h1>{conversation.title}</h1>
              <p>Saved evidence-backed exchange.</p>
            </div>
            <button className="button secondary danger" type="button" onClick={removeConversation}>Delete conversation</button>
          </div>
          <section className="conversation-thread">
            {conversation.messages.map((message) => (
              <article className={`message ${message.role}`} key={message.id}>
                <p className="message-role">{message.role === 'user' ? 'User question' : 'Assistant answer'}</p>
                <p>{message.content}</p>
                {message.role === 'assistant' ? (
                  <>
                    <div className="meta-row">
                      <span><strong>Confidence</strong> {formatConfidence(message.confidence)}</span>
                      <span><strong>Type</strong> {message.response_type || 'n/a'}</span>
                    </div>
                    <EvidenceList citations={message.citations} sources={message.sources} />
                  </>
                ) : null}
              </article>
            ))}
          </section>
          <form className="claude-chat-container" onSubmit={submit}>
            <div className="claude-input-box">
              <textarea
                className="claude-textarea"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    submit(e);
                  }
                }}
                required
                rows={1}
                placeholder="Ask a follow-up question..."
                aria-label="New question"
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
                  disabled={sending || !question.trim()}
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
            </div>
          </form>
        </>
      ) : null}
    </div>
  );
}
