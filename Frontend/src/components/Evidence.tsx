import type { Citation, Source } from '../api/types';

export function EvidenceList({ citations = [], sources = [] }: { citations?: Citation[]; sources?: Source[] }) {
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
    <div className="evidence-grid">
      <section className="result-section">
        <h3>Evidence</h3>
        {normalizedCitations.length === 0 ? (
          <p className="muted">No citations returned by the backend.</p>
        ) : (
          <ol className="citation-list">
            {normalizedCitations.map((citation, index) => (
              <li key={`${citation.documentId}-${citation.chunkId}-${index}`}>
                <strong>{citation.document || citation.documentId || 'Source document'}</strong>
                <dl>
                  {citation.section ? <><dt>Section</dt><dd>{citation.section}</dd></> : null}
                  {citation.page ? <><dt>Page</dt><dd>{citation.page}</dd></> : null}
                  {citation.documentId ? <><dt>Document ID</dt><dd>{citation.documentId}</dd></> : null}
                  {citation.authority ? <><dt>Authority</dt><dd>{citation.authority}</dd></> : null}
                </dl>
              </li>
            ))}
          </ol>
        )}
      </section>
      <section className="result-section">
        <h3>Sources</h3>
        {normalizedSources.length === 0 ? (
          <p className="muted">No source scores returned.</p>
        ) : (
          <ul className="source-list">
            {normalizedSources.map((source, index) => (
              <li key={`${source.documentId}-${index}`}>
                <span>{source.documentId}</span>
                <code>score: {formatScore(source.score)}</code>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

export function formatScore(score?: number) {
  return typeof score === 'number' ? score.toFixed(2) : 'n/a';
}

export function formatConfidence(confidence?: number) {
  return typeof confidence === 'number' ? `${Math.round(confidence * 100)}%` : 'n/a';
}
