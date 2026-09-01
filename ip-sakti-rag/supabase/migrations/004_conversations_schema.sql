-- IP-SAKTI Phase 7 Conversation Management Migration
-- Creates tables for users, conversations, messages, message_citations, and message_sources
-- Includes Row Level Security (RLS) policies and performance indexes

CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  external_auth_id text UNIQUE NOT NULL,
  email text,
  display_name text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conversations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title text NOT NULL DEFAULT 'New Conversation',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role text NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
  content text NOT NULL,
  response_type text,
  confidence double precision,
  abstained boolean,
  jurisdiction text,
  language text DEFAULT 'en',
  detected_language text,
  processing_language text,
  intent text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS message_citations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id uuid NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  document text,
  document_id text,
  page text,
  section text,
  authority text,
  source_url text,
  chunk_id text,
  ordinal integer NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS message_sources (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id uuid NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  document_id text NOT NULL,
  score double precision NOT NULL,
  ordinal integer NOT NULL DEFAULT 0
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_users_external_auth_id ON users (external_auth_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user_id ON conversations (user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_updated_at ON conversations (updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON messages (conversation_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_message_citations_message_id ON message_citations (message_id);
CREATE INDEX IF NOT EXISTS idx_message_sources_message_id ON message_sources (message_id);

-- Row Level Security (RLS)
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE message_citations ENABLE ROW LEVEL SECURITY;
ALTER TABLE message_sources ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS users_owner_all ON users;
CREATE POLICY users_owner_all ON users
  FOR ALL TO authenticated
  USING (external_auth_id = auth.uid()::text)
  WITH CHECK (external_auth_id = auth.uid()::text);

DROP POLICY IF EXISTS conversations_owner_all ON conversations;
CREATE POLICY conversations_owner_all ON conversations
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM users u
    WHERE u.id = conversations.user_id
      AND u.external_auth_id = auth.uid()::text
  ))
  WITH CHECK (EXISTS (
    SELECT 1 FROM users u
    WHERE u.id = conversations.user_id
      AND u.external_auth_id = auth.uid()::text
  ));

DROP POLICY IF EXISTS messages_owner_all ON messages;
CREATE POLICY messages_owner_all ON messages
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM conversations c
    JOIN users u ON u.id = c.user_id
    WHERE c.id = messages.conversation_id
      AND u.external_auth_id = auth.uid()::text
  ))
  WITH CHECK (EXISTS (
    SELECT 1 FROM conversations c
    JOIN users u ON u.id = c.user_id
    WHERE c.id = messages.conversation_id
      AND u.external_auth_id = auth.uid()::text
  ));

DROP POLICY IF EXISTS message_citations_owner_all ON message_citations;
CREATE POLICY message_citations_owner_all ON message_citations
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM messages m
    JOIN conversations c ON c.id = m.conversation_id
    JOIN users u ON u.id = c.user_id
    WHERE m.id = message_citations.message_id
      AND u.external_auth_id = auth.uid()::text
  ));

DROP POLICY IF EXISTS message_sources_owner_all ON message_sources;
CREATE POLICY message_sources_owner_all ON message_sources
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM messages m
    JOIN conversations c ON c.id = m.conversation_id
    JOIN users u ON u.id = c.user_id
    WHERE m.id = message_sources.message_id
      AND u.external_auth_id = auth.uid()::text
  ));