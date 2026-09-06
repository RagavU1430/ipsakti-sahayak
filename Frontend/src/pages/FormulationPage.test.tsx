import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { FormulationPage } from './FormulationPage';
import * as formulationsApi from '../api/formulations';
import type { ProductReadinessResponse } from '../api/types';

vi.mock('../api/formulations');

describe('FormulationPage 5-Step Product Readiness Wizard', () => {
  const auth = { devUserId: 'test-user' };

  const mockResponse: ProductReadinessResponse = {
    product: {
      name: 'Triphala Guggulu Tablets',
      dosageForm: 'Tablet',
      intendedUse: 'Joint comfort',
      manufacturer: 'Dabur India Ltd',
      countryOfManufacture: 'India',
      targetMarket: 'India',
    },
    classification: {
      category: 'CLASSICAL_AYURVEDIC_FORMULATION',
      internalCode: 'CLASSICAL_DRUG',
      confidence: 0.94,
      status: 'CONFIRMED',
      rationale: 'Formulated strictly according to classical treatise Sharangadhara Samhita.',
    },
    regulatory: {
      pathway: 'AYUSH_CLASSICAL_DRUG',
      governingFramework: 'Drugs and Cosmetics Act, 1940 (Chapter IV-A)',
      licensingAuthority: 'State Licensing Authority (AYUSH / ISM)',
      standards: 'Ayurvedic Pharmacopoeia of India (API)',
    },
    documents: {
      totalProvided: 2,
      documents: [
        { id: 'd1', name: 'Formulation Specification', type: 'FORMULATION_SPECIFICATION', status: 'DOCUMENT_PROVIDED' },
      ],
      verificationNotice: 'Candidate evidence',
    },
    ingredients: [
      {
        suppliedName: 'Haritaki',
        botanicalName: 'Terminalia chebula',
        quantityRatio: '100mg',
        status: 'VERIFIED',
        uncertainty: 'Pharmacopoeially documented in API Part I, Vol I.',
      },
    ],
    claims: [
      {
        claimText: 'Supports joint comfort',
        category: 'TRADITIONAL_USE',
        evidenceSource: 'First Schedule Texts',
        evidenceType: 'STATUTORY',
        regulatoryImplication: 'Permissible under classical AYUSH indication.',
        hasAuthoritativeEvidence: true,
      },
    ],
    traditionalKnowledge: {
      status: 'CONFIRMED',
      isClassicalFormulation: true,
      traditionalIngredientsDetected: true,
      tkdlDistinction: 'Traditional knowledge documented in classical texts.',
      patentImplication: 'Excluded under Section 3(p) as traditional knowledge.',
      authoritativeReference: 'First Schedule treaties',
    },
    biodiversityAbs: {
      status: 'POTENTIALLY_RELEVANT',
      biologicalResourceUsed: true,
      sourceOfIngredients: 'India',
      nbaApprovalRequirement: 'Section 6 NBA approval required for IP filings.',
      exemptionsAndAmendments: 'AYUSH practitioners benefit from access exemptions.',
      recommendation: 'Verify biological traceability.',
    },
    ip: {
      routes: [
        {
          route: 'TRADEMARK',
          relevance: 'HIGH',
          reason: 'Brand name protectable under Class 5.',
          statutoryReference: 'Trade Marks Act, 1999',
        },
        {
          route: 'PATENT',
          relevance: 'LOW',
          reason: 'Excluded under Section 3(p).',
          statutoryReference: 'Section 3(p), Patents Act, 1970',
        },
      ],
      summary: 'Trademark is primary; patent excluded under 3(p).',
    },
    gaps: [
      {
        documentArea: 'Product Formulation Specification',
        status: 'AVAILABLE',
        importance: 'REQUIRED',
        reason: 'Specification details provided.',
        statutoryReference: 'Rule 153',
      },
      {
        documentArea: 'Quality Testing & NABL Certificate',
        status: 'MISSING',
        importance: 'REQUIRED',
        reason: 'Heavy metal and microbial testing report required.',
        statutoryReference: 'Schedule T GMP',
      },
    ],
    nextSteps: [
      '1. Finalize regulatory positioning under AYUSH classical drug pathway.',
      '2. Document textual citation from Sharangadhara Samhita.',
    ],
    citations: [],
    sources: [],
    confidence: 0.94,
    status: 'READY_FOR_FURTHER_REGULATORY_REVIEW',
    abstained: false,
    questions: [],
    report: '# AYURVEDA PRODUCT ASSESSMENT\n\n## 1. Product Summary\n- Product Name: Triphala Guggulu Tablets',
    scores: {
      regulatoryClassification: 'CONFIRMED',
      documentCompleteness: 'PARTIAL',
      claims: 'LOW_CONCERN',
      ingredientVerification: 'VERIFIED',
      traditionalKnowledge: 'CONFIRMED',
      biodiversityAbs: 'POTENTIALLY_RELEVANT',
      intellectualProperty: 'POTENTIAL_ROUTES_IDENTIFIED',
      overall: 'READY_FOR_FURTHER_REGULATORY_REVIEW',
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders Step 1 with sample preloading buttons and navigates through the wizard', async () => {
    render(
      <MemoryRouter>
        <FormulationPage auth={auth} />
      </MemoryRouter>
    );

    expect(screen.getByText(/Ayurveda Product Market-Readiness & Compliance Engine/i)).toBeInTheDocument();
    expect(screen.getByText('1. Product Details')).toBeInTheDocument();

    // Click sample button
    const sampleBtn = screen.getByText('🌿 Classical Triphala');
    fireEvent.click(sampleBtn);

    // Verify fields were populated
    expect(screen.getByDisplayValue('Triphala Guggulu Tablets')).toBeInTheDocument();

    // Navigate to Step 2
    const nextBtn1 = screen.getByText(/Next: Documents & Evidence/i);
    fireEvent.click(nextBtn1);

    // Step 2 content
    expect(screen.getByText(/Step 2: Available Documents & Regulatory Records/i)).toBeInTheDocument();

    // Navigate to Step 3
    const nextBtn2 = screen.getByText(/Next: Claims & Positioning/i);
    fireEvent.click(nextBtn2);

    // Step 3 content
    expect(screen.getByText(/Step 3: Marketing & Therapeutic Claims/i)).toBeInTheDocument();

    // Navigate to Step 4
    const nextBtn3 = screen.getByText(/Next: Pre-Flight Review/i);
    fireEvent.click(nextBtn3);

    // Step 4 content
    expect(screen.getByText(/Step 4: Pre-Flight Audit Review/i)).toBeInTheDocument();
    expect(screen.getByText(/Run Market-Readiness & Compliance Analysis/i)).toBeInTheDocument();
  });

  it('submits formulation analysis and renders 5th step assessment report with scores and tabs', async () => {
    vi.mocked(formulationsApi.analyzeProductReadiness).mockResolvedValue(mockResponse);

    render(
      <MemoryRouter>
        <FormulationPage auth={auth} />
      </MemoryRouter>
    );

    // Load sample and jump to step 4
    fireEvent.click(screen.getByText('🌿 Classical Triphala'));
    fireEvent.click(screen.getByText(/Next: Documents & Evidence/i));
    fireEvent.click(screen.getByText(/Next: Claims & Positioning/i));
    fireEvent.click(screen.getByText(/Next: Pre-Flight Review/i));

    const analyzeBtn = screen.getByText(/Run Market-Readiness & Compliance Analysis/i);
    fireEvent.click(analyzeBtn);

    await waitFor(() => {
      expect(screen.getByText('READY FOR FURTHER REGULATORY REVIEW')).toBeInTheDocument();
    });

    // Verify discrete readiness score cards
    expect(screen.getAllByText('CLASSICAL AYURVEDIC FORMULATION').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('CONFIRMED').length).toBeGreaterThanOrEqual(1);

    // Switch to Document Gaps tab
    const gapsTab = screen.getByText(/2. Document Gaps/i);
    fireEvent.click(gapsTab);

    expect(screen.getByText('13-Point Statutory Document Gap Analysis')).toBeInTheDocument();
    expect(screen.getByText('Quality Testing & NABL Certificate')).toBeInTheDocument();
    expect(screen.getByText('MISSING')).toBeInTheDocument();

    // Switch to Report tab
    const reportTab = screen.getByText(/6. Full 17-Section Report/i);
    fireEvent.click(reportTab);

    expect(screen.getByText('Copy Markdown')).toBeInTheDocument();
  });
});
