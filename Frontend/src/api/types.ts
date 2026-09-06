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
  route?: 'GENERAL' | 'RAG' | 'AMBIGUOUS' | 'UNSUPPORTED';
  domain?: string | null;
  routing_reason?: string;
  confidence: number | null;
  abstained: boolean;
  jurisdiction: Jurisdiction;
  language: Language;
  detected_language?: Language;
  processing_language?: Language;
  intent?: string;
  citations: Citation[];
  sources: Source[];
}

export interface ProvidedDocument {
  id: string;
  name: string;
  type: string;
  status: string;
  notes?: string;
  sourceUrl?: string;
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
  ingredientRatios?: string[];
  sourceOfIngredients?: string;
  manufacturer?: string;
  countryOfManufacture?: string;
  documents?: ProvidedDocument[];
}

export interface DocumentGapItem {
  documentArea: string;
  status: string;
  importance: string;
  reason: string;
  statutoryReference: string;
}

export interface ClaimAnalysisItem {
  claimText: string;
  category: string;
  evidenceSource: string;
  evidenceType: string;
  regulatoryImplication: string;
  hasAuthoritativeEvidence: boolean;
}

export interface IngredientVerificationItem {
  suppliedName: string;
  botanicalName?: string;
  quantityRatio?: string;
  formulationRole?: string;
  status: string;
  pharmacopoeialStatus?: string;
  biologicalResourceRelevance?: string;
  uncertainty: string;
}

export interface IpRouteAssessment {
  route: string;
  relevance: string;
  reason: string;
  statutoryReference: string;
}

export interface ReadinessScores {
  regulatoryClassification: string;
  documentCompleteness: string;
  claims: string;
  ingredientVerification: string;
  /** Backend response keys for the TK, ABS, and IP score cards. */
  tk?: string;
  abs?: string;
  ip?: string;
  /** Legacy aliases kept for older fixtures/responses. */
  traditionalKnowledge?: string;
  biodiversityAbs?: string;
  intellectualProperty?: string;
  overall: string;
}

export interface ProductReadinessResponse {
  product: Record<string, unknown>;
  classification: {
    category: string;
    internalCode: string;
    confidence: number;
    status: string;
    rationale: string;
  };
  regulatory: {
    pathway: string;
    governingFramework: string;
    licensingAuthority: string;
    standards: string;
  };
  documents: {
    totalProvided: number;
    documents: ProvidedDocument[];
    verificationNotice: string;
  };
  ingredients: IngredientVerificationItem[];
  claims: ClaimAnalysisItem[];
  traditionalKnowledge: {
    status: string;
    isClassicalFormulation: boolean;
    traditionalIngredientsDetected: boolean;
    tkdlDistinction: string;
    patentImplication: string;
    authoritativeReference: string;
  };
  biodiversityAbs: {
    status: string;
    biologicalResourceUsed: boolean;
    sourceOfIngredients: string;
    nbaApprovalRequirement: string;
    exemptionsAndAmendments: string;
    recommendation: string;
  };
  ip: {
    routes: IpRouteAssessment[];
    summary: string;
  };
  gaps: DocumentGapItem[];
  nextSteps: string[];
  citations: Citation[];
  sources: Source[];
  confidence: number;
  status: string;
  abstained: boolean;
  questions: string[];
  report: string;
  scores: ReadinessScores;
  language?: Language;
  detected_language?: Language;
  processing_language?: Language;
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

export type TkOverlapClassification =
  | 'NO_TK_OVERLAP_FOUND'
  | 'POTENTIAL_TK_OVERLAP'
  | 'STRONG_TK_OVERLAP'
  | 'INSUFFICIENT_EVIDENCE';

export type TkOverlapType =
  | 'INGREDIENT_OVERLAP'
  | 'TRADITIONAL_USE_OVERLAP'
  | 'FORMULATION_OVERLAP'
  | 'PREPARATION_METHOD_OVERLAP'
  | 'PROCESS_OVERLAP'
  | 'KNOWLEDGE_DOMAIN_OVERLAP'
  | 'GEOGRAPHIC_OR_COMMUNITY_OVERLAP'
  | 'BIOLOGICAL_RESOURCE_OVERLAP';

export interface TkEvidenceItem {
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
  score?: number;
}

export interface TkOverlapRequest {
  description: string;
  language?: Language;
}

export interface TkOverlapResponse {
  classification: TkOverlapClassification;
  confidence: number;
  overlap_types: TkOverlapType[];
  explanation: string;
  evidence: TkEvidenceItem[];
  recommendation: string;
  citations: Citation[];
  sources: Source[];
  abstained: boolean;
  language: Language;
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
  route?: 'GENERAL' | 'RAG' | 'AMBIGUOUS' | 'UNSUPPORTED';
  domain?: string | null;
  confidence: number | null;
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

export interface VoiceAskResponse {
  transcript: string;
  language: Language;
  jurisdiction: Jurisdiction;
  answer: string;
  answerType: AnswerType;
  route?: 'GENERAL' | 'RAG' | 'AMBIGUOUS' | 'UNSUPPORTED';
  domain?: string | null;
  confidence: number | null;
  citations: Citation[];
  sources: Source[];
  abstained: boolean;
  audioBase64?: string | null;
  audioMimeType?: string | null;
  status: string;
  latencyMs: number;
  conversationId?: string | null;
  userMessageId?: string | null;
  assistantMessageId?: string | null;
}

export interface VoiceHealthResponse {
  status: string;
  sttProvider: string;
  ttsProvider: string;
  sttConfigured: boolean;
  ttsConfigured: boolean;
  maxAudioBytes: number;
  allowedMimeTypes: string[];
  supportedLanguages: Language[];
}

export interface FormulationChatRequest {
  message: string;
  reportContext?: string;
  language?: Language;
}

export interface FormulationChatResponse {
  reply: string;
  language?: Language;
  suggestedQuestions?: string[];
}

