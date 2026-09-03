import { FormEvent, useState } from 'react';
import { analyzeTkOverlap } from '../api/tk';
import type { AuthHeaders } from '../api/client';
import type { Language, TkOverlapResponse } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { EvidenceList, formatConfidence } from '../components/Evidence';
import { LanguageSelect, TextArea } from '../components/FormControls';
import { LoadingSteps } from '../components/LoadingSteps';

export function TkOverlapPage({ auth }: { auth: AuthHeaders }) {
  const [description, setDescription] = useState('');
  const [language, setLanguage] = useState<Language>('en');
  const [result, setResult] = useState<TkOverlapResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!description.trim() || loading) return;
    setError(null);
    setResult(null);
    setLoading(true);
    try {
      setResult(await analyzeTkOverlap({ description: description.trim(), language }, auth));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page narrow-page">
      <div className="page-heading">
        <p className="eyebrow">Traditional knowledge intelligence</p>
        <h1>TK Overlap Analysis</h1>
        <p>
          Describe an invention, formulation, product, or process. IP-SAKTI checks the existing authoritative RAG corpus for
          potential traditional-knowledge overlap and returns citations where evidence is found.
        </p>
      </div>

      <form className="panel-form" onSubmit={submit}>
        <TextArea
          label="Describe your invention, formulation, product, or process"
          value={description}
          onChange={setDescription}
          rows={7}
          required
          placeholder="Example: A turmeric and neem herbal formulation for traditional Ayurvedic therapeutic use in India..."
        />
        <div className="form-grid two-column">
          <LanguageSelect value={language} onChange={setLanguage} />
        </div>
        <button className="button primary" disabled={loading || !description.trim()} type="submit">
          Analyze TK Overlap
        </button>
      </form>

      {loading ? <LoadingSteps /> : null}
      {error ? <ErrorNotice error={error} /> : null}
      {result ? <TkOverlapResult result={result} /> : null}
    </div>
  );
}

function TkOverlapResult({ result }: { result: TkOverlapResponse }) {
  const stateClass =
    result.abstained || result.classification === 'INSUFFICIENT_EVIDENCE'
      ? 'warning'
      : result.classification === 'NO_TK_OVERLAP_FOUND'
        ? 'neutral'
        : 'success';

  return (
    <section className="result-card" aria-label="TK overlap result">
      <div className={`trust-indicator ${stateClass}`}>
        <span aria-hidden="true">{stateClass === 'success' ? '✓' : stateClass === 'warning' ? '!' : 'i'}</span>
        {humanize(result.classification)}
      </div>

      <div className="result-section answer-block">
        <h2>Assessment</h2>
        <div className="answer-text" style={{ whiteSpace: 'pre-line' }}>{result.explanation}</div>
      </div>

      <div className="meta-row">
        <span><strong>Confidence</strong> {formatConfidence(result.confidence)}</span>
        <span><strong>Language</strong> {result.language}</span>
        <span><strong>Abstained</strong> {result.abstained ? 'Yes' : 'No'}</span>
      </div>

      <section className="result-section">
        <h3>Overlap types</h3>
        {result.overlap_types.length === 0 ? (
          <p className="muted">No specific overlap type was supported by retrieved evidence.</p>
        ) : (
          <ul className="source-list">
            {result.overlap_types.map((type) => <li key={type}>{humanize(type)}</li>)}
          </ul>
        )}
      </section>

      <section className="result-section">
        <h3>Recommendation</h3>
        <p>{result.recommendation}</p>
        <p className="muted">
          This is a system-generated, evidence-backed screening result. It is not an official government classification and
          does not determine patentability, ownership, infringement, or legal validity.
        </p>
      </section>

      <EvidenceList citations={result.citations} sources={result.sources} />
    </section>
  );
}

function humanize(value: string) {
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}
