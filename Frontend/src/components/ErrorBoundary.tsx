import { Component, ErrorInfo, ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return {
      hasError: true,
      error,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // Log error to console for debugging
    console.error('ErrorBoundary caught an error:', error, errorInfo);

    // In production, you would send this to an error reporting service
    this.setState({
      errorInfo,
    });
  }

  handleReset = (): void => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div
          className="error-boundary-container"
          role="alert"
          aria-live="assertive"
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '60vh',
            padding: '2rem',
            textAlign: 'center',
          }}
        >
          <div
            className="error-boundary-content"
            style={{
              maxWidth: '500px',
              background: 'var(--surface)',
              border: '1px solid var(--line)',
              borderRadius: '12px',
              padding: '2.5rem 2rem',
            }}
          >
            <div
              style={{
                fontSize: '3rem',
                marginBottom: '1rem',
                color: 'var(--error)',
              }}
              aria-hidden="true"
            >
              ⚠️
            </div>
            <h2
              style={{
                fontFamily: 'Poppins, sans-serif',
                fontSize: '1.5rem',
                marginBottom: '0.5rem',
                color: 'var(--on-surface)',
              }}
            >
              Something went wrong
            </h2>
            <p
              style={{
                color: 'var(--text-dim)',
                marginBottom: '1.5rem',
                lineHeight: 1.6,
              }}
            >
              We encountered an unexpected error. The application has been paused to prevent further issues.
            </p>
            {this.state.error && (
              <details
                style={{
                  marginBottom: '1.5rem',
                  textAlign: 'left',
                  background: 'var(--surface-variant)',
                  padding: '0.75rem 1rem',
                  borderRadius: '6px',
                  fontSize: '0.85rem',
                }}
              >
                <summary
                  style={{
                    cursor: 'pointer',
                    fontWeight: 600,
                    marginBottom: '0.5rem',
                    color: 'var(--text)',
                  }}
                >
                  Error details
                </summary>
                <code
                  style={{
                    display: 'block',
                    color: 'var(--error)',
                    fontSize: '0.8rem',
                    wordBreak: 'break-word',
                  }}
                >
                  {this.state.error.toString()}
                </code>
                {this.state.errorInfo && (
                  <pre
                    style={{
                      marginTop: '0.5rem',
                      color: 'var(--text-dim)',
                      fontSize: '0.75rem',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                    }}
                  >
                    {this.state.errorInfo.componentStack}
                  </pre>
                )}
              </details>
            )}
            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'center' }}>
              <button
                onClick={this.handleReset}
                style={{
                  padding: '0.65rem 1.5rem',
                  background: 'var(--primary)',
                  color: 'white',
                  border: 'none',
                  borderRadius: '6px',
                  fontSize: '0.9rem',
                  fontWeight: 500,
                  cursor: 'pointer',
                  transition: 'opacity 0.2s',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.9')}
                onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
              >
                Try again
              </button>
              <button
                onClick={() => (window.location.href = '/')}
                style={{
                  padding: '0.65rem 1.5rem',
                  background: 'transparent',
                  color: 'var(--text)',
                  border: '1px solid var(--line)',
                  borderRadius: '6px',
                  fontSize: '0.9rem',
                  fontWeight: 500,
                  cursor: 'pointer',
                  transition: 'opacity 0.2s',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.7')}
                onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
              >
                Go to home
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
