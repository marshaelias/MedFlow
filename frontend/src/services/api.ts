import type { BrainResponse, PatientProfile, WorkflowState } from '@/types/api';

export async function sendChatMessage(sessionId: string, message: string): Promise<BrainResponse> {
  const res = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ session_id: sessionId, message }),
  });
  if (!res.ok) throw new Error(`Chat request failed: ${res.status}`);
  return res.json();
}

export async function fetchPatient(id: string): Promise<PatientProfile> {
  const res = await fetch(`/api/patient/${id}`);
  if (!res.ok) throw new Error(`Fetch patient failed: ${res.status}`);
  return res.json();
}

export async function fetchAllPatients(): Promise<PatientProfile[]> {
  const res = await fetch('/api/patients');
  if (!res.ok) throw new Error(`Fetch patients failed: ${res.status}`);
  return res.json();
}

export async function fetchWorkflow(id: string): Promise<WorkflowState> {
  const res = await fetch(`/api/workflow/${id}`);
  if (!res.ok) throw new Error(`Fetch workflow failed: ${res.status}`);
  return res.json();
}

export function createEventSource(sessionId: string): EventSource {
  return new EventSource(`/api/events/${sessionId}`);
}
