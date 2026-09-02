import { ApiError, friendlyMessage } from '../api/client';

export function ErrorNotice({ error }: { error: unknown }) {
  const message = error instanceof ApiError ? error.detail || friendlyMessage(error.status) : 'IP-SAKTI could not process the request right now. Please try again.';
  const code = error instanceof ApiError ? error.code : undefined;
  return (
    <div className="notice error" role="alert">
      <strong>{code === 'TRANSLATION_UNAVAILABLE' || code?.startsWith('TRANSLATION_') ? 'Translation is not configured' : 'Request could not be completed'}</strong>
      <p>{message}</p>
      {code ? <small>Reference: {code}</small> : null}
    </div>
  );
}
