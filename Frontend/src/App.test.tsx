import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from './App';

describe('App', () => {
  it('renders a calm landing page with primary product actions', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: /IP Knowledge/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Ask an IP Question/i })).toBeInTheDocument();
    expect(screen.getByText(/evidence-backed answers/i)).toBeInTheDocument();
  });

  it('protects conversation history until signed in', () => {
    render(
      <MemoryRouter initialEntries={['/history']}>
        <App />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: /Sign in to save conversations/i })).toBeInTheDocument();
  });
});
