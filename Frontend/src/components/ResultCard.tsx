import React from 'react';
import type { QuestionResponse } from '../api/types';
import { EvidenceList, formatConfidence } from './Evidence';
import { FormattedText } from './FormattedText';
import { AudioPlayerBar } from './AudioPlayerBar';

export const QuestionResult = React.memo(function QuestionResult({ result }: { result: QuestionResponse }) {
  const isAbstained = result.abstained;
  const isFallback = result.route === 'GENERAL' || result.answerType === 'general_fallback';
  const isEvidenceRoute = result.route === 'RAG' || (!result.route && !isFallback);

  return (
    <section className="reply-bubble-wrapper" aria-label="Answer">
      {/* Avatar badge */}
      <div className="reply-avatar">
        <span>⚖️</span>
      </div>

      {/* Chat bubble */}
      <div className={`reply-bubble ${isAbstained ? 'abstained' : isFallback ? 'fallback' : ''}`}>
        {/* Trust badge row */}
        <div className="reply-trust-row">
          <span className={`reply-trust-badge ${isAbstained ? 'warning' : isFallback ? 'neutral' : 'success'}`}>
            {isAbstained ? '⚠️ Insufficient evidence' : isFallback ? 'ℹ️ General information' : '✅ Evidence-backed'}
          </span>
          {typeof result.confidence === 'number' ? (
            <span className="reply-confidence">{formatConfidence(result.confidence)}</span>
          ) : null}
        </div>

        {/* Answer text */}
        <FormattedText className="reply-answer-text" content={result.answer} />

        {/* Meta pills */}
        <div className="reply-meta-pills">
          <span className="reply-meta-pill">📍 {result.jurisdiction}</span>
          <span className="reply-meta-pill">🌐 {result.language?.toUpperCase()}</span>
        </div>

        {/* Audio player */}
        <AudioPlayerBar
          text={result.answer}
          language={result.language}
          label="Answer audio playback"
        />

        {/* Citations & Evidence */}
        {isEvidenceRoute ? <EvidenceList citations={result.citations} sources={result.sources} /> : null}
      </div>
    </section>
  );
});
