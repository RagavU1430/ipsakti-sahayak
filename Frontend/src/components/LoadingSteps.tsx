const steps = ['Searching authoritative sources...', 'Reviewing relevant evidence...', 'Preparing answer...'];

export function LoadingSteps({ label = 'Processing request' }: { label?: string }) {
  return (
    <div className="loading-panel" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <div>
        <strong>{label}</strong>
        <ul>
          {steps.map((step) => <li key={step}>{step}</li>)}
        </ul>
      </div>
    </div>
  );
}
