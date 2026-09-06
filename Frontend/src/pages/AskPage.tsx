import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from 'react';
import { askQuestion } from '../api/questions';
import {
  createConversation,
  askInConversation,
  listConversations,
  getConversation,
  deleteConversation,
} from '../api/conversations';
import type { AuthHeaders } from '../api/client';
import type { Citation, Jurisdiction, Language, QuestionResponse, Source } from '../api/types';
import { EvidenceList, formatConfidence } from '../components/Evidence';
import { FormattedText } from '../components/FormattedText';
import { AudioPlayerBar } from '../components/AudioPlayerBar';
import { MicButton } from '../components/VoiceAssistantControls';
import { VoiceChatOverlay } from '../components/VoiceChatOverlay';
import { useSpeechRecognition } from '../hooks/useSpeechRecognition';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  confidence?: number | null;
  abstained?: boolean;
  jurisdiction?: Jurisdiction;
  language?: Language;
  citations?: Citation[];
  sources?: Source[];
  timestamp?: string;
  isDomainRag?: boolean;
}

interface ConversationItem {
  id: string;
  title: string;
  updated_at: string;
}

const SUGGESTED_PROMPTS = [
  {
    title: 'What is Section 3(p)?',
    desc: 'Patents Act traditional knowledge exclusions',
    query: 'What is Section 3(p) of the Patents Act?',
  },
  {
    title: 'What is traditional knowledge?',
    desc: 'TKDL, documentation & defensive protection',
    query: 'What is traditional knowledge?',
  },
  {
    title: 'How can an invention be protected?',
    desc: 'Patents, industrial designs & trade secrets',
    query: 'How can an invention be protected in India?',
  },
  {
    title: 'Explain Access and Benefit Sharing.',
    desc: 'National Biodiversity Authority (NBA) compliance',
    query: 'Explain Access and Benefit Sharing compliance in India.',
  },
];

function formatTime(dateStr?: string): string {
  if (!dateStr) {
    return new Intl.DateTimeFormat('en-US', { hour: '2-digit', minute: '2-digit', hour12: true }).format(new Date());
  }
  try {
    return new Intl.DateTimeFormat('en-US', { hour: '2-digit', minute: '2-digit', hour12: true }).format(new Date(dateStr));
  } catch {
    return '';
  }
}

export function AskPage({ auth }: { auth: AuthHeaders; signedIn: boolean }) {
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState('');
  const [jurisdiction, setJurisdiction] = useState<Jurisdiction>('INDIA');
  const [language, setLanguage] = useState<Language>('en');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedMessageId, setCopiedMessageId] = useState<string | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [isVoiceOverlayOpen, setIsVoiceOverlayOpen] = useState(false);

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const scrollBottomRef = useRef<HTMLDivElement>(null);

  // Load conversation list on mount
  useEffect(() => {
    loadConversationList();
  }, [auth]);

  async function loadConversationList() {
    try {
      const page = await listConversations(auth);
      if (page && page.items) {
        setConversations(page.items);
      }
    } catch {
      // Non-blocking fallback for local test mode
    }
  }

  // Auto-scroll on new messages or loading state
  useEffect(() => {
    if (scrollBottomRef.current && typeof scrollBottomRef.current.scrollIntoView === 'function') {
      scrollBottomRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, loading]);

  // Textarea auto-resize
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(Math.max(textareaRef.current.scrollHeight, 40), 180)}px`;
    }
  }, [question]);

  const { isListening, toggleListening, error: speechError, clearError } = useSpeechRecognition({
    language,
    onTranscriptChange: (text) => setQuestion(text),
    onFinalTranscript: (text) => {
      setQuestion(text);
      if (text.trim()) {
        executeAsk(text.trim());
      }
    },
  });

  async function startNewChat() {
    setActiveConversationId(null);
    setMessages([]);
    setError(null);
    setQuestion('');
    textareaRef.current?.focus();
  }

  async function selectConversation(convId: string) {
    if (activeConversationId === convId) return;
    setError(null);
    setActiveConversationId(convId);
    try {
      const detail = await getConversation(convId, auth);
      if (detail && detail.messages) {
        const loadedMessages: ChatMessage[] = detail.messages.map((m, idx) => ({
          id: m.id || `msg-${idx}`,
          role: m.role,
          content: m.content,
          confidence: m.confidence,
          abstained: m.abstained,
          jurisdiction: (m.jurisdiction as Jurisdiction) || jurisdiction,
          language: (m.language as Language) || language,
          citations: m.citations || [],
          sources: m.sources || [],
          timestamp: formatTime(m.created_at),
          isDomainRag: Boolean(m.citations && m.citations.length > 0),
        }));
        setMessages(loadedMessages);
      }
    } catch (err) {
      setError('Could not load conversation history.');
    }
  }

  async function handleDeleteConversation(e: React.MouseEvent, convId: string) {
    e.stopPropagation();
    if (!confirm('Delete this conversation?')) return;
    try {
      await deleteConversation(convId, auth);
      setConversations((prev) => prev.filter((c) => c.id !== convId));
      if (activeConversationId === convId) {
        startNewChat();
      }
    } catch {
      setError('Could not delete conversation.');
    }
  }

  async function executeAsk(queryText: string) {
    if (!queryText.trim() || loading) return;
    const userText = queryText.trim();
    setQuestion('');
    setError(null);
    setLoading(true);

    const userMessageId = `user-${Date.now()}`;
    const userMessage: ChatMessage = {
      id: userMessageId,
      role: 'user',
      content: userText,
      timestamp: formatTime(),
    };

    setMessages((prev) => [...prev, userMessage]);

    try {
      let currentConvId = activeConversationId;

      // If no active conversation, create one
      if (!currentConvId) {
        try {
          const newConv = await createConversation('New Conversation', auth);
          if (newConv && newConv.id) {
            currentConvId = newConv.id;
            setActiveConversationId(newConv.id);
          }
        } catch {
          // Fallback if conversation creation is unsupported
        }
      }

      // Try asking in conversation
      if (currentConvId) {
        try {
          const resp = await askInConversation(currentConvId, userText, jurisdiction, language, auth);
          const assistantMessage: ChatMessage = {
            id: resp.message_id || `asst-${Date.now()}`,
            role: 'assistant',
            content: resp.answer,
            confidence: resp.confidence,
            abstained: resp.abstained,
            jurisdiction: resp.jurisdiction || jurisdiction,
            language: resp.language || language,
            citations: resp.citations || [],
            sources: resp.sources || [],
            timestamp: formatTime(resp.created_at),
            isDomainRag: resp.route === 'RAG' || Boolean(resp.citations && resp.citations.length > 0),
          };
          setMessages((prev) => [...prev, assistantMessage]);
          loadConversationList();
          return;
        } catch {
          // If in-conversation ask fails, smoothly fall through to direct askQuestion
        }
      }

      // Direct askQuestion fallback (guarantees question always receives response)
      const directResp = await askQuestion({ question: userText, jurisdiction, language }, auth);
      const assistantMessage: ChatMessage = {
        id: `asst-${Date.now()}`,
        role: 'assistant',
        content: directResp.answer,
        confidence: directResp.confidence,
        abstained: directResp.abstained,
        jurisdiction: directResp.jurisdiction || jurisdiction,
        language: directResp.language || language,
        citations: directResp.citations || [],
        sources: directResp.sources || [],
        timestamp: formatTime(),
        isDomainRag: directResp.route === 'RAG' || Boolean(directResp.citations && directResp.citations.length > 0),
      };
      setMessages((prev) => [...prev, assistantMessage]);
      loadConversationList();
    } catch (err) {
      setError(
        "I'm having trouble retrieving the authoritative information needed to answer that right now. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }

  function handleSubmit(event?: FormEvent) {
    if (event) event.preventDefault();
    if (!question.trim() || loading) return;
    executeAsk(question);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  }

  function copyAnswer(msgId: string, text: string) {
    navigator.clipboard.writeText(text);
    setCopiedMessageId(msgId);
    setTimeout(() => setCopiedMessageId(null), 2000);
  }

  function retryLastQuestion() {
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i].role === 'user') {
        executeAsk(messages[i].content);
        break;
      }
    }
  }

  return (
    <div className="modern-chat-page">
      {/* 1. Conversation Sidebar */}
      <aside className={`chat-sidebar ${sidebarOpen ? '' : 'collapsed'}`} aria-label="Conversation History">
        <div className="chat-sidebar-header">
          <button className="new-chat-action-btn" type="button" onClick={startNewChat} title="Start New Conversation">
            <span className="material-symbols-outlined">add</span>
            <span>New Chat</span>
          </button>
        </div>

        <div className="chat-sidebar-title">Recent Conversations</div>

        <div className="chat-history-scroll" role="navigation" aria-label="Past chats">
          {conversations.length === 0 ? (
            <div style={{ padding: '16px', color: '#94a3b8', fontSize: '0.85rem', textAlign: 'center' }}>
              No past chats yet.
            </div>
          ) : (
            conversations.map((c) => (
              <div
                key={c.id}
                role="button"
                tabIndex={0}
                className={`chat-history-item ${activeConversationId === c.id ? 'active' : ''}`}
                onClick={() => selectConversation(c.id)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    selectConversation(c.id);
                  }
                }}
                title={c.title}
              >
                <div className="chat-history-item-label">
                  <span className="material-symbols-outlined" style={{ fontSize: '18px', color: '#64748b' }}>
                    chat_bubble_outline
                  </span>
                  <span>{c.title}</span>
                </div>
                <button
                  type="button"
                  className="chat-delete-btn"
                  onClick={(e) => handleDeleteConversation(e, c.id)}
                  title="Delete conversation"
                  aria-label={`Delete ${c.title}`}
                >
                  <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>delete</span>
                </button>
              </div>
            ))
          )}
        </div>

        <div className="chat-sidebar-footer">
          <span>IP-SAKTI Chat</span>
          <span>v2.0</span>
        </div>
      </aside>

      {/* 2. Main Chat Area */}
      <section className="chat-main-stage">
        {/* Top Control Bar */}
        <header className="chat-topbar-controls">
          <div className="chat-topbar-left">
            <button
              type="button"
              className="chat-sidebar-toggle"
              onClick={() => setSidebarOpen((open) => !open)}
              title={sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar'}
              aria-label="Toggle conversation sidebar"
            >
              <span className="material-symbols-outlined">
                {sidebarOpen ? 'menu_open' : 'menu'}
              </span>
            </button>
            <div className="chat-assistant-title">
              <span aria-hidden="true">⚖️</span>
              <span>IP-SAKTI Sahayak</span>
            </div>
          </div>

          <div className="chat-topbar-right">
            <label className="chat-compact-select" title="Select Jurisdiction">
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

            <label className="chat-compact-select" title="Select Language">
              <span>🌐</span>
              <select
                value={language}
                onChange={(e) => setLanguage(e.target.value as Language)}
                aria-label="Language"
              >
                <option value="en">English</option>
                <option value="hi">हिन्दी</option>
                <option value="ta">தமிழ்</option>
                <option value="te">తెలుగు</option>
                <option value="kn">ಕನ್ನಡ</option>
                <option value="ml">മലയാളം</option>
              </select>
            </label>

            <button
              type="button"
              className="live-voice-launch-btn compact"
              onClick={() => setIsVoiceOverlayOpen(true)}
              title="Launch Live Voice Assistant"
            >
              <span className="live-voice-pulse-dot" />
              <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>graphic_eq</span>
              <span>Voice</span>
            </button>
          </div>
        </header>

        {/* Scroll Area for Messages */}
        <div className="chat-scroll-area">
          <div className="chat-flow-wrapper">
            {/* 3. Empty Chat Experience (Welcome Hero) */}
            {messages.length === 0 && !loading && (
              <div className="chat-empty-hero">
                <span className="chat-empty-icon" aria-hidden="true">⚖️</span>
                <h1 className="chat-empty-title">IP-SAKTI Sahayak</h1>
                <p className="chat-empty-subtitle">
                  Your AI assistant for Intellectual Property, Traditional Knowledge, Ayurveda and related regulatory information.
                </p>

                <div className="chat-suggestions-grid">
                  {SUGGESTED_PROMPTS.map((prompt) => (
                    <button
                      key={prompt.title}
                      type="button"
                      className="chat-suggestion-card"
                      onClick={() => executeAsk(prompt.query)}
                    >
                      <strong>{prompt.title}</strong>
                      <span>{prompt.desc}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* 4. Active Chat Messages */}
            {messages.map((msg) => (
              <div key={msg.id} className={`chat-turn ${msg.role}`}>
                <div className="chat-turn-avatar" aria-hidden="true">
                  {msg.role === 'user' ? '👤' : '⚖️'}
                </div>
                <div className="chat-turn-body">
                  <div className={`chat-bubble ${msg.role}`}>
                    {/* Trust row for evidence-backed answers */}
                    {msg.role === 'assistant' && msg.isDomainRag ? (
                      <div className="reply-trust-row" style={{ marginBottom: '8px' }}>
                        <span className={`reply-trust-badge ${msg.abstained ? 'warning' : 'success'}`}>
                          {msg.abstained ? '⚠️ Insufficient authoritative evidence' : '✅ Evidence-backed'}
                        </span>
                        {typeof msg.confidence === 'number' ? (
                          <span className="reply-confidence">{formatConfidence(msg.confidence)}</span>
                        ) : null}
                      </div>
                    ) : null}

                    {/* Formatted Content */}
                    <FormattedText content={msg.content} />

                    {/* Sources & Citations Section for RAG responses */}
                    {msg.role === 'assistant' && (msg.citations?.length || msg.sources?.length) ? (
                      <div style={{ marginTop: '14px', paddingTop: '12px', borderTop: '1px solid #e2e8f0' }}>
                        <EvidenceList citations={msg.citations} sources={msg.sources} />
                      </div>
                    ) : null}

                    {/* Audio Player Bar */}
                    {msg.role === 'assistant' ? (
                      <div style={{ marginTop: '10px' }}>
                          <AudioPlayerBar
                            text={msg.content}
                            language={msg.language || language}
                            label="Assistant answer audio"
                            auth={auth}
                        />
                      </div>
                    ) : null}
                  </div>

                  {/* Assistant Message Action Bar */}
                  {msg.role === 'assistant' ? (
                    <div className="chat-action-bar">
                      <button
                        type="button"
                        className={`chat-action-btn ${copiedMessageId === msg.id ? 'copied' : ''}`}
                        onClick={() => copyAnswer(msg.id, msg.content)}
                        title="Copy answer"
                      >
                        <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>
                          {copiedMessageId === msg.id ? 'check' : 'content_copy'}
                        </span>
                        <span>{copiedMessageId === msg.id ? 'Copied' : 'Copy'}</span>
                      </button>
                      <button
                        type="button"
                        className="chat-action-btn"
                        onClick={retryLastQuestion}
                        title="Retry response"
                      >
                        <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>refresh</span>
                        <span>Retry</span>
                      </button>
                    </div>
                  ) : null}

                  <span className="chat-turn-time">{msg.timestamp}</span>
                </div>
              </div>
            ))}

            {/* 5. Thinking / Loading State */}
            {loading && (
              <div className="chat-turn assistant">
                <div className="chat-turn-avatar" aria-hidden="true">⚖️</div>
                <div className="chat-turn-body">
                  <div className="chat-thinking-indicator">
                    <span className="thinking-pulse-dot" />
                    <span>IP-SAKTI is thinking...</span>
                  </div>
                </div>
              </div>
            )}

            {/* 6. Error State */}
            {error && (
              <div className="notice error" style={{ margin: '8px 0', borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }} role="alert">
                <span>{error}</span>
                <button
                  type="button"
                  className="button secondary compact"
                  onClick={retryLastQuestion}
                  style={{ marginLeft: '12px' }}
                >
                  Retry
                </button>
              </div>
            )}

            <div ref={scrollBottomRef} />
          </div>
        </div>

        {/* 7. Bottom Composer */}
        <div className="chat-composer-outer">
          <form className="chat-composer-box" onSubmit={handleSubmit}>
            <textarea
              ref={textareaRef}
              className="chat-composer-textarea"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={handleKeyDown}
              rows={1}
              placeholder="Ask IP-SAKTI anything about patents, trademarks, AYUSH, or IP procedures..."
              aria-label="Ask IP-SAKTI anything"
              disabled={loading}
            />

            <div className="chat-composer-toolbar">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <MicButton
                  isListening={isListening}
                  onToggle={toggleListening}
                  disabled={loading}
                  language={language}
                  errorMessage={speechError}
                  onClearError={clearError}
                />
              </div>

              <button
                type="submit"
                className={`chat-send-action-btn ${question.trim() ? 'active' : ''}`}
                disabled={loading || !question.trim()}
                title="Send message (Enter)"
                aria-label="Send message"
              >
                <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>
                  arrow_upward
                </span>
              </button>
            </div>
          </form>

          <div className="chat-composer-footer-hint">
            Press <strong>Enter ↵</strong> to send, <strong>Shift + Enter</strong> for new line
          </div>
        </div>
      </section>

      {/* Voice V2 Overlay */}
      <VoiceChatOverlay
        isOpen={isVoiceOverlayOpen}
        onClose={(lastResp) => {
          setIsVoiceOverlayOpen(false);
          if (lastResp && lastResp.answer) {
            const assistantMsg: ChatMessage = {
              id: `voice-${Date.now()}`,
              role: 'assistant',
              content: lastResp.answer,
              confidence: lastResp.confidence,
              abstained: lastResp.abstained,
              jurisdiction: lastResp.jurisdiction || jurisdiction,
              language: lastResp.language || language,
              citations: lastResp.citations || [],
              sources: lastResp.sources || [],
              timestamp: formatTime(),
              isDomainRag: Boolean(lastResp.citations && lastResp.citations.length > 0),
            };
            setMessages((prev) => [...prev, assistantMsg]);
          }
        }}
        auth={auth}
        initialLanguage={language}
        initialJurisdiction={jurisdiction}
      />
    </div>
  );
}
