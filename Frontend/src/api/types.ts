export type Language = 'en' | 'hi' | 'ta' | 'te' | 'kn' | 'ml';
export type Jurisdiction = 'INDIA' | 'INTERNATIONAL' | 'AUTO';
export type AnswerType = 'rag_grounded' | 'general_fallback' | 'abstained';

export interface ApiErrorBody {
  error?: string;
  code?: string;
  detail?: string;
  message?: string;
}

export interface Citation {
  document?: string;
  documentId?: string;
  document_id?: string;
  page?: number;
  section?: string;
  authority?: string;
  sourceUrl?: string;
  source_url?: string;
  chunkId?: string;
  chunk_id?: string;
}

export interface Source {
  documentId?: string;
  document_id?: string;
  score?: number;
}

export interface QuestionRequest {
  question: string;
  jurisdiction?: Jurisdiction;
  language?: Language;
}

export interface QuestionResponse {
  answer: string;
  answerType: AnswerType;
  confidence: number;
  abstained: boolean;
  jurisdiction: Jurisdiction;
  language: Language;
  detected_language?: Language;
  processing_language?: Language;
  intent?: string;
  citations: Citation[];
  sources: Source[];
}

export interface FormulationRequest {
  productName: string;
  ingredients?: string[];
  dosageForm?: string;
  intendedUse?: string;
  claims?: string[];
  manufacturingMethod?: string;
  classicalReference?: string;
  traditionalUse?: boolean;
  commercialIntent?: boolean;
  targetMarket?: string;
  country?: string;
  existingLicense?: string;
  knownClassification?: string;
  language?: Language;
}

export interface RegulatoryRoute {
  route: string;
  domains: string[];
  jurisdiction: string;
}

export interface FormulationResponse {
  classification: string | null;
  confidence: number;
  needsClarification: boolean;
  questions: string[];
  reason: string;
  status: string;
  regulatoryRoute?: RegulatoryRoute | null;
  citations: Citation[];
  sources: Source[];
  language?: Language;
  detected_language?: Language;
  processing_language?: Language;
}

export interface RegulatoryAnalysisRequest {
  productName: string;
  ingredients?: string[];
  dosageForm?: string;
  intendedUse?: string;
  claims?: string[];
  traditionalKnowledge?: boolean;
  classicalReference?: string;
  biologicalResources?: boolean;
  resourceOrigin?: string;
  targetMarket?: string;
  jurisdiction?: Jurisdiction;
  formulationNovelty?: boolean;
  knownIngredients?: boolean;
  synergisticEffectClaimed?: boolean;
  geneticResources?: boolean;
  language?: Language;
}

export interface RegulatoryEngineResult {
  engine: 'SECTION_3P' | 'SECTION_3E' | 'ABS' | 'GRATK';
  status: 'NOT_INDICATED' | 'POTENTIALLY_APPLICABLE' | 'REVIEW_RECOMMENDED' | 'INSUFFICIENT_EVIDENCE';
  confidence: number;
  reason: string;
  considerations: string[];
  resourceType?: string;
  citations: Citation[];
  sources: Source[];
}

export interface RegulatoryAnalysisResponse {
  jurisdiction: Jurisdiction;
  overallStatus: RegulatoryEngineResult['status'];
  engines: RegulatoryEngineResult[];
  overallConfidence: number;
  needsClarification: boolean;
  questions: string[];
  reason: string;
  language?: Language;
  detected_language?: Language;
  processing_language?: Language;
}

export interface ConversationSummary {
  id: string;
  title: string;
  created_at: string;
  updated_at: string;
}

export interface ConversationPage {
  items: ConversationSummary[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface ConversationMessage {
  id?: string;
  role: 'user' | 'assistant';
  content: string;
  response_type?: AnswerType | string;
  confidence?: number;
  abstained?: boolean;
  jurisdiction?: string;
  language?: string;
  detected_language?: string;
  processing_language?: string;
  intent?: string;
  citations?: Citation[];
  sources?: Source[];
  created_at?: string;
}

export interface ConversationDetail {
  id: string;
  title: string;
  created_at: string;
  updated_at: string;
  messages: ConversationMessage[];
}

export interface ConversationMessageResponse {
  conversation_id: string;
  message_id: string;
  user_message_id: string;
  answer: string;
  response_type: string;
  confidence: number;
  abstained: boolean;
  jurisdiction: Jurisdiction;
  language: Language;
  detected_language?: Language;
  processing_language?: Language;
  intent?: string;
  citations: Citation[];
  sources: Source[];
  created_at: string;
}
