export interface ConversationMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  metadata: Record<string, unknown>;
}

export interface InsuranceInfo {
  provider: string | null;
  policyNumber: string | null;
  verified: boolean | null;
  coverageStatus: string | null;
  copayAmount: string | null;
  preAuthRequired: boolean | null;
}

export interface AppointmentInfo {
  scheduledTime: string | null;
  department: string | null;
  provider: string | null;
  notes: string | null;
}

export interface PatientProfile {
  patientId: string;
  name: string | null;
  age: number | null;
  gender: string | null;
  symptoms: string[];
  condition: string | null;
  urgencyLevel: string | null;
  insurance: InsuranceInfo | null;
  appointment: AppointmentInfo | null;
  extractedData: Record<string, unknown>;
  lastUpdated: string;
}

export type StepStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';

export interface ReasoningStep {
  stepId: string;
  agent: string;
  action: string;
  reasoning: string;
  status: StepStatus;
  input: Record<string, unknown> | null;
  output: Record<string, unknown> | null;
  fallbackPlan: string | null;
  timestamp: string;
}

export type WorkflowStatus =
  | 'IDLE'
  | 'ANALYZING'
  | 'AWAITING_DATA'
  | 'EXECUTING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_RECOVERY'
  | 'FAILED'
  | 'RECOVERING';

export interface WorkflowState {
  workflowId: string;
  patientId: string | null;
  status: WorkflowStatus;
  currentStep: string | null;
  reasoningChain: ReasoningStep[];
  context: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface BrainResponse {
  workflow_id: string;
  status: string;
  message: string;
  reasoning_chain: ReasoningStep[];
  patient_profile: PatientProfile | null;
  missing_fields: string[];
  session_id: string;
}

export interface ConversationContext {
  sessionId: string;
  patientId: string | null;
  history: ConversationMessage[];
  currentIntent: string | null;
  missingDataFields: string[];
  contextData: Record<string, unknown>;
  createdAt: string;
  lastActivity: string;
}

export interface WorkflowStatusEvent {
  workflow_id: string;
  status: string;
}

export interface AssistantMessageEvent {
  message: string;
}

export interface ThinkingEvent {
  agent: string;
  action: string;
  content: string;
  type: string;
}

export interface ConnectedEvent {
  session: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: string;
  metadata?: Record<string, unknown>;
}

