import type { QuestionResponse } from '../api/types';
import { EvidenceList, formatConfidence } from './Evidence';

export function QuestionResult({ result }: { result: QuestionResponse }) {
  const indicator =
    result.abstained ? 'Insufficient authoritative evidence' :
    result.answerType === 'general_fallback' ? 'General information' :
    'Evidence-backed answer';

  return (
    <section className="result-card" aria-label="Answer">
      <div className={`trust-indicator ${result.abstained ? 'warning' : result.answerType === 'general_fallback' ? 'neutral' : 'success'}`}>
        <span aria-hidden="true">{result.abstained ? '!' : result.answerType === 'general_fallback' ? 'i' : '✓'}</span>
        {indicator}
      </div>
      <div className="result-section answer-block">
        <h2>Answer</h2>
        <div className="answer-text" style={{ whiteSpace: 'pre-line' }}>{result.answer}</div>
      </div>
      <div className="meta-row">
        <span><strong>Confidence</strong> {formatConfidence(result.confidence)}</span>
        <span><strong>Jurisdiction</strong> {result.jurisdiction}</span>
        <span><strong>Language</strong> {result.language}</span>
      </div>
      <EvidenceList citations={result.citations} sources={result.sources} />
    </section>
  );
}
