import { FormEvent, useState } from 'react';
import type { AuthHeaders } from '../api/client';
import { classifyFormulation } from '../api/formulations';
import type { FormulationResponse, Language } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { CheckboxField, LanguageSelect, TextArea, TextField } from '../components/FormControls';
import { EvidenceList, formatConfidence } from '../components/Evidence';
import { LoadingSteps } from '../components/LoadingSteps';

export function FormulationPage({ auth }: { auth: AuthHeaders }) {
  const [productName, setProductName] = useState('');
  const [ingredients, setIngredients] = useState('');
  const [intendedUse, setIntendedUse] = useState('');
  const [dosageForm, setDosageForm] = useState('');
  const [claims, setClaims] = useState('');
  const [traditionalUse, setTraditionalUse] = useState(false);
  const [language, setLanguage] = useState<Language>('en');
  const [result, setResult] = useState<FormulationResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setResult(null);
    setLoading(true);
    try {
      setResult(await classifyFormulation({
        productName,
        ingredients: splitLines(ingredients),
        intendedUse,
        dosageForm,
        claims: splitLines(claims),
        traditionalUse,
        commercialIntent: true,
        targetMarket: 'India',
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
    setDosageForm('');
    setClaims('');
    setTraditionalUse(false);
    setResult(null);
    setError(null);
  }

  function loadSample(type: 'classical' | 'aahara' | 'proprietary') {
    if (type === 'classical') {
      setProductName('Triphala Guggulu Tablets');
      setIngredients('Haritaki (Terminalia chebula) 100mg\nBibhitaki (Terminalia bellirica) 100mg\nAmalaki (Emblica officinalis) 100mg\nShuddha Guggulu (Commiphora mukul) 300mg');
      setIntendedUse('Traditional digestive support, anti-inflammatory and joint comfort');
      setDosageForm('Tablet');
      setClaims('Supports natural digestion and joint mobility\nFormulated as per classical Ayurvedic Formulary');
      setTraditionalUse(true);
    } else if (type === 'aahara') {
      setProductName('AyurVital Herbal Infusion Tea');
      setIngredients('Tulsi (Ocimum sanctum) leaves 40%\nGinger (Zingiber officinale) rhizome 30%\nCardamom (Elettaria cardamomum) 20%\nCinnamon (Cinnamomum verum) 10%');
      setIntendedUse('Daily health beverage and wellness nutrition');
      setDosageForm('Herbal Tea / Infusion');
      setClaims('Nutritional herbal dietary supplement\nGeneral wellness support');
      setTraditionalUse(true);
    } else {
      setProductName('CurcuNano Bio-Enhanced Joint Care Gel');
      setIngredients('Curcumin nanoparticle extract 250mg\nPiperine bioavailability enhancer 10mg\nBoswellia serrata standardized resin 100mg\nLiposomal lipid matrix');
      setIntendedUse('Targeted topical pain management and inflammation relief');
      setDosageForm('Topical Gel');
      setClaims('Novel synergistic formulation with enhanced bioavailability\nUnique liposomal delivery matrix');
      setTraditionalUse(false);
    }
  }

  return (
    <div className="page wide-page">
      <div className="page-heading">
        <h1>Formulation Classification</h1>
        <p>Provide formulation details to identify the most likely regulatory category and route.</p>
      </div>

      <div className="form-and-results-grid">
        {/* Left Column: Form */}
        <div className="flex flex-col gap-4">
          <div className="suggested-queries">
            <p className="suggested-title">Load sample formulation:</p>
            <div className="suggested-chips">
              <button type="button" onClick={() => loadSample('classical')}>🌿 Classical Triphala Guggulu</button>
              <button type="button" onClick={() => loadSample('aahara')}>🍵 Ayurveda Aahara Herbal Tea</button>
              <button type="button" onClick={() => loadSample('proprietary')}>🔬 Proprietary Nanogel</button>
            </div>
          </div>

          <form className="panel form-grid" onSubmit={submit}>
            <TextField label="Product / formulation name" value={productName} onChange={setProductName} required />
            <TextArea label="Ingredients (Botanical/Chemical Names & Ratios)" value={ingredients} onChange={setIngredients} placeholder="One ingredient per line..." rows={4} />
            <div className="control-row">
              <div style={{ flex: 1 }}>
                <TextField label="Intended use" value={intendedUse} onChange={setIntendedUse} placeholder="e.g. Digestive support" />
              </div>
              <div style={{ flex: 1 }}>
                <TextField label="Dosage form" value={dosageForm} onChange={setDosageForm} placeholder="Tablet, capsule, tea, gel..." />
              </div>
            </div>
            <TextArea label="Claims / Therapeutic Positioning" value={claims} onChange={setClaims} placeholder="One claim per line..." rows={3} />
            <div className="control-row">
              <CheckboxField label="Known traditional use described in authoritative texts" checked={traditionalUse} onChange={setTraditionalUse} />
              <LanguageSelect value={language} onChange={setLanguage} />
            </div>

            <div className="control-row" style={{ marginTop: '8px', borderTop: '1px solid var(--outline-variant)', paddingTop: '16px' }}>
              <button className="button secondary" type="button" onClick={handleClear}>Clear Form</button>
              <button className="button primary" disabled={loading || !productName.trim()} type="submit">
                <span className="material-symbols-outlined">analytics</span>
                Analyze Formulation
              </button>
            </div>
          </form>
        </div>

        {/* Right Column: AI Assistant Result Card */}
        <div>
          {loading ? <LoadingSteps label="Analyzing formulation and checking regulations..." /> : null}
          {error ? <ErrorNotice error={error} /> : null}
          {result ? (
            <FormulationResult result={result} />
          ) : (
            <div className="empty-state">
              <span className="material-symbols-outlined" style={{ fontSize: '32px', color: 'var(--outline)', marginBottom: '8px' }}>science</span>
              <p style={{ margin: 0 }}>Provide formulation details or load a sample on the left to view the regulatory classification summary and citations.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function FormulationResult({ result }: { result: FormulationResponse }) {
  const isClassified = result.status === 'classified' || (!result.needsClarification && result.classification);

  return (
    <section className="result-card">
      <div className="result-card-header">
        <span className="material-symbols-outlined">robot_2</span>
        <h3>Analysis Summary</h3>
      </div>

      <div className="result-body">
        {/* Core Classification & Confidence */}
        <div className="classification-badge-row">
          <div className="classification-badge">
            {result.classification ? result.classification.replace(/_/g, ' ') : (result.needsClarification ? 'NEEDS CLARIFICATION' : result.status)}
          </div>
          <div className="confidence-pill">
            <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>
              {isClassified ? 'check_circle' : 'help'}
            </span>
            <span>{formatConfidence(result.confidence)} Confidence</span>
          </div>
        </div>

        {/* Route Info */}
        {result.regulatoryRoute ? (
          <div className="route-section">
            <span className="route-title">Regulatory Route</span>
            <span className="route-value">{result.regulatoryRoute.route.replace(/_/g, ' ')}</span>
            {result.regulatoryRoute.domains?.length ? (
              <div style={{ fontSize: '12px', color: 'var(--secondary)', marginTop: '4px' }}>
                Governing Domains: {result.regulatoryRoute.domains.join(', ')}
              </div>
            ) : null}
          </div>
        ) : null}

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

        {/* Evidence Citations */}
        {result.citations?.length || result.sources?.length ? (
          <div className="evidence-section">
            <div className="evidence-header">
              <span className="material-symbols-outlined">menu_book</span>
              <span>Evidence Citations</span>
            </div>
            <EvidenceList citations={result.citations} sources={result.sources} />
          </div>
        ) : null}
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

function splitLines(value: string) {
  return value.split(/\n|,/).map((item) => item.trim()).filter(Boolean);
}
