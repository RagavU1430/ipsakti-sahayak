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

  it('collapses and expands the sidebar like ChatGPT when the toggle button is clicked', async () => {
    const { fireEvent } = await import('@testing-library/react');
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );

    const sidebar = screen.getByRole('complementary', { name: /Service navigation/i });
    expect(sidebar).not.toHaveClass('collapsed');

    // Click inside-sidebar collapse button
    const collapseBtns = screen.getAllByRole('button', { name: /Collapse sidebar/i });
    expect(collapseBtns.length).toBeGreaterThan(0);
    fireEvent.click(collapseBtns[0]);

    // Sidebar should now be collapsed
    expect(sidebar).toHaveClass('collapsed');

    // Click topbar expand button to bring it back out
    const expandBtn = screen.getByRole('button', { name: /Expand sidebar/i });
    fireEvent.click(expandBtn);

    // Sidebar should now be expanded again
    expect(sidebar).not.toHaveClass('collapsed');
  });
});
