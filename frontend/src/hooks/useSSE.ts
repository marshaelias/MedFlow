import { useEffect, useRef, useCallback } from 'react';
import { createEventSource } from '@/services/api';
import type {
  ReasoningStep,
  PatientProfile,
  WorkflowStatusEvent,
  AssistantMessageEvent,
  ThinkingEvent,
  ConnectedEvent,
} from '@/types/api';

export interface SSEState {
  reasoningSteps: ReasoningStep[];
  patientProfile: PatientProfile | null;
  workflowStatus: string | null;
  thinking: { agent: string; action: string; content: string } | null;
  connected: boolean;
}

type SSEHandler = (state: SSEState) => void;

export function useSSE(sessionId: string | null, onEvent: SSEHandler) {
  const esRef = useRef<EventSource | null>(null);
  const stateRef = useRef<SSEState>({
    reasoningSteps: [],
    patientProfile: null,
    workflowStatus: null,
    thinking: null,
    connected: false,
  });

  const update = useCallback((patch: Partial<SSEState>) => {
    stateRef.current = { ...stateRef.current, ...patch };
    onEvent(stateRef.current);
  }, [onEvent]);

  useEffect(() => {
    if (!sessionId) return;

    const es = createEventSource(sessionId);
    esRef.current = es;

    es.addEventListener('connected', (e) => {
      const data = JSON.parse((e as MessageEvent).data) as ConnectedEvent;
      update({ connected: true });
      console.log('SSE connected:', data.session);
    });

    es.addEventListener('reasoning_step', (e) => {
      const step = JSON.parse((e as MessageEvent).data) as ReasoningStep;
      update({
        reasoningSteps: [...stateRef.current.reasoningSteps, step],
      });
    });

    es.addEventListener('patient_update', (e) => {
      const profile = JSON.parse((e as MessageEvent).data) as PatientProfile;
      update({ patientProfile: profile });
    });

    es.addEventListener('workflow_status', (e) => {
      const data = JSON.parse((e as MessageEvent).data) as WorkflowStatusEvent;
      update({ workflowStatus: data.status });
    });

    es.addEventListener('assistant_message', (e) => {
      const data = JSON.parse((e as MessageEvent).data) as AssistantMessageEvent;
      update({ thinking: null });
    });

    es.addEventListener('thinking', (e) => {
      const data = JSON.parse((e as MessageEvent).data) as ThinkingEvent;
      update({ thinking: { agent: data.agent, action: data.action, content: data.content } });
    });

    es.onerror = () => {
      update({ connected: false });
    };

    return () => {
      es.close();
      esRef.current = null;
      stateRef.current = {
        reasoningSteps: [],
        patientProfile: null,
        workflowStatus: null,
        thinking: null,
        connected: false,
      };
    };
  }, [sessionId, update]);
}
