import { useState, useRef, useEffect, useCallback } from 'react';
import { Send, Bot, User, Loader2 } from 'lucide-react';
import { sendChatMessage } from '@/services/api';
import { useSSE, type SSEState } from '@/hooks/useSSE';
import type { ChatMessage, BrainResponse } from '@/types/api';

interface AIChatProps {
  sessionId: string;
  onPatientUpdate: (profile: BrainResponse['patient_profile']) => void;
  onReasoningUpdate: (steps: BrainResponse['reasoning_chain']) => void;
  onWorkflowStatusChange: (status: string) => void;
}

export default function AIChat({
  sessionId,
  onPatientUpdate,
  onReasoningUpdate,
  onWorkflowStatusChange,
}: AIChatProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const handleSSE = useCallback(
    (state: SSEState) => {
      if (state.patientProfile) onPatientUpdate(state.patientProfile);
      if (state.reasoningSteps.length > 0) onReasoningUpdate(state.reasoningSteps);
      if (state.workflowStatus) onWorkflowStatusChange(state.workflowStatus);

      if (state.thinking) {
        setMessages((prev) => {
          const last = prev[prev.length - 1];
          if (last?.role === 'system' && last.metadata?.type === 'thinking') return prev;
          return [
            ...prev,
            {
              id: `thinking-${Date.now()}`,
              role: 'system',
              content: `${state.thinking.agent}: ${state.thinking.content}`,
              timestamp: new Date().toISOString(),
              metadata: { type: 'thinking' },
            },
          ];
        });
      }
    },
    [onPatientUpdate, onReasoningUpdate, onWorkflowStatusChange]
  );

  useSSE(sessionId, handleSSE);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || isLoading) return;

    const userMsg: ChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: text,
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setIsLoading(true);

    try {
      const res = await sendChatMessage(sessionId, text);

      const assistantMsg: ChatMessage = {
        id: `assistant-${Date.now()}`,
        role: 'assistant',
        content: res.message,
        timestamp: new Date().toISOString(),
      };

      setMessages((prev) => {
        const filtered = prev.filter((m) => !(m.role === 'system' && m.metadata?.type === 'thinking'));
        return [...filtered, assistantMsg];
      });

      if (res.patient_profile) onPatientUpdate(res.patient_profile);
      if (res.reasoning_chain) onReasoningUpdate(res.reasoning_chain);
      if (res.status) onWorkflowStatusChange(res.status);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          id: `error-${Date.now()}`,
          role: 'system',
          content: `Error: ${err instanceof Error ? err.message : 'Request failed'}`,
          timestamp: new Date().toISOString(),
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex flex-col h-full bg-white/90 rounded-2xl border border-slate-200/40 shadow-lg overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center gap-3">
        <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-med-pink to-med-pink-dark flex items-center justify-center">
          <Bot className="w-5 h-5 text-white" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-slate-800">MedFlow AI</h2>
          <p className="text-xs text-slate-400">Describe symptoms, insurance, or schedule appointments</p>
        </div>
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-center text-slate-400 gap-3 py-12">
            <Bot className="w-12 h-12 text-med-pink-dark/40" />
            <p className="text-sm max-w-[280px]">
              Hi! I'm your clinical assistant. Tell me about your symptoms, insurance details, or let me schedule an appointment for you.
            </p>
          </div>
        )}

        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            {msg.role !== 'user' && (
              <div
                className={`w-7 h-7 rounded-lg flex items-center justify-center shrink-0 mt-0.5 ${
                  msg.role === 'system'
                    ? 'bg-amber-100 text-amber-600'
                    : 'bg-gradient-to-br from-med-pink to-med-pink-dark text-white'
                }`}
              >
                <Bot className="w-4 h-4" />
              </div>
            )}
            <div
              className={`max-w-[75%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed ${
                msg.role === 'user'
                  ? 'bg-slate-800 text-white rounded-br-md'
                  : msg.role === 'system'
                    ? 'bg-amber-50 text-amber-800 border border-amber-200/60 rounded-bl-md'
                    : 'bg-slate-100 text-slate-800 rounded-bl-md'
              }`}
            >
              {msg.content}
            </div>
            {msg.role === 'user' && (
              <div className="w-7 h-7 rounded-lg bg-slate-700 text-white flex items-center justify-center shrink-0 mt-0.5">
                <User className="w-4 h-4" />
              </div>
            )}
          </div>
        ))}

        {isLoading && (
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-med-pink to-med-pink-dark text-white flex items-center justify-center shrink-0">
              <Bot className="w-4 h-4" />
            </div>
            <div className="bg-slate-100 rounded-2xl rounded-bl-md px-4 py-3 flex items-center gap-2">
              <Loader2 className="w-4 h-4 text-slate-400 animate-spin" />
              <span className="text-xs text-slate-400">Processing...</span>
            </div>
          </div>
        )}
      </div>

      <div className="px-4 py-3 border-t border-slate-100">
        <div className="flex gap-2 items-end">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type your message..."
            rows={1}
            className="flex-1 resize-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-med-pink-dark/30 focus:border-med-pink-dark/50 transition"
          />
          <button
            onClick={handleSend}
            disabled={isLoading || !input.trim()}
            className="w-10 h-10 rounded-xl bg-slate-800 text-white flex items-center justify-center hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed transition shrink-0"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
