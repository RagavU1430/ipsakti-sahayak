import React, { useState, useRef, useEffect } from 'react';
import type { ProductReadinessResponse, Language } from '../api/types';
import { chatFormulationReport } from '../api/formulations';
import { FormattedText } from './FormattedText';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

interface FormulationReportChatbotProps {
  result: ProductReadinessResponse;
  language: Language;
  onViewFullReport?: () => void;
}

export function FormulationReportChatbot({
  result,
  language = 'en',
  onViewFullReport,
}: FormulationReportChatbotProps) {
  const [messages, setMessages] = useState<Message[]>(() => [
    {
      id: 'welcome',
      role: 'assistant',
      content: getInitialWelcome(language, result),
      timestamp: formatTime(new Date()),
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const chatScrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (messages.length > 1 && chatScrollRef.current) {
      chatScrollRef.current.scrollTop = chatScrollRef.current.scrollHeight;
    }
  }, [messages, loading]);

  const quickPrompts = getQuickPrompts(language);

  async function handleSend(queryText?: string) {
    const textToSend = (queryText ?? input).trim();
    if (!textToSend || loading) return;

    const userMessage: Message = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: textToSend,
      timestamp: formatTime(new Date()),
    };

    setMessages((prev) => [...prev, userMessage]);
    if (!queryText) setInput('');
    setLoading(true);

    try {
      const response = await chatFormulationReport({
        message: textToSend,
        reportContext: result.report,
        language,
      });

      const assistantMessage: Message = {
        id: `asst-${Date.now()}`,
        role: 'assistant',
        content: response.reply,
        timestamp: formatTime(new Date()),
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch {
      // Intelligent client-side fallback using structured response
      const fallbackContent = generateLocalSummaryReply(textToSend, result, language);
      const assistantMessage: Message = {
        id: `asst-${Date.now()}`,
        role: 'assistant',
        content: fallbackContent,
        timestamp: formatTime(new Date()),
      };
      setMessages((prev) => [...prev, assistantMessage]);
    } finally {
      setLoading(false);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  function handleCopy(id: string, text: string) {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  }

  const missingGaps = (result.gaps || []).filter((g) => g.status === 'MISSING');

  return (
    <div className="report-chatbot-container">
      {/* 1. Scrollable Chat Body: Executive Briefing Card + Messages Thread */}
      <div className="report-chat-history" ref={chatScrollRef}>
        {/* Instant Executive Briefing Card */}
        <div className="report-briefing-card">
          <div className="briefing-card-header">
            <span className="material-symbols-outlined briefing-icon">auto_awesome</span>
            <div>
              <h4 className="briefing-title">
                {language === 'ta'
                  ? 'ஆயுர்வேத ஒழுங்குமுறை சுருக்கம் (Executive Summary)'
                  : language === 'te'
                  ? 'ఆయుర్వేద నియంత్రణ సారాంశం (Executive Summary)'
                  : 'Executive Regulatory Assessment Summary'}
              </h4>
              <p className="briefing-subtitle">
                {language === 'ta'
                  ? 'அறிக்கையின் முக்கிய அம்சங்கள் கீழே சுருக்கமாக வழங்கப்பட்டுள்ளன'
                  : language === 'te'
                  ? 'నివేదిక యొక్క ముఖ్యమైన ముఖ్యాంశాలు క్రింద ఇవ్వబడ్డాయి'
                  : 'Key regulatory findings and statutory guidance summarized for immediate decision-making'}
              </p>
            </div>
          </div>

          <div className="briefing-grid">
            {/* Box 1: Category & Route */}
            <div className="briefing-box">
              <span className="briefing-box-label">
                <span className="material-symbols-outlined">category</span>
                {language === 'ta' ? 'தயாரிப்பு வகை & பாதை' : language === 'te' ? 'ఉత్పత్తి వర్గం' : 'Category & Pathway'}
              </span>
              <div className="briefing-box-value">{result.classification?.category?.replace(/_/g, ' ') || 'AYUSH'}</div>
              <div className="briefing-box-subtext">{result.regulatory?.pathway?.replace(/_/g, ' ') || 'State AYUSH Licensing'}</div>
            </div>

            {/* Box 2: Missing Documents */}
            <div className="briefing-box warning">
              <span className="briefing-box-label">
                <span className="material-symbols-outlined">warning</span>
                {language === 'ta' ? 'விடுபட்ட ஆவணங்கள்' : language === 'te' ? 'తప్పిపోయిన పత్రాలు' : 'Document Gaps'}
              </span>
              <div className="briefing-box-value">
                {missingGaps.length > 0
                  ? `${missingGaps.length} ${language === 'ta' ? 'விடுபட்டுள்ளன' : language === 'te' ? 'తప్పిపోయాయి' : 'Missing'}`
                  : language === 'ta' ? 'அனைத்தும் உள்ளன' : language === 'te' ? 'అన్నీ ఉన్నాయి' : 'Complete'}
              </div>
              <div className="briefing-box-subtext">
                {missingGaps.length > 0
                  ? missingGaps.map((g) => g.documentArea).slice(0, 2).join(', ') + (missingGaps.length > 2 ? '...' : '')
                  : 'Schedule T & testing available'}
              </div>
            </div>

            {/* Box 3: Traditional Knowledge & Section 3(p) */}
            <div className="briefing-box">
              <span className="briefing-box-label">
                <span className="material-symbols-outlined">menu_book</span>
                {language === 'ta' ? 'பாரம்பரிய அறிவு / காப்புரிமை' : language === 'te' ? 'సాంప్రదాయ జ్ఞానం' : 'TK / Patent Feasibility'}
              </span>
              <div className="briefing-box-value">
                {result.traditionalKnowledge?.isClassicalFormulation
                  ? (language === 'ta' ? 'கிளாசிக்கல் சூத்திரம்' : language === 'te' ? 'క్లాసికల్ ఫార్ములేషన్' : 'Classical Formulation')
                  : (language === 'ta' ? 'தனியுரிமை தயாரிப்பு' : language === 'te' ? 'ప్రొప్రైటరీ கலயிக' : 'Proprietary Blend')}
              </div>
              <div className="briefing-box-subtext">
                {language === 'ta'
                  ? 'பிரிவு 3(p)-இன் கீழ் தனியாக காப்புரிமை பெற முடியாது'
                  : language === 'te'
                  ? 'సెక్షన్ 3(p) ప్రకారం నేరుగా పేటెంట్ లభించదు'
                  : 'Section 3(p) excludes traditional botanical knowledge'}
              </div>
            </div>

            {/* Box 4: Top Action Item */}
            <div className="briefing-box action">
              <span className="briefing-box-label">
                <span className="material-symbols-outlined">arrow_forward</span>
                {language === 'ta' ? 'அடுத்த உடனடி நடவடிக்கை' : language === 'te' ? 'తక్షణ చర్య' : 'Immediate Next Action'}
              </span>
              <div className="briefing-box-value">
                {language === 'ta' ? 'ஆய்வக சோதனை & உரிமம்' : language === 'te' ? 'లైసెన్స్ దాఖలు' : 'Regulatory Filing'}
              </div>
              <div className="briefing-box-subtext">
                {result.nextSteps?.[0]
                  ? result.nextSteps[0].slice(0, 65) + '...'
                  : 'Submit Form 1 to State Licensing Authority'}
              </div>
            </div>
          </div>
        </div>

        {/* Conversation Messages Thread */}
        {messages.map((msg) => (
          <div key={msg.id} className={`report-chat-bubble-turn ${msg.role}`}>
            <div className="chat-bubble-avatar">
              {msg.role === 'assistant' ? (
                <span className="material-symbols-outlined">smart_toy</span>
              ) : (
                <span className="material-symbols-outlined">person</span>
              )}
            </div>
            <div className="chat-bubble-content-wrap">
              <div className={`chat-bubble-text ${msg.role}`}>
                <FormattedText content={msg.content} />
              </div>
              <div className="chat-bubble-meta">
                <span className="chat-bubble-time">{msg.timestamp}</span>
                {msg.role === 'assistant' && (
                  <button
                    type="button"
                    className="chat-copy-action"
                    onClick={() => handleCopy(msg.id, msg.content)}
                    title="Copy response"
                  >
                    <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>
                      {copiedId === msg.id ? 'check' : 'content_copy'}
                    </span>
                    <span>{copiedId === msg.id ? 'Copied' : 'Copy'}</span>
                  </button>
                )}
              </div>
            </div>
          </div>
        ))}

        {loading && (
          <div className="report-chat-bubble-turn assistant">
            <div className="chat-bubble-avatar">
              <span className="material-symbols-outlined">smart_toy</span>
            </div>
            <div className="chat-bubble-content-wrap">
              <div className="chat-thinking-indicator">
                <span className="thinking-pulse-dot" />
                <span>
                  {language === 'ta'
                    ? 'அறிக்கையை பகுப்பாய்வு செய்கிறது...'
                    : language === 'te'
                    ? 'నివేదికను విశ్లేషిస్తోంది...'
                    : 'Analyzing 17-section assessment report...'}
                </span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 2. Pinned Bottom Footer: Chips Row + Composer */}
      <div className="report-chatbot-footer">
        <div className="report-chat-chips-row">
          <span className="chat-chips-label">
            <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>tips_and_updates</span>
            {language === 'ta' ? 'விரைவு கேள்விகள்:' : language === 'te' ? 'త్వరిత ప్రశ్నలు:' : 'Quick Questions:'}
          </span>
          <div className="chat-chips-list">
            {quickPrompts.map((p, idx) => (
              <button
                key={idx}
                type="button"
                className="chat-chip-btn"
                onClick={() => handleSend(p.prompt)}
                disabled={loading}
              >
                <span>{p.icon}</span>
                <span>{p.label}</span>
              </button>
            ))}
          </div>
        </div>

      {/* 4. Chat Input Composer & Full Report Link */}
      <div className="report-chat-composer-wrap">
        <form
          className="report-chat-composer-box"
          onSubmit={(e) => {
            e.preventDefault();
            handleSend();
          }}
        >
          <textarea
            ref={inputRef}
            className="report-chat-input"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
            placeholder={
              language === 'ta'
                ? 'இந்த அறிக்கையைப் பற்றி ஏதேனும் கேட்கவும் (எ.கா: காப்புரிமை பெற முடியுமா?)...'
                : language === 'te'
                ? 'ఈ నివేదిక గురించి ఏదైనా అడగండి (ఉదా: పేటెంట్ పొందవచ్చా?)...'
                : 'Ask anything about this assessment report (e.g. Can I patent this?)...'
            }
            disabled={loading}
          />
          <button
            type="submit"
            className={`report-chat-send-btn ${input.trim() ? 'active' : ''}`}
            disabled={loading || !input.trim()}
            title="Send (Enter)"
            aria-label="Send message"
          >
            <span className="material-symbols-outlined">arrow_upward</span>
          </button>
        </form>

        <div className="report-chat-bottom-bar">
          <span className="chat-hint">
            {language === 'ta'
              ? 'Enter அழுத்தினால் செய்தி அனுப்பப்படும் • Shift + Enter புதிய வரி'
              : language === 'te'
              ? 'Enter నొక్కితే సందేశం వెళుతుంది • Shift + Enter కొత్త లైన్'
              : 'Press Enter ↵ to send • Shift + Enter for new line'}
          </span>
          {onViewFullReport && (
            <button type="button" className="switch-full-report-link" onClick={onViewFullReport}>
              <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>article</span>
              <span>{language === 'ta' ? 'முழு 17-பிரிவு அறிக்கை காண்க →' : language === 'te' ? 'పూర్తి నివేదికను చూడండి →' : 'View Full 17-Section Audit Report →'}</span>
            </button>
          )}
        </div>
      </div>
    </div>
    </div>
  );
}

function getInitialWelcome(language: Language, result: ProductReadinessResponse): string {
  const productName = result.product?.name || 'Your formulation';
  switch (language) {
    case 'ta':
      return `### 👋 வணக்கம்! நான் உங்கள் ஒழுங்குமுறை அறிக்கை உதவியாளர் (Report Assistant)

**"${productName}"** தயாரிப்பிற்கான 17-பிரிவு மதிப்பீட்டு அறிக்கை தயார் செய்யப்பட்டுள்ளது.

இந்த அறிக்கை மிகவும் நீளமாகவும் சட்ட விதிமுறைகளுடன் இருப்பதால், உங்களுக்கு தேவையானதை எளிதாகப் புரிந்துகொள்ள நான் உதவுகிறேன்:
- ⚡ **அறிக்கையின் சுருக்கம்** (Executive Summary)
- 📄 **விடுபட்ட முக்கிய ஆவணங்கள்** (Missing Documents)
- 💡 **பிரிவு 3(p) காப்புரிமை சாத்தியக்கூறுகள்** (Patent Feasibility)
- 🌿 **தேசிய பல்லுயிர் ஆணைய (NBA) அனுமதி** (Biodiversity Requirements)

மேலே உள்ள விரைவு பட்டன்களைக் கிளிக் செய்யவும் அல்லது உங்கள் கேள்வியை கீழே தட்டச்சு செய்யவும்!`;

    case 'te':
      return `### 👋 నమస్కారం! నేను మీ నియంత్రణ నివేదిక సహాయకుడిని (Report Assistant)

**"${productName}"** ఉత్పత్తికి సంబంధించిన 17-విభాగాల మూల్యాంకన నివేదిక సిద్ధంగా ఉంది.

ఈ నివేదికను సులభంగా అర్థం చేసుకోవడానికి మరియు ప్రధాన అంశాలను తెలుసుకోవడానికి నేను మీకు సహాయం చేస్తాను:
- ⚡ **నివేదిక సారాంశం** (Executive Summary)
- 📄 **తప్పిపోయిన ముఖ్యమైన పత్రాలు** (Missing Documents)
- 💡 **సెక్షన్ 3(p) పేటెంట్ సాధ్యత** (Patent Feasibility)
- 🌿 **జీవవైవిధ్యం (NBA) అనుమతులు** (Biodiversity Requirements)

పై బటన్లను క్లిక్ చేయండి లేదా క్రింద మీ ప్రశ్నను టైప్ చేయండి!`;

    default:
      return `### 👋 Welcome to your Formulation Report Assistant!

The comprehensive 17-section assessment audit for **"${productName}"** is ready.

Since regulatory audit reports are dense and technical, I am here to help you quickly digest, interpret, and act on the findings:
- ⚡ **Instant Executive Summary**: Category, route, and risk level.
- 📄 **Missing Document Checklist**: Which statutory certificates you still need.
- 💡 **Patent Realities**: Section 3(p) traditional knowledge exclusions and IP options.
- 🌿 **Biodiversity & NBA**: Section 6 access and benefit-sharing rules.

Click any of the quick-action chips above or ask your own question below!`;
  }
}

function getQuickPrompts(language: Language): { icon: string; label: string; prompt: string }[] {
  switch (language) {
    case 'ta':
      return [
        { icon: '⚡', label: '3 வரிகளில் சுருக்கம்', prompt: 'இந்த அறிக்கையை 3 எளிய வரிகளில் சுருக்கவும்.' },
        { icon: '📄', label: 'விடுபட்ட ஆவணங்கள்', prompt: 'இந்த தயாரிப்புக்கு என்னென்ன ஆவணங்கள் விடுபட்டுள்ளன மற்றும் கட்டாயம் தேவை?' },
        { icon: '💡', label: 'காப்புரிமை சாத்தியமா?', prompt: 'இந்த ஆயுர்வேத தயாரிப்புக்கு இந்தியாவில் காப்புரிமை (Patent) பெற முடியுமா?' },
        { icon: '🌿', label: 'NBA அனுமதி தேவையா?', prompt: 'தேசிய பல்லுயிர் ஆணைய (NBA) பிரிவு 6 அனுமதி இந்த தயாரிப்புக்கு தேவையா?' },
        { icon: '📋', label: 'அடுத்த கட்ட நடவடிக்கை', prompt: 'உரிமம் பெறுவதற்கு நான் செய்ய வேண்டிய உடனடி அடுத்த கட்ட நடவடிக்கை என்ன?' },
      ];
    case 'te':
      return [
        { icon: '⚡', label: '3 వాక్యాలలో సారాంశం', prompt: 'ఈ నివేదికను 3 సాధారణ వాక్యాలలో సంగ్రహించండి.' },
        { icon: '📄', label: 'తప్పిపోయిన పత్రాలు', prompt: 'ఈ ఉత్పత్తికి ఏయే పత్రాలు తప్పిపోయాయి మరియు తప్పనిసరిగా కావాలి?' },
        { icon: '💡', label: 'పేటెంట్ సాధ్యమేనా?', prompt: 'ఈ ఆయుర్వేద ఉత్పత్తికి భారతదేశంలో పేటెంట్ పొందవచ్చా?' },
        { icon: '🌿', label: 'NBA అనుమతి అవసరమా?', prompt: 'జాతీయ జీవవైవిధ్య అథారిటీ (NBA) సెక్షన్ 6 అనుమతి అవసరమా?' },
        { icon: '📋', label: 'తదుపరి చర్యలు', prompt: 'లైసెన్స్ పొందడానికి నేను తీసుకోవలసిన తదుపరి చర్య ఏమిటి?' },
      ];
    default:
      return [
        { icon: '⚡', label: '3-Bullet Summary', prompt: 'Summarize this assessment report in 3 simple, clear bullets.' },
        { icon: '📄', label: 'Missing Documents', prompt: 'Which documents are marked missing or unverified in this report?' },
        { icon: '💡', label: 'Patent Feasibility', prompt: 'Can this formulation be patented in India under Section 3(p)?' },
        { icon: '🌿', label: 'NBA Approval', prompt: 'Do I need National Biodiversity Authority (NBA) Section 6 approval?' },
        { icon: '📋', label: 'Immediate Next Steps', prompt: 'What is the immediate next step before applying for an AYUSH license?' },
      ];
  }
}

function generateLocalSummaryReply(query: string, result: ProductReadinessResponse, language: Language): string {
  const lower = query.toLowerCase();
  const missing = (result.gaps || []).filter((g) => g.status === 'MISSING');

  if (lower.includes('patent') || lower.includes('3(p)') || lower.includes('காப்புரிமை') || lower.includes('పేటెంట్')) {
    return result.traditionalKnowledge?.patentImplication || 'Section 3(p) excludes classical traditional knowledge formulations from patentability in India.';
  }

  if (lower.includes('document') || lower.includes('missing') || lower.includes('ஆவணம்') || lower.includes('పత్రం')) {
    if (missing.length === 0) {
      return language === 'ta'
        ? '✅ விடுபட்ட முக்கிய ஆவணங்கள் எதுவும் இல்லை. அனைத்து அடிப்படை ஆவணங்களும் உள்ளன.'
        : language === 'te'
        ? '✅ తప్పిపోయిన పత్రాలు ఏవీ లేవు. ప్రాథమిక పత్రాలన్నీ ఉన్నాయి.'
        : '✅ No mandatory documents are missing. Core documentation requirements are satisfied.';
    }
    const docList = missing.map((m, i) => `${i + 1}. **${m.documentArea}** (${m.importance}): ${m.reason}`).join('\n');
    return language === 'ta'
      ? `### 📄 விடுபட்ட முக்கிய ஆவணங்கள்:\n${docList}`
      : language === 'te'
      ? `### 📄 తప్పిపోయిన పత్రాలు:\n${docList}`
      : `### 📄 Identified Document Gaps:\n${docList}`;
  }

  if (lower.includes('nba') || lower.includes('biodiversity') || lower.includes('பல்லுயிர்') || lower.includes('జీవవైవిధ్యం')) {
    return result.biodiversityAbs?.recommendation || result.biodiversityAbs?.nbaApprovalRequirement || 'Mandatory Section 6 approval required if Indian biological resources are utilized.';
  }

  if (lower.includes('next') || lower.includes('step') || lower.includes('நடவடிக்கை') || lower.includes('చర్య')) {
    const steps = (result.nextSteps || []).map((s, i) => `${i + 1}. ${s}`).join('\n');
    return language === 'ta'
      ? `### 📋 பரிந்துரைக்கப்பட்ட அடுத்த கட்ட நடவடிக்கைகள்:\n${steps}`
      : language === 'te'
      ? `### 📋 సిఫార్సు చేయబడిన తదుపరి చర్యలు:\n${steps}`
      : `### 📋 Recommended Next Action Steps:\n${steps}`;
  }

  // General summary
  const cat = result.classification?.category?.replace(/_/g, ' ') || 'AYUSH Formulation';
  const pathway = result.regulatory?.pathway?.replace(/_/g, ' ') || 'State AYUSH Licensing';
  const topStep = result.nextSteps?.[0] || 'Prepare regulatory submission dossier';

  switch (language) {
    case 'ta':
      return `### ⚡ அறிக்கையின் முக்கிய சுருக்கம்:
- **வகை மற்றும் பாதை**: இந்த தயாரிப்பு **${cat}** வகையைச் சேர்ந்தது. ஒழுங்குமுறை பாதை: **${pathway}**.
- **ஆவணங்கள் நிலை**: ${missing.length > 0 ? `${missing.length} ஆவணங்கள் விடுபட்டுள்ளன.` : 'அனைத்து ஆவணங்களும் உள்ளன.'}
- **காப்புரிமை (Patent) நிலை**: பிரிவு 3(p)-இன் கீழ் பாரம்பரிய சூத்திரங்களுக்கு இந்தியாவில் காப்புரிமை வழங்கப்படாது; வர்த்தக முத்திரை (Trademark) பதிவு செய்வது பாதுகாப்பானது.
- **அடுத்த நடவடிக்கை**: ${topStep}`;

    case 'te':
      return `### ⚡ నివేదిక యొక్క ముఖ్యాంశాలు:
- **ఉత్పత్తి వర్గం**: ఈ ఉత్పత్తి **${cat}** కిందకు వస్తుంది. నియంత్రణ మార్గం: **${pathway}**.
- **పత్రాల స్థితి**: ${missing.length > 0 ? `${missing.length} పత్రాలు సమర్పించాల్సి ఉంది.` : 'అన్ని పత్రాలు సమర్పించబడ్డాయి.'}
- **పేటెంట్ స్థితి**: సెక్షన్ 3(p) ప్రకారం సాంప్రదాయ సూత్రాలకు పేటెంట్ లభించదు; ట్రేడ్‌మార్క్ రక్షణ సిఫార్సు చేయబడింది.
- **తదుపరి చర్య**: ${topStep}`;

    default:
      return `### ⚡ Executive Assessment Digest:
- **Category & Route**: Assessed under **${cat}** requiring authorization through **${pathway}**.
- **Statutory Document Status**: ${missing.length > 0 ? `${missing.length} documents are currently missing or unverified.` : 'All primary document criteria satisfied.'}
- **Patent Eligibility**: Section 3(p) strictly bars traditional botanical knowledge from patent grants. Trademark protection is recommended.
- **Priority Next Step**: ${topStep}`;
  }
}

function formatTime(d: Date): string {
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
