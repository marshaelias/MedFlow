import { Cpu, CheckCircle2, Loader2, XCircle, SkipForward, Clock } from 'lucide-react';
import type { ReasoningStep, StepStatus } from '@/types/api';

interface ReasoningStepsProps {
  steps: ReasoningStep[];
  workflowStatus: string | null;
}

const statusConfig: Record<StepStatus, { icon: React.ReactNode; color: string; bg: string }> = {
  PENDING: {
    icon: <Clock className="w-3.5 h-3.5" />,
    color: 'text-slate-400',
    bg: 'bg-slate-100',
  },
  RUNNING: {
    icon: <Loader2 className="w-3.5 h-3.5 animate-spin" />,
    color: 'text-blue-500',
    bg: 'bg-blue-50',
  },
  COMPLETED: {
    icon: <CheckCircle2 className="w-3.5 h-3.5" />,
    color: 'text-urgency-low',
    bg: 'bg-green-50',
  },
  FAILED: {
    icon: <XCircle className="w-3.5 h-3.5" />,
    color: 'text-urgency-critical',
    bg: 'bg-red-50',
  },
  SKIPPED: {
    icon: <SkipForward className="w-3.5 h-3.5" />,
    color: 'text-slate-400',
    bg: 'bg-slate-50',
  },
};

export default function ReasoningSteps({ steps, workflowStatus }: ReasoningStepsProps) {
  return (
    <div className="bg-white/90 rounded-2xl border border-slate-200/40 shadow-lg overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center gap-3">
        <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-violet-400 to-violet-600 flex items-center justify-center">
          <Cpu className="w-5 h-5 text-white" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-slate-800">AI Reasoning</h2>
          <p className="text-xs text-slate-400">
            {workflowStatus ? `Status: ${workflowStatus}` : 'Waiting for input...'}
          </p>
        </div>
      </div>

      <div className="px-5 py-3 max-h-[400px] overflow-y-auto">
        {steps.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-10 text-center text-slate-400 gap-2">
            <Cpu className="w-10 h-10 text-slate-200" />
            <p className="text-sm">No reasoning steps yet</p>
            <p className="text-xs text-slate-300">Steps appear as the AI processes your request</p>
          </div>
        ) : (
          <div className="space-y-2">
            {steps.map((step, i) => {
              const cfg = statusConfig[step.status] || statusConfig.PENDING;
              return (
                <div
                  key={step.stepId || i}
                  className={`rounded-xl p-3 border border-slate-100 ${cfg.bg} transition-all`}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <span className={cfg.color}>{cfg.icon}</span>
                    <span className="text-xs font-semibold text-slate-700">{step.agent}</span>
                    <span className="text-xs text-slate-400">·</span>
                    <span className="text-xs text-slate-500">{step.action}</span>
                    <span className={`ml-auto text-[10px] font-medium px-1.5 py-0.5 rounded ${cfg.bg} ${cfg.color}`}>
                      {step.status}
                    </span>
                  </div>
                  {step.reasoning && (
                    <p className="text-xs text-slate-500 ml-6 leading-relaxed">{step.reasoning}</p>
                  )}
                  {step.fallbackPlan && step.status === 'FAILED' && (
                    <p className="text-xs text-amber-600 ml-6 mt-1">
                      Fallback: {step.fallbackPlan}
                    </p>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
