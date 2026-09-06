import React from 'react';

interface FormattedTextProps {
  content?: string | null;
  className?: string;
  style?: React.CSSProperties;
}

/**
 * FormattedText parses markdown-style text, replacing '**text**' with styled bold text/lines,
 * converting bullet points, headings, and preserving readable line breaks.
 */
export function FormattedText({ content, className, style }: FormattedTextProps) {
  if (!content) return null;

  // Normalize markdown text so attached headings (e.g. "...வேண்டும்.## 1. ") and bullets have proper line breaks
  const normalized = content
    .replace(/\r\n/g, '\n')
    .replace(/([^\n])(#{1,6}\s+)/g, '$1\n\n$2')
    .replace(/([^\n])([-*•]\s+)/g, '$1\n$2');

  const lines = normalized.split('\n');

  return (
    <div className={className} style={{ ...style }}>
      {lines.map((line, lineIdx) => {
        const trimmed = line.trim();

        // Empty spacer line
        if (!trimmed) {
          return <div key={lineIdx} style={{ height: '0.6em' }} />;
        }

        // Horizontal divider: *** or ---
        if (/^(\*{3,}|-{3,}|_{3,})$/.test(trimmed)) {
          return (
            <hr
              key={lineIdx}
              style={{
                margin: '10px 0',
                border: 'none',
                borderTop: '2px solid var(--outline-variant, #e0e0e0)',
              }}
            />
          );
        }

        // Markdown headings: #, ##, ###
        const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/);
        if (headingMatch) {
          const level = headingMatch[1].length;
          return (
            <div
              key={lineIdx}
              style={{
                fontWeight: 700,
                color: 'var(--primary, #1a237e)',
                fontSize: level === 1 ? '1.15em' : level === 2 ? '1.08em' : '1.02em',
                margin: '8px 0 4px',
              }}
            >
              {renderInline(headingMatch[2])}
            </div>
          );
        }

        // Entire line is bold like **1. Heading:** or **Section 3(p):**
        const fullBoldLineMatch = trimmed.match(/^\*\*([^*]+)\*\*$/);
        if (fullBoldLineMatch) {
          return (
            <div
              key={lineIdx}
              style={{
                fontWeight: 700,
                color: 'var(--on-surface, #1e293b)',
                fontSize: '1.02em',
                margin: '6px 0 2px',
              }}
            >
              {fullBoldLineMatch[1]}
            </div>
          );
        }

        // Bullet point lines: -, *, •
        const bulletMatch = trimmed.match(/^[-*•]\s+(.+)$/);
        if (bulletMatch) {
          return (
            <div
              key={lineIdx}
              style={{
                display: 'flex',
                alignItems: 'baseline',
                gap: '8px',
                margin: '3px 0 3px 12px',
                lineHeight: '1.5',
              }}
            >
              <span style={{ color: 'var(--primary, #2563eb)', fontSize: '0.85em', userSelect: 'none' }}>•</span>
              <span style={{ flex: 1 }}>{renderInline(bulletMatch[1])}</span>
            </div>
          );
        }

        // Numbered list item: 1. or 1)
        const numMatch = trimmed.match(/^(\d+[\.\)])\s+(.+)$/);
        if (numMatch) {
          return (
            <div
              key={lineIdx}
              style={{
                display: 'flex',
                alignItems: 'baseline',
                gap: '8px',
                margin: '3px 0 3px 12px',
                lineHeight: '1.5',
              }}
            >
              <span style={{ fontWeight: 600, minWidth: '1.4em', color: 'var(--secondary, #475569)' }}>
                {numMatch[1]}
              </span>
              <span style={{ flex: 1 }}>{renderInline(numMatch[2])}</span>
            </div>
          );
        }

        // Standard text line with potential inline bold / code
        return (
          <div key={lineIdx} style={{ lineHeight: '1.55', margin: '2px 0' }}>
            {renderInline(line)}
          </div>
        );
      })}
    </div>
  );
}

// Convert inline formatting: **bold**, `code`, *italic*
function renderInline(text: string): React.ReactNode[] {
  const parts: React.ReactNode[] = [];
  // Matches **bold**, `code`, and *italic*
  const regex = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)/g;

  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(text.substring(lastIndex, match.index));
    }

    const token = match[0];
    if (token.startsWith('**') && token.endsWith('**')) {
      parts.push(
        <strong
          key={match.index}
          style={{
            fontWeight: 700,
            color: 'var(--on-surface, inherit)',
          }}
        >
          {token.slice(2, -2)}
        </strong>
      );
    } else if (token.startsWith('`') && token.endsWith('`')) {
      parts.push(
        <code
          key={match.index}
          style={{
            background: 'var(--surface-container-high, #f1f5f9)',
            padding: '1px 5px',
            borderRadius: '4px',
            fontSize: '0.9em',
            fontFamily: 'monospace',
          }}
        >
          {token.slice(1, -1)}
        </code>
      );
    } else if (token.startsWith('*') && token.endsWith('*')) {
      parts.push(<em key={match.index}>{token.slice(1, -1)}</em>);
    }

    lastIndex = regex.lastIndex;
  }

  if (lastIndex < text.length) {
    parts.push(text.substring(lastIndex));
  }

  return parts.length > 0 ? parts : [text];
}
