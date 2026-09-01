import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { AuthHeaders } from '../api/client';
import { createConversation, listConversations } from '../api/conversations';
import type { ConversationSummary } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { LoadingSteps } from '../components/LoadingSteps';

export function HistoryPage({ auth }: { auth: AuthHeaders }) {
  const [items, setItems] = useState<ConversationSummary[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    let mounted = true;
    listConversations(auth)
      .then((page) => { if (mounted) setItems(page.items || []); })
      .catch((err) => { if (mounted) setError(err); })
      .finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [auth]);

  async function addConversation() {
    setCreating(true);
    setError(null);
    try {
      const created = await createConversation('New IP conversation', auth);
      setItems((current) => [created, ...current]);
    } catch (err) {
      setError(err);
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="page narrow-page">
      <div className="page-heading split-heading">
        <div>
          <h1>Recent Conversations</h1>
          <p>Revisit saved IP questions and their evidence.</p>
        </div>
        <button className="button primary" type="button" onClick={addConversation} disabled={creating}>
          {creating ? 'Creating...' : 'New conversation'}
        </button>
      </div>
      {loading ? <LoadingSteps label="Loading conversations" /> : null}
      {error ? <ErrorNotice error={error} /> : null}
      {!loading && !error && items.length === 0 ? <p className="empty-state">No conversations yet.</p> : null}
      <div className="history-list">
        {items.map((item) => (
          <Link className="history-item" to={`/history/${item.id}`} key={item.id}>
            <strong>{item.title}</strong>
            <span>Last updated {formatDate(item.updated_at)}</span>
            <p>Open saved questions, answers, evidence, and sources.</p>
          </Link>
        ))}
      </div>
    </div>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
}
