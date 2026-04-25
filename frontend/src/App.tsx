import { useState, useCallback } from 'react';
import { Activity } from 'lucide-react';
import AIChat from '@/components/AIChat';
import PatientProfileCard from '@/components/PatientProfile';
import ReasoningSteps from '@/components/ReasoningSteps';
import type { PatientProfile, ReasoningStep } from '@/types/api';

function generateSessionId() {
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export default function App() {
  const [sessionId] = useState(generateSessionId);
  const [patientProfile, setPatientProfile] = useState<PatientProfile | null>(null);
  const [reasoningSteps, setReasoningSteps] = useState<ReasoningStep[]>([]);
  const [workflowStatus, setWorkflowStatus] = useState<string | null>(null);
  const [missingFields, setMissingFields] = useState<string[]>([]);

  const handlePatientUpdate = useCallback((profile: PatientProfile | null) => {
    if (profile) setPatientProfile(profile);
  }, []);

  const handleReasoningUpdate = useCallback((steps: ReasoningStep[]) => {
    setReasoningSteps(steps);
  }, []);

  const handleWorkflowStatus = useCallback((status: string) => {
    setWorkflowStatus(status);
  }, []);

  const handleMissingFields = useCallback((fields: string[]) => {
    setMissingFields(fields);
  }, []);

  const statusLabel = workflowStatus
    ? workflowStatus.replace(/_/g, ' ').toLowerCase()
    : 'ready';
  const isActive = workflowStatus === 'ANALYZING' || workflowStatus === 'EXECUTING';

  return (
    <div className="min-h-screen bg-gradient-to-br from-beige-50 via-beige-100 to-beige-200">
      <header className="px-6 py-4 flex items-center justify-between bg-white/70 backdrop-blur-sm border-b border-slate-200/50">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-med-pink to-med-pink-dark flex items-center justify-center">
            <Activity className="w-5 h-5 text-white" />
          </div>
          <div>
            <p className="text-[11px] text-slate-400 uppercase tracking-widest font-medium">MedFlow</p>
            <h1 className="text-xl font-bold text-slate-800 leading-tight">Clinical Workflow Assistant</h1>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-slate-400 font-mono">{sessionId.slice(0, 16)}</span>
          <span
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold ${
              isActive
                ? 'bg-blue-100 text-blue-700'
                : workflowStatus === 'COMPLETED'
                  ? 'bg-green-100 text-green-700'
                  : workflowStatus === 'FAILED'
                    ? 'bg-red-100 text-red-700'
                    : 'bg-green-100 text-green-700'
            }`}
          >
            {isActive && <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse-dot" />}
            {statusLabel}
          </span>
        </div>
      </header>

      <main className="p-6 grid grid-cols-1 lg:grid-cols-3 gap-6 max-w-[1600px] mx-auto" style={{ height: 'calc(100vh - 73px)' }}>
        <div className="lg:col-span-2 min-h-0">
          <AIChat
            sessionId={sessionId}
            onPatientUpdate={handlePatientUpdate}
            onReasoningUpdate={handleReasoningUpdate}
            onWorkflowStatusChange={(status) => {
              handleWorkflowStatus(status);
              if (status === 'AWAITING_DATA') {
                handleMissingFields(['patient_name', 'symptoms']);
              } else {
                handleMissingFields([]);
              }
            }}
          />
        </div>
        <div className="flex flex-col gap-6 min-h-0 overflow-y-auto">
          <PatientProfileCard profile={patientProfile} missingFields={missingFields} />
          <ReasoningSteps steps={reasoningSteps} workflowStatus={workflowStatus} />
        </div>
      </main>
    </div>
  );
}
