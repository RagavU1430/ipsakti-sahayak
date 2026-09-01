import { FormEvent, useState } from 'react';
import type { AuthHeaders } from '../api/client';
import { analyzeRegulatory } from '../api/regulatory';
import type { Jurisdiction, Language, RegulatoryAnalysisResponse } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { CheckboxField, JurisdictionSelect, LanguageSelect, TextArea, TextField } from '../components/FormControls';
import { EvidenceList, formatConfidence } from '../components/Evidence';
import { LoadingSteps } from '../components/LoadingSteps';

export function RegulatoryPage({ auth }: { auth: AuthHeaders }) {
  const [productName, setProductName] = useState('');
  const [ingredients, setIngredients] = useState('');
  const [intendedUse, setIntendedUse] = useState('');
  const [claims, setClaims] = useState('');
  const [resourceOrigin, setResourceOrigin] = useState('');
  const [traditionalKnowledge, setTraditionalKnowledge] = useState(false);
  const [knownIngredients, setKnownIngredients] = useState(false);
  const [biologicalResources, setBiologicalResources] = useState(false);
  const [geneticResources, setGeneticResources] = useState(false);
  const [jurisdiction, setJurisdiction] = useState<Jurisdiction>('INDIA');
  const [language, setLanguage] = useState<Language>('en');
  const [result, setResult] = useState<RegulatoryAnalysisResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setResult(null);
    setLoading(true);
    try {
      setResult(await analyzeRegulatory({
        productName,
        ingredients: splitItems(ingredients),
        intendedUse,
        claims: splitItems(claims),
        resourceOrigin,
        targetMarket: jurisdiction === 'INTERNATIONAL' ? 'International' : 'India',
        traditionalKnowledge,
        knownIngredients,
        biologicalResources,
        geneticResources,
        jurisdiction,
        language,
      }, auth));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  function handleClear() {
    setProductName('');
    setIngredients('');
    setIntendedUse('');
    setClaims('');
    setResourceOrigin('');
    setTraditionalKnowledge(false);
    setKnownIngredients(false);
    setBiologicalResources(false);
    setGeneticResources(false);
    setResult(null);
    setError(null);
  }

  return (
    <div className="page wide-page">
      <div className="page-heading">
        <h1>Regulatory Analysis</h1>
        <p>Review traditional knowledge, Section 3(p)/3(e) exclusions, ABS compliance, and GRATK treaty requirements.</p>
      </div>

      <div className="form-and-results-grid">
        {/* Left Column: Form */}
        <div>
          <form className="panel form-grid" onSubmit={submit}>
            <TextField label="Product / invention description" value={productName} onChange={setProductName} required />
            <TextArea label="Ingredients / biological resources" value={ingredients} onChange={setIngredients} placeholder="List herbs, plants, organisms..." rows={3} />
            <div className="control-row">
              <div style={{ flex: 1 }}>
                <TextField label="Intended use" value={intendedUse} onChange={setIntendedUse} placeholder="Therapeutic or commercial use..." />
              </div>
              <div style={{ flex: 1 }}>
                <TextField label="Source / origin" value={resourceOrigin} onChange={setResourceOrigin} placeholder="India (State) or international..." />
              </div>
            </div>
            <TextArea label="Claims & Novelty Features" value={claims} onChange={setClaims} placeholder="Synergistic effect, improved extraction, novel process..." rows={3} />

            <div style={{ background: 'var(--surface-container-low)', padding: '14px', borderRadius: '4px', border: '1px solid var(--outline-variant)' }}>
              <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--secondary)', display: 'block', marginBottom: '8px', textTransform: 'uppercase' }}>
                Assessment Triggers
              </span>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '8px' }}>
                <CheckboxField label="Section 3(p) / Traditional knowledge" checked={traditionalKnowledge} onChange={setTraditionalKnowledge} />
                <CheckboxField label="Section 3(e) / Known ingredients" checked={knownIngredients} onChange={setKnownIngredients} />
                <CheckboxField label="ABS / Biological resources (BDA)" checked={biologicalResources} onChange={setBiologicalResources} />
                <CheckboxField label="GRATK / Genetic resources (WIPO)" checked={geneticResources} onChange={setGeneticResources} />
              </div>
            </div>

            <div className="control-row">
              <JurisdictionSelect value={jurisdiction} onChange={setJurisdiction} />
              <LanguageSelect value={language} onChange={setLanguage} />
            </div>

            <div className="control-row" style={{ marginTop: '8px', borderTop: '1px solid var(--outline-variant)', paddingTop: '16px' }}>
              <button className="button secondary" type="button" onClick={handleClear}>Clear Form</button>
              <button className="button primary" disabled={loading || !productName.trim()} type="submit">
                <span className="material-symbols-outlined">policy</span>
                Run Regulatory Review
              </button>
            </div>
          </form>
        </div>

        {/* Right Column: Result Area */}
        <div>
          {loading ? <LoadingSteps label="Assessing multi-engine regulatory requirements..." /> : null}
          {error ? <ErrorNotice error={error} /> : null}
          {result ? (
            <RegulatoryResult result={result} />
          ) : (
            <div className="empty-state">
              <span className="material-symbols-outlined" style={{ fontSize: '32px', color: 'var(--outline)', marginBottom: '8px' }}>policy</span>
              <p style={{ margin: 0 }}>Describe your formulation or invention on the left to review Section 3 exclusions, Biological Diversity Act, and GRATK compliance.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function RegulatoryResult({ result }: { result: RegulatoryAnalysisResponse }) {
  return (
    <section className="result-card">
      <div className="result-card-header">
        <span className="material-symbols-outlined">verified_user</span>
        <h3>Regulatory Assessment Summary</h3>
      </div>

      <div className="result-body">
        {/* Core Status & Confidence */}
        <div className="classification-badge-row">
          <div className="classification-badge">
            {result.overallStatus.replace(/_/g, ' ')}
          </div>
          <div className="confidence-pill">
            <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>shield</span>
            <span>{formatConfidence(result.overallConfidence)} Confidence</span>
          </div>
          <div className="confidence-pill">
            <span>{result.jurisdiction}</span>
          </div>
        </div>

        {/* Reason / Guidance */}
        <p className="result-reason">{result.reason}</p>

        {/* Clarification Questions */}
        {result.questions?.length ? (
          <div className="clarification-block">
            <h4>Clarification Questions</h4>
            <ul>
              {result.questions.map((q) => (
                <li key={q}>{q}</li>
              ))}
            </ul>
          </div>
        ) : null}

        {/* Engines Details */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '4px' }}>
          {result.engines.map((engine) => (
            <article key={engine.engine} style={{ border: '1px solid var(--outline-variant)', borderRadius: '4px', padding: '12px 14px', background: 'var(--surface)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                <span style={{ fontWeight: 700, fontSize: '13.5px', color: 'var(--primary)' }}>{engine.engine.replace(/_/g, ' ')}</span>
                <span style={{ fontSize: '12px', fontWeight: 600, padding: '2px 8px', borderRadius: '4px', background: 'var(--surface-container-high)', color: 'var(--on-surface)' }}>
                  {engine.status.replace(/_/g, ' ')}
                </span>
              </div>
              <p style={{ fontSize: '13px', margin: '0 0 8px', color: 'var(--on-surface)', lineHeight: 1.5 }}>{engine.reason}</p>
              {engine.considerations?.length ? (
                <ul style={{ margin: '0 0 8px', paddingLeft: '18px', fontSize: '12.5px', color: 'var(--secondary)' }}>
                  {engine.considerations.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : null}
              {engine.citations?.length ? (
                <div style={{ borderTop: '1px solid var(--outline-variant)', paddingTop: '8px', marginTop: '6px' }}>
                  <EvidenceList citations={engine.citations} sources={engine.sources} />
                </div>
              ) : null}
            </article>
          ))}
        </div>
      </div>

      <div className="result-card-footer">
        <button type="button" onClick={() => window.print()}>
          <span className="material-symbols-outlined">download</span>
          Export Report
        </button>
      </div>
    </section>
  );
}

function splitItems(value: string) {
  return value.split(/\n|,/).map((item) => item.trim()).filter(Boolean);
}
