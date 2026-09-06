import React from 'react';
import type { Citation, Source } from '../api/types';

export const EvidenceList = React.memo(function EvidenceList({ citations = [], sources = [] }: { citations?: Citation[]; sources?: Source[] }) {
  const normalizedCitations = citations.map((citation) => ({
    ...citation,
    documentId: citation.documentId || citation.document_id,
    sourceUrl: citation.sourceUrl || citation.source_url,
    chunkId: citation.chunkId || citation.chunk_id,
  }));
  const normalizedSources = sources.map((source) => ({
    ...source,
    documentId: source.documentId || source.document_id,
  }));

  return (
    <div className="evidence-section">
      <div className="evidence-header">
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>
          menu_book
        </span>
        <span>Authoritative Corpus & Statutory Evidence</span>
        {normalizedCitations.length > 0 ? (
          <span
            style={{
              fontSize: '11.5px',
              padding: '2px 8px',
              borderRadius: '999px',
              background: 'var(--surface-container-high)',
              color: 'var(--primary)',
              fontWeight: 700,
            }}
          >
            {normalizedCitations.length} cited
          </span>
        ) : null}
      </div>

      {normalizedCitations.length === 0 ? (
        <p className="muted" style={{ fontSize: '13px', margin: '4px 0' }}>
          No citations returned by the backend.
        </p>
      ) : (
        <div className="citation-list">
          {normalizedCitations.map((citation, index) => (
            <div key={`${citation.documentId}-${citation.chunkId}-${index}`} className="citation-card">
              <span className="material-symbols-outlined citation-icon">description</span>
              <div className="citation-content">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px', flexWrap: 'wrap' }}>
                  <span className="citation-title">
                    {citation.document || citation.documentId || 'Official Reference Document'}
                  </span>
                  {citation.documentId ? (
                    <span className="evidence-tag doc-id">{citation.documentId}</span>
                  ) : null}
                </div>
                <div className="citation-meta-tags">
                  {citation.section ? (
                    <span className="evidence-tag">
                      <strong>Section:</strong> {citation.section}
                    </span>
                  ) : null}
                  {citation.page ? (
                    <span className="evidence-tag">
                      <strong>Page:</strong> {citation.page}
                    </span>
                  ) : null}
                  {citation.authority ? (
                    <span className="evidence-tag authority">
                      🏛️ {citation.authority}
                    </span>
                  ) : null}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {normalizedSources.length > 0 ? (
        <div style={{ marginTop: '10px' }}>
          <span
            style={{
              fontSize: '12px',
              fontWeight: 700,
              color: 'var(--secondary)',
              textTransform: 'uppercase',
              letterSpacing: '0.03em',
              display: 'block',
              marginBottom: '6px',
            }}
          >
            Retrieved Corpus Sources & Relevance Score
          </span>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {normalizedSources.map((source, index) => (
              <div
                key={`${source.documentId}-${index}`}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '8px',
                  background: 'var(--surface-container-low)',
                  border: '1px solid var(--outline-variant)',
                  borderRadius: '4px',
                  padding: '4px 10px',
                  fontSize: '12px',
                }}
              >
                <span style={{ fontWeight: 600, color: 'var(--on-surface)' }}>{source.documentId}</span>
                <span
                  style={{
                    background: 'var(--surface-container-high)',
                    padding: '2px 6px',
                    borderRadius: '3px',
                    fontSize: '11px',
                    fontFamily: 'monospace',
                    color: 'var(--primary)',
                    fontWeight: 700,
                  }}
                >
                  score: {formatScore(source.score)}
                </span>
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
});

export function formatScore(score?: number) {
  return typeof score === 'number' ? score.toFixed(2) : 'n/a';
}

export function formatConfidence(confidence?: number | null) {
  return typeof confidence === 'number' ? `${Math.round(confidence * 100)}%` : 'n/a';
}
