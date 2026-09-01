import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { QuestionResult } from './ResultCard';
import type { QuestionResponse } from '../api/types';

describe('QuestionResult', () => {
  it('shows grounded trust indicator and preserves citation metadata', () => {
    render(<QuestionResult result={response()} />);

    expect(screen.getByText('Evidence-backed answer')).toBeInTheDocument();
    expect(screen.getByText('Trade Marks Act, 1999')).toBeInTheDocument();
    expect(screen.getAllByText('IND-TM-ACT-1999')).toHaveLength(2);
    expect(screen.getByText('91%')).toBeInTheDocument();
  });

  it('shows abstention without inventing citations', () => {
    const abstained = { ...response(), answerType: 'abstained', abstained: true, citations: [], sources: [] } as QuestionResponse;
    render(<QuestionResult result={abstained} />);

    expect(screen.getByText('Insufficient authoritative evidence')).toBeInTheDocument();
    expect(screen.getByText('No citations returned by the backend.')).toBeInTheDocument();
  });
});

function response(): QuestionResponse {
  return {
    answer: 'Grounded answer',
    answerType: 'rag_grounded',
    confidence: 0.91,
    abstained: false,
    jurisdiction: 'INDIA',
    language: 'en',
    detected_language: 'en',
    processing_language: 'en',
    intent: 'TRADEMARK',
    citations: [{ document: 'Trade Marks Act, 1999', documentId: 'IND-TM-ACT-1999', page: 12, section: 'Section 18' }],
    sources: [{ documentId: 'IND-TM-ACT-1999', score: 0.94 }],
  };
}
