import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AskPage } from './AskPage';
import * as questionsApi from '../api/questions';
import * as conversationsApi from '../api/conversations';

vi.mock('../api/questions');
vi.mock('../api/conversations');

describe('AskPage Conversational Chat Experience', () => {
  const auth = { devUserId: 'test-user' };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(conversationsApi.listConversations).mockResolvedValue({
      items: [
        { id: 'conv-1', title: 'Section 3(p) inquiry', created_at: '2026-09-04T10:00:00Z', updated_at: '2026-09-04T10:00:00Z' },
      ],
      page: 0,
      size: 20,
      total_elements: 1,
      total_pages: 1,
    });
  });

  it('renders the welcome hero, sidebar, and 4 clickable suggestions in empty state', async () => {
    render(
      <MemoryRouter>
        <AskPage auth={auth} signedIn={true} />
      </MemoryRouter>
    );

    // Welcome hero
    expect(screen.getByRole('heading', { name: /IP-SAKTI Sahayak/i })).toBeInTheDocument();
    expect(screen.getByText(/Your AI assistant for Intellectual Property/i)).toBeInTheDocument();

    // 4 Clickable suggested prompts
    expect(screen.getByText('What is Section 3(p)?')).toBeInTheDocument();
    expect(screen.getByText('What is traditional knowledge?')).toBeInTheDocument();
    expect(screen.getByText('How can an invention be protected?')).toBeInTheDocument();
    expect(screen.getByText('Explain Access and Benefit Sharing.')).toBeInTheDocument();

    // Sidebar with past conversations
    await waitFor(() => {
      expect(screen.getByText('Section 3(p) inquiry')).toBeInTheDocument();
    });

    // Composer placeholder
    expect(screen.getByPlaceholderText(/Ask IP-SAKTI anything/i)).toBeInTheDocument();
  });

  it('clicking a suggested prompt sends it and renders grounded answer without exposing route names', async () => {
    vi.mocked(conversationsApi.createConversation).mockResolvedValue({
      id: 'conv-new',
      title: 'New Conversation',
      created_at: '2026-09-04T10:00:00Z',
      updated_at: '2026-09-04T10:00:00Z',
    });

    vi.mocked(conversationsApi.askInConversation).mockResolvedValue({
      conversation_id: 'conv-new',
      message_id: 'asst-1',
      user_message_id: 'user-1',
      answer: 'Section 3(p) of the Patents Act, 1970 excludes traditional knowledge from patentability.',
      response_type: 'RAG_GROUNDED',
      route: 'RAG',
      domain: 'PATENT',
      confidence: 0.94,
      abstained: false,
      jurisdiction: 'INDIA',
      language: 'en',
      detected_language: 'en',
      processing_language: 'en',
      intent: 'PATENT',
      citations: [
        {
          document: 'Indian Patents Act, 1970',
          document_id: 'IND-PAT-ACT-1970',
          page: 12,
          section: 'Section 3(p)',
          authority: 'Parliament of India',
          chunk_id: 'chunk-1',
        },
      ],
      sources: [{ document_id: 'IND-PAT-ACT-1970', score: 0.95 }],
      created_at: '2026-09-04T10:00:05Z',
    });

    render(
      <MemoryRouter>
        <AskPage auth={auth} signedIn={true} />
      </MemoryRouter>
    );

    const suggestionBtn = screen.getByText('What is Section 3(p)?');
    fireEvent.click(suggestionBtn);

    await waitFor(() => {
      expect(screen.getByText(/excludes traditional knowledge from patentability/i)).toBeInTheDocument();
    });

    // Verify citations rendered
    expect(screen.getByText(/Indian Patents Act, 1970/i)).toBeInTheDocument();

    // Verify internal routing words are NOT exposed to user
    expect(screen.queryByText('Route: DOMAIN_RAG')).not.toBeInTheDocument();
    expect(screen.queryByText('Route: GENERAL')).not.toBeInTheDocument();
    expect(screen.queryByText('RoutingDecision')).not.toBeInTheDocument();
    expect(screen.queryByText('QueryRoute')).not.toBeInTheDocument();
  });

  it('renders a friendly conversational general answer without fake citations', async () => {
    vi.mocked(conversationsApi.createConversation).mockResolvedValue({
      id: 'conv-new-2',
      title: 'New Conversation',
      created_at: '2026-09-04T10:00:00Z',
      updated_at: '2026-09-04T10:00:00Z',
    });

    vi.mocked(conversationsApi.askInConversation).mockResolvedValue({
      conversation_id: 'conv-new-2',
      message_id: 'asst-2',
      user_message_id: 'user-2',
      answer: 'Hello! I am IP-SAKTI Sahayak, your AI assistant.',
      response_type: 'GENERAL_FALLBACK',
      route: 'GENERAL',
      domain: null,
      confidence: null,
      abstained: false,
      jurisdiction: 'INDIA',
      language: 'en',
      detected_language: 'en',
      processing_language: 'en',
      intent: 'GENERAL',
      citations: [],
      sources: [],
      created_at: '2026-09-04T10:00:05Z',
    });

    render(
      <MemoryRouter>
        <AskPage auth={auth} signedIn={true} />
      </MemoryRouter>
    );

    const textarea = screen.getByPlaceholderText(/Ask IP-SAKTI anything/i);
    fireEvent.change(textarea, { target: { value: 'Hi' } });
    fireEvent.keyDown(textarea, { key: 'Enter', code: 'Enter', shiftKey: false });

    await waitFor(() => {
      expect(screen.getByText('Hello! I am IP-SAKTI Sahayak, your AI assistant.')).toBeInTheDocument();
    });

    // Verify citations are not shown for general conversation
    expect(screen.queryByText(/Evidence & Sources/i)).not.toBeInTheDocument();
    expect(screen.queryByText('Route: GENERAL')).not.toBeInTheDocument();
  });
});
