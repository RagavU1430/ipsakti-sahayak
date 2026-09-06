import { useEffect } from 'react';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

export function KeyboardHelpOverlay({ isOpen, onClose }: Props) {
  useEffect(() => {
    if (!isOpen) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
    };
    document.addEventListener('keydown', handleKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', handleKey);
      document.body.style.overflow = '';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const shortcuts = [
    { group: 'Navigation', items: [
      { key: 'G', desc: 'Go to Ask Page' },
      { key: 'T', desc: 'TK Overlap' },
      { key: 'F', desc: 'Formulation' },
      { key: 'R', desc: 'Regulatory' },
      { key: 'H', desc: 'History' },
      { key: 'A', desc: 'About' },
    ]},
    { group: 'Actions', items: [
      { key: 'N', desc: 'New Query' },
      { key: 'V', desc: 'Voice Assistant' },
      { key: '?', desc: 'This Help' },
      { key: 'M', desc: 'Toggle Menu' },
    ]},
    { group: 'Voice', items: [
      { key: 'Space', desc: 'Toggle Play / Pause' },
      { key: '← →', desc: 'Seek / Navigate' },
    ]},
    { group: 'General', items: [
      { key: 'Escape', desc: 'Close overlays' },
      { key: 'Tab', desc: 'Focus navigation' },
    ]},
  ];

  return (
    <div
      className="keyboard-overlay-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label="Keyboard shortcuts help"
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.6)',
        backdropFilter: 'blur(8px)',
        zIndex: 1000,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '2rem',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--surface)',
          border: '1px solid var(--outline-variant)',
          borderRadius: '16px',
          padding: '2rem 2.5rem',
          maxWidth: '640px',
          width: '100%',
          boxShadow: 'var(--shadow-xl)',
          maxHeight: '80vh',
          overflowY: 'auto',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
          <h2 style={{ fontFamily: 'Cinzel Decorative, serif', fontSize: '1.3rem', margin: 0 }}>
            ⌨️ Keyboard Shortcuts
          </h2>
          <button
            onClick={onClose}
            aria-label="Close keyboard shortcuts"
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--text-dim)',
              fontSize: '1.5rem',
              lineHeight: 1,
            }}
          >
            ✕
          </button>
        </div>

        <div style={{ display: 'grid', gap: '2rem' }}>
          {shortcuts.map((group) => (
            <section key={group.group} aria-label={group.group}>
              <h3 style={{ fontSize: '0.75rem', letterSpacing: '0.15em', textTransform: 'uppercase', color: 'var(--primary)', marginBottom: '0.75rem', fontWeight: 600 }}>
                {group.group}
              </h3>
              <div style={{ display: 'grid', gap: '0.5rem' }}>
                {group.items.map((item) => (
                  <div key={item.key} style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                    <kbd style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      minWidth: '48px',
                      padding: '0.25rem 0.5rem',
                      border: '1px solid var(--outline-variant)',
                      borderRadius: '6px',
                      background: 'var(--surface-container)',
                      fontFamily: 'JetBrains Mono, monospace',
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      color: 'var(--primary)',
                      boxShadow: '0 1px 0 var(--line) inset',
                    }}>
                      {item.key}
                    </kbd>
                    <span style={{ color: 'var(--text-dim)', fontSize: '0.9rem' }}>{item.desc}</span>
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>

        <div style={{ marginTop: '1.5rem', paddingTop: '1rem', borderTop: '1px solid var(--line)', color: 'var(--text-faint)', fontSize: '0.8rem', textAlign: 'center' }}>
          Press <kbd style={{ fontFamily: 'monospace', padding: '0.1rem 0.35rem', border: '1px solid var(--outline-variant)', borderRadius: '4px', fontSize: '0.75rem' }}>Escape</kbd> or click outside to close
        </div>
      </div>
    </div>
  );
}
