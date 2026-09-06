import { FormEvent, useEffect, useState } from 'react';
import type { AuthHeaders } from '../api/client';
import { analyzeProductReadiness } from '../api/formulations';
import type {
  ClaimAnalysisItem,
  DocumentGapItem,
  FormulationRequest,
  IngredientVerificationItem,
  IpRouteAssessment,
  Language,
  ProductReadinessResponse,
  ProvidedDocument,
} from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { CheckboxField, LanguageSelect, TextArea, TextField } from '../components/FormControls';
import { EvidenceList, formatConfidence } from '../components/Evidence';
import { LoadingSteps } from '../components/LoadingSteps';

type WizardStep = 1 | 2 | 3 | 4 | 5;

const STANDARD_DOCUMENT_TEMPLATES: Array<{ name: string; type: string; description: string }> = [
  { name: 'Product Formulation Specification', type: 'FORMULATION_SPECIFICATION', description: 'Complete quantitative composition, excipients, and dosage form specs.' },
  { name: 'Classical Text Reference', type: 'CLASSICAL_TEXT_REFERENCE', description: 'Citation from First Schedule texts (e.g. Charaka, Sushruta, AFI, API).' },
  { name: 'Ingredient Sourcing & Botanical Records', type: 'INGREDIENT_SOURCE_RECORDS', description: 'Geographical origin, part used, vendor records, and harvest details.' },
  { name: 'Batch Manufacturing Flowchart / GMP', type: 'MANUFACTURING_INFO', description: 'Detailed extraction steps, temperature/pressure profiles, and GMP compliance.' },
  { name: 'Quality & NABL Assay Test Reports', type: 'QUALITY_TEST_REPORT', description: 'Heavy metals, microbial load, pesticide residue, and aflatoxin assays.' },
  { name: 'Product Label Mockup', type: 'PRODUCT_LABEL', description: 'Artwork with statutory claims, batch number, MRP, and mandatory warning text.' },
  { name: 'Stability Study Records', type: 'STABILITY_STUDY', description: 'Accelerated and real-time shelf-life degradation studies.' },
  { name: 'Trademark Clearance / Filing Record', type: 'TRADEMARK_INFO', description: 'IP India Class 5/3/30 search report or trademark application acknowledgment.' },
  { name: 'NBA / ABS Prior Approval / SBB Intimation', type: 'NBA_ABS_APPROVAL', description: 'Section 6 NBA clearance for IP filings based on Indian biological resources.' },
];

export function FormulationPage({ auth }: { auth: AuthHeaders }) {
  const [currentStep, setCurrentStep] = useState<WizardStep>(1);

  // Step 1: Product Details
  const [productName, setProductName] = useState('');
  const [dosageForm, setDosageForm] = useState('');
  const [intendedUse, setIntendedUse] = useState('');
  const [manufacturer, setManufacturer] = useState('');
  const [countryOfManufacture, setCountryOfManufacture] = useState('India');
  const [targetMarket, setTargetMarket] = useState('India');
  const [knownClassification, setKnownClassification] = useState('');
  const [ingredientsText, setIngredientsText] = useState('');
  const [ingredientRatiosText, setIngredientRatiosText] = useState('');
  const [sourceOfIngredients, setSourceOfIngredients] = useState('');

  // Step 2: Documents
  const [selectedStandardDocs, setSelectedStandardDocs] = useState<Record<string, boolean>>({});
  const [customDocuments, setCustomDocuments] = useState<ProvidedDocument[]>([]);
  const [customDocName, setCustomDocName] = useState('');
  const [customDocType, setCustomDocType] = useState('FORMULATION_SPECIFICATION');
  const [customDocDetails, setCustomDocDetails] = useState('');

  // Step 3: Claims & Positioning
  const [claimsText, setClaimsText] = useState('');
  const [traditionalUse, setTraditionalUse] = useState(false);
  const [commercialIntent, setCommercialIntent] = useState(true);
  const [language, setLanguage] = useState<Language>('en');

  // Step 5 & State: Analysis
  const [result, setResult] = useState<ProductReadinessResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<'overview' | 'gaps' | 'claims' | 'ip' | 'steps' | 'report'>('overview');

  useEffect(() => {
    if (!reportOpen) return;
    const previousOverflow = document.body.style.overflow;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setReportOpen(false);
    };
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [reportOpen]);

  function toggleStandardDoc(type: string) {
    setSelectedStandardDocs((prev) => ({
      ...prev,
      [type]: !prev[type],
    }));
  }

  function addCustomDoc() {
    if (!customDocName.trim()) return;
    const newDoc: ProvidedDocument = {
      id: `doc-custom-${Date.now()}`,
      name: customDocName.trim(),
      type: customDocType,
      status: 'DOCUMENT_PROVIDED',
      notes: customDocDetails.trim() || undefined,
    };
    setCustomDocuments((prev) => [...prev, newDoc]);
    setCustomDocName('');
    setCustomDocDetails('');
  }

  function removeCustomDoc(id: string) {
    setCustomDocuments((prev) => prev.filter((d) => d.id !== id));
  }

  function buildDocumentList(): ProvidedDocument[] {
    const list: ProvidedDocument[] = [];
    STANDARD_DOCUMENT_TEMPLATES.forEach((tmpl, idx) => {
      if (selectedStandardDocs[tmpl.type]) {
        list.push({
          id: `std-doc-${idx + 1}`,
          name: tmpl.name,
          type: tmpl.type,
          status: 'DOCUMENT_PROVIDED',
          notes: tmpl.description,
        });
      }
    });
    return [...list, ...customDocuments];
  }

  async function handleAnalyze(e?: FormEvent) {
    if (e) e.preventDefault();
    setError(null);
    setLoading(true);

    const payload: FormulationRequest = {
      productName: productName.trim(),
      ingredients: splitLines(ingredientsText),
      ingredientRatios: splitLines(ingredientRatiosText),
      dosageForm: dosageForm.trim() || undefined,
      intendedUse: intendedUse.trim() || undefined,
      claims: splitLines(claimsText),
      sourceOfIngredients: sourceOfIngredients.trim() || undefined,
      manufacturer: manufacturer.trim() || undefined,
      countryOfManufacture: countryOfManufacture.trim() || 'India',
      targetMarket: targetMarket.trim() || 'India',
      knownClassification: knownClassification.trim() || undefined,
      traditionalUse,
      commercialIntent,
      language,
      documents: buildDocumentList(),
    };

    try {
      const resp = await analyzeProductReadiness(payload, auth);
      setResult(resp);
      setCurrentStep(5);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  function handleReset() {
    setProductName('');
    setDosageForm('');
    setIntendedUse('');
    setManufacturer('');
    setCountryOfManufacture('India');
    setTargetMarket('India');
    setKnownClassification('');
    setIngredientsText('');
    setIngredientRatiosText('');
    setSourceOfIngredients('');
    setSelectedStandardDocs({});
    setCustomDocuments([]);
    setClaimsText('');
    setTraditionalUse(false);
    setCommercialIntent(true);
    setResult(null);
    setReportOpen(false);
    setError(null);
    setCurrentStep(1);
  }

  function loadSample(type: 'classical' | 'aahara' | 'proprietary') {
    if (type === 'classical') {
      setProductName('Triphala Guggulu Tablets');
      setDosageForm('Tablet');
      setIntendedUse('Traditional joint comfort, deep tissue detox, and digestive balance');
      setManufacturer('Dabur India Ltd');
      setCountryOfManufacture('India');
      setTargetMarket('India');
      setKnownClassification('Classical Ayurvedic Medicine');
      setIngredientsText('Haritaki (Terminalia chebula)\nBibhitaki (Terminalia bellirica)\nAmalaki (Emblica officinalis)\nShuddha Guggulu (Commiphora mukul)');
      setIngredientRatiosText('1:1:1:3 by weight');
      setSourceOfIngredients('Wild harvested & cultivated in Madhya Pradesh, India');
      setSelectedStandardDocs({
        FORMULATION_SPECIFICATION: true,
        CLASSICAL_TEXT_REFERENCE: true,
        MANUFACTURING_INFO: true,
      });
      setCustomDocuments([]);
      setClaimsText('Traditional digestive support as documented in Sharangadhara Samhita\nHelps maintain healthy joint mobility and metabolic balance');
      setTraditionalUse(true);
      setCommercialIntent(true);
    } else if (type === 'aahara') {
      setProductName('AyurVital Daily Infusion Tea');
      setDosageForm('Herbal Tea / Infusion Sachet');
      setIntendedUse('Daily wellness beverage and nutritional refreshment');
      setManufacturer('TeaCo Organics Pvt Ltd');
      setCountryOfManufacture('India');
      setTargetMarket('India');
      setKnownClassification('Ayurveda Aahara');
      setIngredientsText('Tulsi (Ocimum sanctum) leaves\nGinger (Zingiber officinale) rhizome\nCardamom (Elettaria cardamomum) seeds\nCinnamon (Cinnamomum verum) bark');
      setIngredientRatiosText('40% : 30% : 20% : 10%');
      setSourceOfIngredients('Assam & Kerala, India');
      setSelectedStandardDocs({
        FORMULATION_SPECIFICATION: true,
        INGREDIENT_SOURCE_RECORDS: true,
        PRODUCT_LABEL: true,
      });
      setCustomDocuments([]);
      setClaimsText('Nutritional herbal dietary infusion for daily wellness\nRefreshing blend supporting natural vitality and digestion');
      setTraditionalUse(true);
      setCommercialIntent(true);
    } else {
      setProductName('CurcuNano Bio-Enhanced Joint Gel');
      setDosageForm('Topical Gel');
      setIntendedUse('Targeted topical pain management and inflammation relief');
      setManufacturer('BioAyur Labs India');
      setCountryOfManufacture('India');
      setTargetMarket('India');
      setKnownClassification('Ayurvedic Proprietary Medicine');
      setIngredientsText('Curcumin nanoparticle extract\nPiperine bioavailability enhancer\nBoswellia serrata standardized resin\nLiposomal lipid matrix');
      setIngredientRatiosText('250mg : 10mg : 100mg per dose');
      setSourceOfIngredients('Standardized botanical extracts from Western Ghats, Kerala, India');
      setSelectedStandardDocs({
        FORMULATION_SPECIFICATION: true,
        QUALITY_TEST_REPORT: true,
        TRADEMARK_INFO: true,
      });
      setCustomDocuments([]);
      setClaimsText('Novel synergistic formulation with enhanced lipid nanoparticle delivery\nTargeted joint comfort and localized anti-inflammatory support');
      setTraditionalUse(false);
      setCommercialIntent(true);
    }
  }

  const allProvidedDocs = buildDocumentList();

  return (
    <div className="page wide-page">
      {/* Header Banner */}
      <div className="page-heading">
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '44px',
            height: '44px',
            borderRadius: '12px',
            background: 'linear-gradient(135deg, var(--primary), #1e40af)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
          }}>
            <span className="material-symbols-outlined" style={{ fontSize: '26px' }}>verified_user</span>
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: '24px', fontWeight: 700 }}>Ayurveda Product Market-Readiness & Compliance Engine</h1>
            <p style={{ margin: '4px 0 0', color: 'var(--on-surface-variant)', fontSize: '14px' }}>
              Evidence-grounded regulatory pre-screening, statutory gap analysis, claims scrutiny & IP roadmap.
            </p>
          </div>
        </div>
      </div>

      {/* Stepper Navigation */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '16px 24px',
        background: 'var(--surface-container-low)',
        borderRadius: '16px',
        marginBottom: '24px',
        border: '1px solid var(--outline-variant)',
      }}>
        {[
          { step: 1, title: '1. Product Details', icon: 'inventory_2' },
          { step: 2, title: '2. Documents & Evidence', icon: 'description' },
          { step: 3, title: '3. Claims & Positioning', icon: 'flag' },
          { step: 4, title: '4. Pre-Flight Review', icon: 'fact_check' },
          { step: 5, title: '5. Assessment Report', icon: 'analytics' },
        ].map((item) => {
          const isCurrent = currentStep === item.step;
          const isDone = currentStep > item.step;
          return (
            <button
              key={item.step}
              type="button"
              disabled={loading || (item.step === 5 && !result)}
              onClick={() => setCurrentStep(item.step as WizardStep)}
              style={{
                background: isCurrent ? 'var(--primary)' : isDone ? 'var(--surface-container-high)' : 'transparent',
                color: isCurrent ? 'var(--on-primary)' : 'var(--on-surface)',
                border: 'none',
                borderRadius: '12px',
                padding: '10px 16px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                fontWeight: isCurrent ? 700 : 500,
                fontSize: '13px',
                cursor: (item.step === 5 && !result) ? 'not-allowed' : 'pointer',
                transition: 'all 0.2s ease',
              }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>
                {isDone ? 'check_circle' : item.icon}
              </span>
              <span>{item.title}</span>
            </button>
          );
        })}
      </div>

      {error ? <ErrorNotice error={error} /> : null}

      {/* STEP 1: Product Details */}
      {currentStep === 1 && (
        <div className="panel" style={{ padding: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <div>
              <h2 style={{ margin: 0, fontSize: '18px', fontWeight: 600 }}>Step 1: Product Identification & Formula</h2>
              <p style={{ margin: '4px 0 0', color: 'var(--on-surface-variant)', fontSize: '13px' }}>
                Enter product identity, dosage form, manufacturing entities, and raw materials.
              </p>
            </div>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <span style={{ fontSize: '12px', color: 'var(--secondary)' }}>Preload Sample:</span>
              <button className="chip-button" type="button" onClick={() => loadSample('classical')}>
                🌿 Classical Triphala
              </button>
              <button className="chip-button" type="button" onClick={() => loadSample('aahara')}>
                🍵 AyurVital Tea
              </button>
              <button className="chip-button" type="button" onClick={() => loadSample('proprietary')}>
                🔬 CurcuNano Gel
              </button>
            </div>
          </div>

          <div className="form-grid">
            <div className="control-row">
              <div style={{ flex: 2 }}>
                <TextField
                  label="Product / Formulation Name *"
                  value={productName}
                  onChange={setProductName}
                  placeholder="e.g. Triphala Guggulu Tablets, AyurVital Infusion"
                  required
                />
              </div>
              <div style={{ flex: 1 }}>
                <TextField
                  label="Dosage Form"
                  value={dosageForm}
                  onChange={setDosageForm}
                  placeholder="Tablet, Capsule, Powder, Tea, Topical Gel..."
                />
              </div>
            </div>

            <TextField
              label="Intended Use / Purpose"
              value={intendedUse}
              onChange={setIntendedUse}
              placeholder="e.g. Traditional joint comfort and digestive support"
            />

            <div className="control-row">
              <div style={{ flex: 1 }}>
                <TextField
                  label="Manufacturer Name (Optional)"
                  value={manufacturer}
                  onChange={setManufacturer}
                  placeholder="e.g. Dabur India Ltd / GoodLife Botanicals"
                />
              </div>
              <div style={{ flex: 1 }}>
                <TextField
                  label="Country of Manufacture"
                  value={countryOfManufacture}
                  onChange={setCountryOfManufacture}
                  placeholder="India"
                />
              </div>
              <div style={{ flex: 1 }}>
                <TextField
                  label="Target Market"
                  value={targetMarket}
                  onChange={setTargetMarket}
                  placeholder="India / Global"
                />
              </div>
            </div>

            <TextField
              label="Known / Intended Regulatory Classification (Optional)"
              value={knownClassification}
              onChange={setKnownClassification}
              placeholder="Classical Ayurvedic Medicine / Proprietary / Ayurveda Aahara / Cosmetic"
            />

            <TextArea
              label="Ingredients List (Botanical / Common / Classical Names) *"
              value={ingredientsText}
              onChange={setIngredientsText}
              placeholder="One ingredient per line:&#10;Haritaki (Terminalia chebula)&#10;Bibhitaki (Terminalia bellirica)&#10;Amalaki (Emblica officinalis)"
              rows={4}
            />

            <div className="control-row">
              <div style={{ flex: 1 }}>
                <TextArea
                  label="Ingredient Ratios / Quantities (Optional)"
                  value={ingredientRatiosText}
                  onChange={setIngredientRatiosText}
                  placeholder="One ratio per line or comma-separated:&#10;100mg&#10;100mg&#10;100mg"
                  rows={2}
                />
              </div>
              <div style={{ flex: 1 }}>
                <TextArea
                  label="Source of Ingredients / Geographic Origin"
                  value={sourceOfIngredients}
                  onChange={setSourceOfIngredients}
                  placeholder="e.g. Cultivated in Madhya Pradesh, Western Ghats biodiversity region"
                  rows={2}
                />
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '24px', gap: '12px' }}>
            <button className="button secondary" type="button" onClick={handleReset}>Clear All</button>
            <button
              className="button primary"
              type="button"
              disabled={!productName.trim()}
              onClick={() => setCurrentStep(2)}
            >
              <span>Next: Documents & Evidence</span>
              <span className="material-symbols-outlined">navigate_next</span>
            </button>
          </div>
        </div>
      )}

      {/* STEP 2: Documents & Evidence */}
      {currentStep === 2 && (
        <div className="panel" style={{ padding: '24px' }}>
          <div style={{ marginBottom: '20px' }}>
            <h2 style={{ margin: 0, fontSize: '18px', fontWeight: 600 }}>Step 2: Available Documents & Regulatory Records</h2>
            <p style={{ margin: '4px 0 0', color: 'var(--on-surface-variant)', fontSize: '13px' }}>
              Declare existing evidence. The system compares available records against statutory requirements to generate a gap analysis.
            </p>
          </div>

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
            gap: '12px',
            marginBottom: '24px',
          }}>
            {STANDARD_DOCUMENT_TEMPLATES.map((tmpl) => {
              const isSelected = !!selectedStandardDocs[tmpl.type];
              return (
                <div
                  key={tmpl.type}
                  onClick={() => toggleStandardDoc(tmpl.type)}
                  style={{
                    padding: '16px',
                    borderRadius: '12px',
                    border: isSelected ? '2px solid var(--primary)' : '1px solid var(--outline-variant)',
                    background: isSelected ? 'var(--surface-container-high)' : 'var(--surface-container-lowest)',
                    cursor: 'pointer',
                    display: 'flex',
                    gap: '12px',
                    alignItems: 'flex-start',
                    transition: 'all 0.15s ease',
                  }}
                >
                  <span className="material-symbols-outlined" style={{
                    color: isSelected ? 'var(--primary)' : 'var(--outline)',
                    fontSize: '22px',
                  }}>
                    {isSelected ? 'check_box' : 'check_box_outline_blank'}
                  </span>
                  <div>
                    <strong style={{ fontSize: '13px', display: 'block', marginBottom: '4px' }}>{tmpl.name}</strong>
                    <p style={{ margin: 0, fontSize: '11px', color: 'var(--on-surface-variant)' }}>{tmpl.description}</p>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Custom Document Entry */}
          <div style={{
            padding: '16px',
            borderRadius: '12px',
            background: 'var(--surface-container-low)',
            border: '1px dashed var(--outline-variant)',
            marginBottom: '24px',
          }}>
            <h4 style={{ margin: '0 0 12px', fontSize: '14px' }}>Add Other Document / Certificate</h4>
            <div className="control-row">
              <div style={{ flex: 2 }}>
                <TextField
                  label="Document Title"
                  value={customDocName}
                  onChange={setCustomDocName}
                  placeholder="e.g. NABL Heavy Metal Assay, AYUSH Manufacturing License"
                />
              </div>
              <div style={{ flex: 1 }}>
                <label style={{ fontSize: '12px', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Document Category</label>
                <select
                  className="input-select"
                  value={customDocType}
                  onChange={(e) => setCustomDocType(e.target.value)}
                  style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid var(--outline-variant)' }}
                >
                  <option value="FORMULATION_SPECIFICATION">Formulation Specification</option>
                  <option value="CLASSICAL_TEXT_REFERENCE">Classical Text Reference</option>
                  <option value="INGREDIENT_SOURCE_RECORDS">Ingredient Source Records</option>
                  <option value="MANUFACTURING_INFO">Manufacturing / GMP</option>
                  <option value="QUALITY_TEST_REPORT">Quality / Lab Report</option>
                  <option value="STABILITY_STUDY">Stability Study</option>
                  <option value="PRODUCT_LABEL">Product Label Mockup</option>
                  <option value="TRADEMARK_INFO">Trademark Information</option>
                  <option value="PATENT_DOC">Patent / Prior Art Document</option>
                  <option value="NBA_ABS_APPROVAL">ABS / NBA Approval Record</option>
                  <option value="OTHER_REGULATORY_DOC">Other Regulatory License</option>
                </select>
              </div>
              <div style={{ flex: 2 }}>
                <TextField
                  label="Details / Certificate No. / Notes"
                  value={customDocDetails}
                  onChange={setCustomDocDetails}
                  placeholder="e.g. Certificate #AYU-2024-991, Exp: 2027"
                />
              </div>
              <div style={{ display: 'flex', alignItems: 'flex-end' }}>
                <button className="button secondary" type="button" onClick={addCustomDoc}>
                  <span className="material-symbols-outlined">add</span>
                  Add Document
                </button>
              </div>
            </div>

            {customDocuments.length > 0 && (
              <div style={{ marginTop: '12px' }}>
                <span style={{ fontSize: '12px', fontWeight: 600 }}>Custom Documents Added:</span>
                <ul style={{ margin: '6px 0 0', paddingLeft: '20px', fontSize: '12px' }}>
                  {customDocuments.map((doc) => (
                    <li key={doc.id} style={{ marginBottom: '4px' }}>
                      <strong>{doc.name}</strong> ({doc.type}) {doc.notes ? `— ${doc.notes}` : ''}
                      <button
                        type="button"
                        onClick={() => removeCustomDoc(doc.id)}
                        style={{ marginLeft: '8px', background: 'none', border: 'none', color: 'var(--error)', cursor: 'pointer', fontSize: '11px' }}
                      >
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '13px', color: 'var(--secondary)' }}>
              Total Documents Attached: <strong>{allProvidedDocs.length}</strong>
            </span>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button className="button secondary" type="button" onClick={() => setCurrentStep(1)}>
                <span className="material-symbols-outlined">navigate_before</span>
                Back
              </button>
              <button className="button primary" type="button" onClick={() => setCurrentStep(3)}>
                <span>Next: Claims & Positioning</span>
                <span className="material-symbols-outlined">navigate_next</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* STEP 3: Claims & Positioning */}
      {currentStep === 3 && (
        <div className="panel" style={{ padding: '24px' }}>
          <div style={{ marginBottom: '20px' }}>
            <h2 style={{ margin: 0, fontSize: '18px', fontWeight: 600 }}>Step 3: Marketing & Therapeutic Claims</h2>
            <p style={{ margin: '4px 0 0', color: 'var(--on-surface-variant)', fontSize: '13px' }}>
              Enter label claims and marketing positioning to screen against the Drugs and Magic Remedies Act and FSSAI rules.
            </p>
          </div>

          <div className="form-grid">
            <TextArea
              label="Stated Claims / Marketing Representations *"
              value={claimsText}
              onChange={setClaimsText}
              placeholder="One claim per line:&#10;Supports healthy joint mobility&#10;Traditional digestive balance as per Ayurvedic texts&#10;Contains lipid nanoparticle delivery system"
              rows={4}
            />

            <div style={{
              padding: '16px',
              borderRadius: '12px',
              background: 'var(--surface-container-low)',
              border: '1px solid var(--outline-variant)',
            }}>
              <h4 style={{ margin: '0 0 8px', fontSize: '14px' }}>Regulatory Guidance Signals</h4>
              <CheckboxField
                label="Product formulation is based strictly on classical Ayurvedic treatises (Charaka, Sushruta, AFI, etc.)"
                checked={traditionalUse}
                onChange={setTraditionalUse}
              />
              <div style={{ height: '8px' }} />
              <CheckboxField
                label="Commercial launch intended (triggers Trademark clearance and statutory label checklist)"
                checked={commercialIntent}
                onChange={setCommercialIntent}
              />
              <div style={{ marginTop: '12px', display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontSize: '13px', fontWeight: 500 }}>Report Language:</span>
                <LanguageSelect value={language} onChange={setLanguage} />
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '24px' }}>
            <button className="button secondary" type="button" onClick={() => setCurrentStep(2)}>
              <span className="material-symbols-outlined">navigate_before</span>
              Back
            </button>
            <button className="button primary" type="button" onClick={() => setCurrentStep(4)}>
              <span>Next: Pre-Flight Review</span>
              <span className="material-symbols-outlined">navigate_next</span>
            </button>
          </div>
        </div>
      )}

      {/* STEP 4: Pre-Flight Review */}
      {currentStep === 4 && (
        <div className="panel" style={{ padding: '24px' }}>
          <div style={{ marginBottom: '20px' }}>
            <h2 style={{ margin: 0, fontSize: '18px', fontWeight: 600 }}>Step 4: Pre-Flight Audit Review</h2>
            <p style={{ margin: '4px 0 0', color: 'var(--on-surface-variant)', fontSize: '13px' }}>
              Confirm all formulation data before initiating authoritative RAG multi-domain analysis.
            </p>
          </div>

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
            gap: '16px',
            marginBottom: '24px',
          }}>
            <div style={{
              padding: '16px',
              background: 'var(--surface-container-low)',
              borderRadius: '12px',
              border: '1px solid var(--outline-variant)',
            }}>
              <span style={{ fontSize: '11px', textTransform: 'uppercase', color: 'var(--secondary)', fontWeight: 700 }}>Product Core</span>
              <div style={{ marginTop: '6px', fontSize: '15px', fontWeight: 600 }}>{productName || 'Unnamed'}</div>
              <div style={{ fontSize: '13px', color: 'var(--on-surface-variant)', marginTop: '2px' }}>
                Dosage Form: <strong>{dosageForm || 'Unspecified'}</strong>
              </div>
              <div style={{ fontSize: '13px', color: 'var(--on-surface-variant)' }}>
                Intended Use: {intendedUse || 'Unspecified'}
              </div>
              <div style={{ fontSize: '12px', color: 'var(--outline)', marginTop: '8px' }}>
                Manufacturer: {manufacturer || 'Not disclosed'} ({countryOfManufacture} → {targetMarket})
              </div>
            </div>

            <div style={{
              padding: '16px',
              background: 'var(--surface-container-low)',
              borderRadius: '12px',
              border: '1px solid var(--outline-variant)',
            }}>
              <span style={{ fontSize: '11px', textTransform: 'uppercase', color: 'var(--secondary)', fontWeight: 700 }}>Ingredients & Origin</span>
              <div style={{ marginTop: '6px', fontSize: '13px' }}>
                Total Declared: <strong>{splitLines(ingredientsText).length} items</strong>
              </div>
              <div style={{ fontSize: '12px', color: 'var(--on-surface-variant)', marginTop: '4px' }}>
                Source: {sourceOfIngredients || 'Unspecified'}
              </div>
              <div style={{ fontSize: '12px', color: 'var(--on-surface-variant)', marginTop: '2px' }}>
                Classical Basis: <strong>{traditionalUse ? 'Yes (Classical Text)' : 'Non-classical / Proprietary'}</strong>
              </div>
            </div>

            <div style={{
              padding: '16px',
              background: 'var(--surface-container-low)',
              borderRadius: '12px',
              border: '1px solid var(--outline-variant)',
            }}>
              <span style={{ fontSize: '11px', textTransform: 'uppercase', color: 'var(--secondary)', fontWeight: 700 }}>Evidence & Claims</span>
              <div style={{ marginTop: '6px', fontSize: '13px' }}>
                Documents Attached: <strong>{allProvidedDocs.length} records</strong>
              </div>
              <div style={{ fontSize: '13px', marginTop: '2px' }}>
                Claims Stated: <strong>{splitLines(claimsText).length} statements</strong>
              </div>
              <div style={{ fontSize: '12px', color: 'var(--secondary)', marginTop: '6px' }}>
                Multi-domain RAG calls: Ayurveda + Drugs & Cosmetics Act + FSSAI + NBA (ABS) + Patents Act
              </div>
            </div>
          </div>

          <div style={{
            padding: '16px',
            borderRadius: '12px',
            background: 'var(--surface-container)',
            borderLeft: '4px solid var(--primary)',
            marginBottom: '24px',
          }}>
            <p style={{ margin: 0, fontSize: '12px', lineHeight: 1.5 }}>
              <strong>Notice</strong>: This engine is an advisory regulatory information and pre-screening verification tool.
              It retrieves evidence from the frozen legal corpus to highlight missing documents, statutory risks, and IP routes.
              It does not grant licenses or guarantee market approval.
            </p>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <button className="button secondary" type="button" onClick={() => setCurrentStep(3)}>
              <span className="material-symbols-outlined">navigate_before</span>
              Back
            </button>
            <button
              className="button primary"
              type="button"
              disabled={loading || !productName.trim()}
              onClick={() => handleAnalyze()}
              style={{ padding: '12px 24px', fontSize: '15px' }}
            >
              <span className="material-symbols-outlined">analytics</span>
              {loading ? 'Analyzing Market Readiness...' : 'Run Market-Readiness & Compliance Analysis'}
            </button>
          </div>

          {loading ? (
            <div style={{ marginTop: '24px' }}>
              <LoadingSteps label="Querying multi-domain RAG for Drugs & Cosmetics Act, FSSAI regulations, Section 3(p) exclusions, and NBA requirements..." />
            </div>
          ) : null}
        </div>
      )}

      {/* STEP 5: Assessment Report */}
      {currentStep === 5 && result && (
        <div>
          {/* Status Banner */}
          <div style={{
            padding: '20px 24px',
            borderRadius: '16px',
            background: getStatusBgColor(result.status),
            border: `1px solid ${getStatusBorderColor(result.status)}`,
            marginBottom: '24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '16px',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <span className="material-symbols-outlined" style={{ fontSize: '36px', color: getStatusTextColor(result.status) }}>
                {getStatusIcon(result.status)}
              </span>
              <div>
                <span style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 700, letterSpacing: '0.05em', color: getStatusTextColor(result.status) }}>
                  Pre-Market Readiness Status
                </span>
                <h2 style={{ margin: '2px 0 0', fontSize: '20px', fontWeight: 700, color: getStatusTextColor(result.status) }}>
                  {formatStatusLabel(result.status)}
                </h2>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
              <div style={{
                background: 'rgba(255,255,255,0.85)',
                padding: '8px 16px',
                borderRadius: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                border: '1px solid var(--outline-variant)',
              }}>
                <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--primary)' }}>verified</span>
                <span style={{ fontSize: '13px', fontWeight: 600 }}>{formatConfidence(result.confidence)} Confidence</span>
              </div>
              <button className="button secondary" type="button" onClick={() => setCurrentStep(1)}>
                <span className="material-symbols-outlined">edit</span>
                Edit Inputs
              </button>
            </div>
          </div>

          {/* 7 Discrete Readiness Score Cards */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
            gap: '12px',
            marginBottom: '24px',
          }}>
            <ScoreCard
              label="Classification"
              value={result.scores.regulatoryClassification}
              subtext={result.classification.category.replace(/_/g, ' ')}
            />
            <ScoreCard
              label="Doc Completeness"
              value={result.scores.documentCompleteness}
              subtext={`${result.documents.totalProvided} provided`}
            />
            <ScoreCard
              label="Claims Risk"
              value={result.scores.claims}
              subtext={`${result.claims.length} analyzed`}
            />
            <ScoreCard
              label="Ingredients"
              value={result.scores.ingredientVerification}
              subtext={`${result.ingredients.length} items checked`}
            />
            <ScoreCard
              label="Traditional Knowledge"
              value={result.scores.tk ?? result.scores.traditionalKnowledge}
              subtext={result.traditionalKnowledge.isClassicalFormulation ? 'Classical' : 'Proprietary'}
            />
            <ScoreCard
              label="Biodiversity / ABS"
              value={result.scores.abs ?? result.scores.biodiversityAbs}
              subtext={result.biodiversityAbs.biologicalResourceUsed ? 'Bio-Resource Indicated' : 'Not Indicated'}
            />
            <ScoreCard
              label="IP Opportunities"
              value={result.scores.ip ?? result.scores.intellectualProperty}
              subtext="Routes Identified"
            />
          </div>

          {/* Navigation Tabs */}
          <div style={{
            display: 'flex',
            gap: '8px',
            borderBottom: '1px solid var(--outline-variant)',
            marginBottom: '20px',
            overflowX: 'auto',
            background: 'rgba(255, 255, 255, 0.58)',
            backdropFilter: 'blur(4px)',
            WebkitBackdropFilter: 'blur(4px)',
            borderRadius: '10px 10px 0 0',
          }}>
            {[
              { id: 'overview', label: '1. Pathway & Core', icon: 'category' },
              { id: 'gaps', label: `2. Document Gaps (${result.gaps.filter(g => g.status === 'MISSING').length} Missing)`, icon: 'checklist' },
              { id: 'claims', label: '3. Claims & Ingredients', icon: 'health_and_safety' },
              { id: 'ip', label: '4. IP, TK & ABS', icon: 'lightbulb' },
              { id: 'steps', label: `5. Next Steps (${result.nextSteps.length})`, icon: 'list_alt' },
              { id: 'report', label: '6. Full 17-Section Report', icon: 'article' },
            ].map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => {
                  if (tab.id === 'report') {
                    setReportOpen(true);
                    return;
                  }
                  setActiveTab(tab.id as typeof activeTab);
                }}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: activeTab === tab.id ? '3px solid var(--primary)' : '3px solid transparent',
                  padding: '10px 16px',
                  fontWeight: activeTab === tab.id ? 700 : 500,
                  color: activeTab === tab.id ? 'var(--primary)' : 'var(--on-surface-variant)',
                  fontSize: '13px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                }}
              >
                <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>{tab.icon}</span>
                <span>{tab.label}</span>
              </button>
            ))}
          </div>

          {/* TAB 1: Overview & Pathway */}
          {activeTab === 'overview' && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
              <div className="panel" style={{ padding: '20px' }}>
                <h3 style={{ margin: '0 0 16px', fontSize: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span className="material-symbols-outlined" style={{ color: 'var(--primary)' }}>category</span>
                  Preliminary Product Category
                </h3>
                <div style={{
                  padding: '16px',
                  borderRadius: '12px',
                  background: 'var(--surface-container-high)',
                  marginBottom: '16px',
                }}>
                  <span style={{ fontSize: '11px', textTransform: 'uppercase', color: 'var(--secondary)', fontWeight: 700 }}>Assessed Category</span>
                  <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--primary)', marginTop: '4px' }}>
                    {result.classification.category.replace(/_/g, ' ')}
                  </div>
                  <p style={{ margin: '8px 0 0', fontSize: '13px', color: 'var(--on-surface-variant)' }}>
                    {result.classification.rationale}
                  </p>
                </div>

                <div style={{ fontSize: '13px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <div><strong>Internal Code:</strong> <code>{result.classification.internalCode}</code></div>
                  <div><strong>Classification Confidence:</strong> {formatConfidence(result.classification.confidence)}</div>
                  <div><strong>Statutory Status:</strong> {result.classification.status}</div>
                </div>
              </div>

              <div className="panel" style={{ padding: '20px' }}>
                <h3 style={{ margin: '0 0 16px', fontSize: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span className="material-symbols-outlined" style={{ color: 'var(--primary)' }}>route</span>
                  Regulatory Pathway to Investigate
                </h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <InfoRow label="Pathway Route" value={result.regulatory.pathway.replace(/_/g, ' ')} />
                  <InfoRow label="Governing Framework" value={result.regulatory.governingFramework} />
                  <InfoRow label="Licensing Authority" value={result.regulatory.licensingAuthority} />
                  <InfoRow label="Applicable Standards" value={result.regulatory.standards} />
                </div>
              </div>

              {result.questions?.length > 0 && (
                <div className="panel" style={{ padding: '20px', gridColumn: '1 / -1' }}>
                  <h4 style={{ margin: '0 0 8px', color: 'var(--primary)', fontSize: '15px' }}>
                    Suggested Clarifications to Accelerate Regulatory Assessment:
                  </h4>
                  <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '13px', lineHeight: 1.6 }}>
                    {result.questions.map((q, i) => (
                      <li key={i}>{q}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* TAB 2: Document Gap Analysis */}
          {activeTab === 'gaps' && (
            <div className="panel" style={{ padding: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <h3 style={{ margin: 0, fontSize: '16px' }}>13-Point Statutory Document Gap Analysis</h3>
                <span style={{ fontSize: '12px', color: 'var(--secondary)' }}>
                  Evaluated against Drugs and Cosmetics Rules, Schedule T GMP & FSSAI Standards
                </span>
              </div>

              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                  <thead>
                    <tr style={{ background: 'var(--surface-container)', borderBottom: '1px solid var(--outline-variant)', textAlign: 'left' }}>
                      <th style={{ padding: '10px 12px' }}>Document Area</th>
                      <th style={{ padding: '10px 12px' }}>Status</th>
                      <th style={{ padding: '10px 12px' }}>Statutory Importance</th>
                      <th style={{ padding: '10px 12px' }}>Statutory Reference</th>
                      <th style={{ padding: '10px 12px' }}>Observation / Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.gaps.map((gap, idx) => (
                      <tr key={idx} style={{ borderBottom: '1px solid var(--surface-container-high)' }}>
                        <td style={{ padding: '10px 12px', fontWeight: 600 }}>{gap.documentArea}</td>
                        <td style={{ padding: '10px 12px' }}>
                          <span style={{
                            display: 'inline-block',
                            padding: '3px 8px',
                            borderRadius: '6px',
                            fontSize: '11px',
                            fontWeight: 700,
                            background: getDocStatusBg(gap.status),
                            color: getDocStatusColor(gap.status),
                          }}>
                            {gap.status}
                          </span>
                        </td>
                        <td style={{ padding: '10px 12px' }}>
                          <span style={{
                            fontSize: '11px',
                            fontWeight: 600,
                            color: gap.importance === 'REQUIRED' ? '#b45309' : 'var(--secondary)',
                          }}>
                            {gap.importance}
                          </span>
                        </td>
                        <td style={{ padding: '10px 12px', fontSize: '11px', color: 'var(--secondary)' }}>
                          {gap.statutoryReference}
                        </td>
                        <td style={{ padding: '10px 12px', color: 'var(--on-surface-variant)' }}>
                          {gap.reason}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* TAB 3: Claims & Ingredients */}
          {activeTab === 'claims' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <div className="panel" style={{ padding: '20px' }}>
                <h3 style={{ margin: '0 0 16px', fontSize: '16px' }}>Claims Risk Scrutiny & Statutory Categorization</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  {result.claims.map((claim: ClaimAnalysisItem, idx: number) => (
                    <div
                      key={idx}
                      style={{
                        padding: '14px',
                        borderRadius: '12px',
                        background: 'var(--surface-container-low)',
                        border: '1px solid var(--outline-variant)',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '6px',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <strong style={{ fontSize: '14px' }}>"{claim.claimText}"</strong>
                        <span style={{
                          padding: '3px 8px',
                          borderRadius: '6px',
                          fontSize: '11px',
                          fontWeight: 700,
                          background: claim.category === 'DISEASE_CLAIM' ? '#fef2f2' : '#f0fdf4',
                          color: claim.category === 'DISEASE_CLAIM' ? '#991b1b' : '#166534',
                        }}>
                          {claim.category}
                        </span>
                      </div>
                      <div style={{ fontSize: '12px', color: 'var(--on-surface-variant)' }}>
                        <strong>Regulatory Implication:</strong> {claim.regulatoryImplication}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--secondary)' }}>
                        Statutory Reference: {claim.evidenceSource} ({claim.evidenceType})
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="panel" style={{ padding: '20px' }}>
                <h3 style={{ margin: '0 0 16px', fontSize: '16px' }}>Ingredient Verification & Pharmacopoeial Standards</h3>
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                    <thead>
                      <tr style={{ background: 'var(--surface-container)', borderBottom: '1px solid var(--outline-variant)', textAlign: 'left' }}>
                        <th style={{ padding: '10px 12px' }}>Supplied Name</th>
                        <th style={{ padding: '10px 12px' }}>Botanical / Scientific</th>
                        <th style={{ padding: '10px 12px' }}>Pharmacopoeial Status</th>
                        <th style={{ padding: '10px 12px' }}>Verification Status</th>
                        <th style={{ padding: '10px 12px' }}>Uncertainty Note</th>
                      </tr>
                    </thead>
                    <tbody>
                      {result.ingredients.map((ing: IngredientVerificationItem, idx: number) => (
                        <tr key={idx} style={{ borderBottom: '1px solid var(--surface-container-high)' }}>
                          <td style={{ padding: '10px 12px', fontWeight: 600 }}>{ing.suppliedName}</td>
                          <td style={{ padding: '10px 12px', fontStyle: 'italic', color: 'var(--secondary)' }}>
                            {ing.botanicalName || 'Not supplied'}
                          </td>
                          <td style={{ padding: '10px 12px', fontSize: '11px' }}>{ing.pharmacopoeialStatus || 'Standard'}</td>
                          <td style={{ padding: '10px 12px' }}>
                            <span style={{
                              padding: '2px 8px',
                              borderRadius: '6px',
                              fontSize: '11px',
                              fontWeight: 700,
                              background: ing.status === 'VERIFIED' ? '#f0fdf4' : '#fefce8',
                              color: ing.status === 'VERIFIED' ? '#166534' : '#854d0e',
                            }}>
                              {ing.status}
                            </span>
                          </td>
                          <td style={{ padding: '10px 12px', fontSize: '12px', color: 'var(--on-surface-variant)' }}>
                            {ing.uncertainty}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: IP, TK & ABS */}
          {activeTab === 'ip' && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
              <div className="panel" style={{ padding: '20px' }}>
                <h3 style={{ margin: '0 0 16px', fontSize: '16px' }}>Traditional Knowledge & Section 3(p)</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13px' }}>
                  <InfoRow label="TK Status" value={result.traditionalKnowledge.status} />
                  <InfoRow label="Classical Formulation" value={result.traditionalKnowledge.isClassicalFormulation ? 'Yes (Classical Textual Citation Required)' : 'No (Proprietary Combination)'} />
                  <InfoRow label="TKDL Distinction" value={result.traditionalKnowledge.tkdlDistinction} />
                  <InfoRow label="Section 3(p) Patent Exclusion" value={result.traditionalKnowledge.patentImplication} />
                </div>
              </div>

              <div className="panel" style={{ padding: '20px' }}>
                <h3 style={{ margin: '0 0 16px', fontSize: '16px' }}>Biodiversity / ABS Requirements (NBA Section 6)</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13px' }}>
                  <InfoRow label="ABS Status" value={result.biodiversityAbs.status} />
                  <InfoRow label="Biological Resources Used" value={result.biodiversityAbs.biologicalResourceUsed ? 'Yes (Indian Botanical Materials Indicated)' : 'Not Indicated'} />
                  <InfoRow label="NBA Section 6 Mandate" value={result.biodiversityAbs.nbaApprovalRequirement} />
                  <InfoRow label="2023 Amendments & AYUSH" value={result.biodiversityAbs.exemptionsAndAmendments} />
                  <InfoRow label="Action Guidance" value={result.biodiversityAbs.recommendation} />
                </div>
              </div>

              <div className="panel" style={{ padding: '20px', gridColumn: '1 / -1' }}>
                <h3 style={{ margin: '0 0 16px', fontSize: '16px' }}>Potentially Relevant Intellectual Property Protection Routes</h3>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                  gap: '12px',
                }}>
                  {result.ip.routes.map((route: IpRouteAssessment, idx: number) => (
                    <div
                      key={idx}
                      style={{
                        padding: '14px',
                        borderRadius: '12px',
                        background: 'var(--surface-container-low)',
                        border: '1px solid var(--outline-variant)',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                        <strong style={{ fontSize: '14px' }}>{route.route.replace(/_/g, ' ')}</strong>
                        <span style={{
                          padding: '2px 8px',
                          borderRadius: '6px',
                          fontSize: '11px',
                          fontWeight: 700,
                          background: route.relevance === 'HIGH' ? '#f0fdf4' : route.relevance === 'MEDIUM' ? '#eff6ff' : '#f8fafc',
                          color: route.relevance === 'HIGH' ? '#166534' : route.relevance === 'MEDIUM' ? '#1e40af' : '#475569',
                        }}>
                          {route.relevance} Relevance
                        </span>
                      </div>
                      <p style={{ margin: '0 0 8px', fontSize: '12px', color: 'var(--on-surface-variant)' }}>
                        {route.reason}
                      </p>
                      <span style={{ fontSize: '11px', color: 'var(--secondary)' }}>
                        Statute: {route.statutoryReference}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: Next Steps */}
          {activeTab === 'steps' && (
            <div className="panel" style={{ padding: '20px' }}>
              <h3 style={{ margin: '0 0 16px', fontSize: '16px' }}>Recommended Pre-Market Action Plan</h3>
              <ol style={{ margin: 0, paddingLeft: '24px', display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '13px' }}>
                {result.nextSteps.map((step, idx) => (
                  <li key={idx} style={{ lineHeight: 1.5, color: 'var(--on-surface)' }}>
                    {step}
                  </li>
                ))}
              </ol>

              {result.citations?.length > 0 && (
                <div style={{ marginTop: '24px', borderTop: '1px solid var(--outline-variant)', paddingTop: '16px' }}>
                  <h4 style={{ margin: '0 0 12px', fontSize: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span className="material-symbols-outlined" style={{ color: 'var(--primary)' }}>menu_book</span>
                    Authoritative RAG Legal Citations Validated
                  </h4>
                  <EvidenceList citations={result.citations} sources={result.sources} />
                </div>
              )}
            </div>
          )}

          {reportOpen && (
            <div
              className="assessment-report-backdrop"
              role="presentation"
              onMouseDown={(event) => {
                if (event.target === event.currentTarget) setReportOpen(false);
              }}
            >
              <section className="assessment-report-modal" role="dialog" aria-modal="true" aria-labelledby="assessment-report-title">
                <div className="assessment-report-header">
                  <div>
                    <span className="assessment-report-eyebrow">Assessment Report</span>
                    <h3 id="assessment-report-title">Complete 17-Section Regulatory Audit Report</h3>
                  </div>
                  <button className="assessment-report-close" type="button" aria-label="Close report" onClick={() => setReportOpen(false)}>
                    <span className="material-symbols-outlined">close</span>
                  </button>
                </div>

                <div className="assessment-report-actions">
                  <button
                    className="button secondary"
                    type="button"
                    onClick={() => {
                      navigator.clipboard.writeText(result.report);
                      alert('17-Section Report copied to clipboard!');
                    }}
                  >
                    <span className="material-symbols-outlined">content_copy</span>
                    Copy Markdown
                  </button>
                  <button className="button secondary" type="button" onClick={() => window.print()}>
                    <span className="material-symbols-outlined">print</span>
                    Print / PDF
                  </button>
                </div>

                <div className="assessment-report-content">{result.report}</div>
              </section>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ScoreCard({ label, value, subtext }: { label: string; value?: string; subtext: string }) {
  const displayValue = value ?? 'NOT_AVAILABLE';
  const isGood = displayValue === 'CONFIRMED' || displayValue === 'COMPLETE' || displayValue === 'LOW_CONCERN' || displayValue === 'VERIFIED' || displayValue === 'POTENTIAL_ROUTES_IDENTIFIED';
  const isWarn = displayValue === 'PARTIAL' || displayValue === 'REVIEW_REQUIRED' || displayValue === 'LIKELY' || displayValue === 'POTENTIAL' || displayValue === 'POTENTIALLY_RELEVANT';
  const bg = isGood ? '#f0fdf4' : isWarn ? '#eff6ff' : '#fefce8';
  const text = isGood ? '#166534' : isWarn ? '#1e40af' : '#854d0e';

  return (
    <div style={{
      padding: '12px 14px',
      background: bg,
      borderRadius: '12px',
      border: `1px solid ${text}33`,
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'space-between',
    }}>
      <span style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 600, color: 'var(--secondary)' }}>
        {label}
      </span>
      <div style={{ fontSize: '13px', fontWeight: 700, color: text, margin: '4px 0' }}>
        {displayValue.replace(/_/g, ' ')}
      </div>
      <span style={{ fontSize: '10px', color: 'var(--on-surface-variant)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {subtext}
      </span>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ padding: '8px 12px', background: 'var(--surface-container-low)', borderRadius: '8px' }}>
      <span style={{ fontSize: '11px', textTransform: 'uppercase', color: 'var(--secondary)', fontWeight: 600, display: 'block' }}>
        {label}
      </span>
      <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--on-surface)', marginTop: '2px', display: 'block' }}>
        {value}
      </span>
    </div>
  );
}

function getStatusBgColor(status: string) {
  if (status === 'READY_FOR_FURTHER_REGULATORY_REVIEW') return '#f0fdf4';
  if (status === 'REQUIRES_DOCUMENT_COMPLETION') return '#eff6ff';
  if (status === 'REQUIRES_REGULATORY_REVIEW') return '#fffbeb';
  return '#f8fafc';
}

function getStatusBorderColor(status: string) {
  if (status === 'READY_FOR_FURTHER_REGULATORY_REVIEW') return '#bbf7d0';
  if (status === 'REQUIRES_DOCUMENT_COMPLETION') return '#bfdbfe';
  if (status === 'REQUIRES_REGULATORY_REVIEW') return '#fde68a';
  return '#e2e8f0';
}

function getStatusTextColor(status: string) {
  if (status === 'READY_FOR_FURTHER_REGULATORY_REVIEW') return '#166534';
  if (status === 'REQUIRES_DOCUMENT_COMPLETION') return '#1e40af';
  if (status === 'REQUIRES_REGULATORY_REVIEW') return '#92400e';
  return '#475569';
}

function getStatusIcon(status: string) {
  if (status === 'READY_FOR_FURTHER_REGULATORY_REVIEW') return 'check_circle';
  if (status === 'REQUIRES_DOCUMENT_COMPLETION') return 'rule';
  if (status === 'REQUIRES_REGULATORY_REVIEW') return 'policy';
  return 'help';
}

function formatStatusLabel(status: string) {
  return status.replace(/_/g, ' ');
}

function getDocStatusBg(status: string) {
  if (status === 'AVAILABLE') return '#f0fdf4';
  if (status === 'MISSING') return '#fff7ed';
  if (status === 'UNVERIFIED') return '#fefce8';
  return '#f1f5f9';
}

function getDocStatusColor(status: string) {
  if (status === 'AVAILABLE') return '#166534';
  if (status === 'MISSING') return '#c2410c';
  if (status === 'UNVERIFIED') return '#854d0e';
  return '#475569';
}

function splitLines(value: string) {
  return value.split(/\n|,/).map((item) => item.trim()).filter(Boolean);
}

export default FormulationPage;
