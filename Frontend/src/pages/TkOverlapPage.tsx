import { FormEvent, useState } from 'react';
import { analyzeTkOverlap } from '../api/tk';
import type { AuthHeaders } from '../api/client';
import type { Language, TkOverlapResponse } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { EvidenceList, formatConfidence } from '../components/Evidence';
import { FormattedText } from '../components/FormattedText';
import { LanguageSelect, TextArea } from '../components/FormControls';
import { LoadingSteps } from '../components/LoadingSteps';

const TK_SAMPLES = [
  {
    label: '🌿 Classical Neem & Turmeric',
    description:
      'A therapeutic herbal formulation comprising standardized extracts of Turmeric (Curcuma longa rhizome) and Neem (Azadirachta indica bark and leaves) prepared in sesame oil base for traditional topical wound healing, antimicrobial skin treatment, and anti-inflammatory Ayurvedic applications in India.',
    language: 'en' as const,
  },
  {
    label: '🧠 Ashwagandha-Brahmi Synergy',
    description:
      'A synergistic cognitive enhancement oral suspension combining Withania somnifera (Ashwagandha) root extract and Bacopa monnieri (Brahmi) whole plant extract formulated with piperine bio-enhancer for neuroprotection and adaptogenic stress relief.',
    language: 'en' as const,
  },
  {
    label: '🔬 Graphene Biosensor (No TK)',
    description:
      'A multiplexed electrochemical biosensor chip utilizing functionalized graphene oxide and gold nanoparticle electrodes for real-time enzymatic detection of blood glucose, containing no biological plant matter or traditional knowledge remedies.',
    language: 'en' as const,
  },
];

export function TkOverlapPage({ auth }: { auth: AuthHeaders }) {
  const [description, setDescription] = useState('');
  const [language, setLanguage] = useState<Language>('en');
  const [result, setResult] = useState<TkOverlapResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);

  function loadSample(sample: typeof TK_SAMPLES[number]) {
    setDescription(sample.description);
    setLanguage(sample.language);
    setError(null);
    setResult(null);
  }

  function handleClear() {
    setDescription('');
    setError(null);
    setResult(null);
  }

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
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px', marginBottom: '8px' }}>
          <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--secondary)', textTransform: 'uppercase' }}>Preload Sample:</span>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            {TK_SAMPLES.map((sample) => (
              <button
                key={sample.label}
                className="chip-button"
                type="button"
                onClick={() => loadSample(sample)}
              >
                {sample.label}
              </button>
            ))}
          </div>
        </div>

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
        <div className="control-row" style={{ gap: '10px' }}>
          {description ? (
            <button className="button secondary" type="button" onClick={handleClear}>
              Clear
            </button>
          ) : null}
          <button className="button primary" disabled={loading || !description.trim()} type="submit">
            Analyze TK Overlap
          </button>
        </div>
      </form>

      {loading ? <LoadingSteps /> : null}
      {error ? <ErrorNotice error={error} /> : null}
      {result ? <TkOverlapResult result={result} /> : null}
    </div>
  );
}

function TkOverlapResult({ result }: { result: TkOverlapResponse }) {
  const isStrong = result.classification === 'STRONG_TK_OVERLAP';
  const isPotential = result.classification === 'POTENTIAL_TK_OVERLAP';
  const isNoOverlap = result.classification === 'NO_TK_OVERLAP_FOUND';

  const badgeTheme = isStrong
    ? {
        bg: 'rgba(239, 68, 68, 0.1)',
        color: '#b91c1c',
        border: 'rgba(239, 68, 68, 0.35)',
        icon: 'warning',
        label: 'Strong Traditional Knowledge Overlap',
      }
    : isPotential
      ? {
          bg: 'rgba(245, 158, 11, 0.12)',
          color: '#b45309',
          border: 'rgba(245, 158, 11, 0.35)',
          icon: 'report_problem',
          label: 'Potential Traditional Knowledge Overlap',
        }
      : isNoOverlap
        ? {
            bg: 'rgba(34, 197, 94, 0.1)',
            color: '#15803d',
            border: 'rgba(34, 197, 94, 0.35)',
            icon: 'verified',
            label: 'No TK Overlap Found in Authoritative Corpus',
          }
        : {
            bg: 'rgba(100, 116, 139, 0.1)',
            color: '#475569',
            border: 'rgba(100, 116, 139, 0.35)',
            icon: 'help_outline',
            label: 'Insufficient Evidence / Abstained',
          };

  return (
    <section className="result-card" aria-label="TK overlap result" style={{ marginTop: '24px' }}>
      <div className="result-card-header">
        <span className="material-symbols-outlined" style={{ fontSize: '22px', color: 'var(--primary)' }}>
          policy
        </span>
        <h2 style={{ margin: 0, fontSize: '16px', fontWeight: 700, color: 'var(--on-surface)' }}>
          Traditional Knowledge Assessment Report
        </h2>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: '8px' }}>
          <button
            type="button"
            className="chip-button"
            onClick={() => window.print()}
            style={{ padding: '4px 12px', fontSize: '12px', background: 'transparent' }}
          >
            <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>
              print
            </span>
            Export Report
          </button>
        </div>
      </div>

      <div className="result-body">
        {/* Classification & Metadata Badges */}
        <div className="classification-badge-row">
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 14px',
              borderRadius: '4px',
              fontWeight: 700,
              fontSize: '13.5px',
              letterSpacing: '0.02em',
              background: badgeTheme.bg,
              color: badgeTheme.color,
              border: `1px solid ${badgeTheme.border}`,
            }}
          >
            <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>
              {badgeTheme.icon}
            </span>
            {badgeTheme.label}
          </div>

          <div className="confidence-pill">
            <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>
              shield
            </span>
            <span>{formatConfidence(result.confidence)} Match Confidence</span>
          </div>

          <div className="confidence-pill">
            <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>
              translate
            </span>
            <span>Language: {result.language?.toUpperCase() || 'EN'}</span>
          </div>

          {result.abstained ? (
            <div
              className="confidence-pill"
              style={{ background: '#fef3c7', color: '#92400e', border: '1px solid #fcd34d' }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>
                help_center
              </span>
              <span>Abstained (Out of Scope)</span>
            </div>
          ) : null}
        </div>

        {/* Assessment Narrative */}
        <div
          style={{
            background: 'var(--surface-container-low)',
            padding: '16px 20px',
            borderRadius: '6px',
            border: '1px solid var(--outline-variant)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--primary)' }}>
              summarize
            </span>
            <span
              style={{
                fontSize: '12px',
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                color: 'var(--secondary)',
              }}
            >
              Core Assessment Summary
            </span>
          </div>
          <FormattedText className="result-reason" content={result.explanation} />
        </div>

        {/* Overlap Types Dimensions */}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '10px' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--secondary)' }}>
              interests
            </span>
            <span
              style={{
                fontSize: '12.5px',
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                color: 'var(--secondary)',
              }}
            >
              Identified Overlap Dimensions ({result.overlap_types.length})
            </span>
          </div>

          {result.overlap_types.length === 0 ? (
            <div
              style={{
                padding: '12px 16px',
                background: 'var(--surface)',
                borderRadius: '4px',
                border: '1px dashed var(--outline-variant)',
                fontSize: '13px',
                color: 'var(--secondary)',
              }}
            >
              ✓ No specific overlap categories were supported by retrieved traditional knowledge evidence.
            </div>
          ) : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
              {result.overlap_types.map((type) => (
                <span
                  key={type}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                    padding: '6px 14px',
                    borderRadius: '999px',
                    fontSize: '12.5px',
                    fontWeight: 600,
                    background: 'var(--surface-container-high)',
                    color: 'var(--on-surface)',
                    border: '1px solid var(--outline-variant)',
                  }}
                >
                  <span className="material-symbols-outlined" style={{ fontSize: '15px', color: 'var(--primary)' }}>
                    check_circle
                  </span>
                  {humanize(type)}
                </span>
              ))}
            </div>
          )}
        </div>

        {/* Recommendation & Advisory Notice */}
        <div
          style={{
            background: 'var(--surface-container-lowest)',
            border: '1px solid var(--outline-variant)',
            borderRadius: '6px',
            padding: '16px 20px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--primary)' }}>
              gavel
            </span>
            <span
              style={{
                fontSize: '12.5px',
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                color: 'var(--secondary)',
              }}
            >
              Statutory Examination Recommendation
            </span>
          </div>
          <p style={{ margin: '0 0 12px', fontSize: '14px', lineHeight: 1.6, color: 'var(--on-surface)' }}>
            {result.recommendation}
          </p>
          <div
            style={{
              borderTop: '1px solid var(--outline-variant)',
              paddingTop: '10px',
              display: 'flex',
              alignItems: 'flex-start',
              gap: '8px',
              fontSize: '12px',
              lineHeight: 1.45,
              color: 'var(--secondary)',
            }}
          >
            <span className="material-symbols-outlined" style={{ fontSize: '16px', color: 'var(--secondary)', flexShrink: 0, marginTop: '1px' }}>
              info
            </span>
            <span>
              <strong>Legal Disclaimer:</strong> This is a system-generated, evidence-backed screening result. It is not an official government classification and does not determine patentability, ownership, infringement, or legal validity.
            </span>
          </div>
        </div>

        {/* Citations & Evidence List */}
        <EvidenceList citations={result.citations} sources={result.sources} />
      </div>
    </section>
  );
}

function humanize(value: string) {
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}
