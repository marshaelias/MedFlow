import {
  User,
  Heart,
  Shield,
  Calendar,
  AlertTriangle,
  CheckCircle2,
  Clock,
  XCircle,
} from 'lucide-react';
import type { PatientProfile as PatientProfileType } from '@/types/api';

interface PatientProfileProps {
  profile: PatientProfileType | null;
  missingFields: string[];
}

function UrgencyBadge({ level }: { level: string | null }) {
  if (!level) return null;
  const lower = level.toLowerCase();
  const config: Record<string, { bg: string; text: string; icon: React.ReactNode }> = {
    critical: {
      bg: 'bg-red-100',
      text: 'text-urgency-critical',
      icon: <AlertTriangle className="w-3.5 h-3.5" />,
    },
    high: {
      bg: 'bg-orange-100',
      text: 'text-urgency-high',
      icon: <AlertTriangle className="w-3.5 h-3.5" />,
    },
    medium: {
      bg: 'bg-yellow-100',
      text: 'text-urgency-medium',
      icon: <Clock className="w-3.5 h-3.5" />,
    },
    low: {
      bg: 'bg-green-100',
      text: 'text-urgency-low',
      icon: <CheckCircle2 className="w-3.5 h-3.5" />,
    },
  };
  const c = config[lower] || config.medium;
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${c.bg} ${c.text}`}>
      {c.icon}
      {level}
    </span>
  );
}

function Field({ label, value, icon }: { label: string; value: React.ReactNode; icon?: React.ReactNode }) {
  return (
    <div className="flex items-start gap-2.5 py-2">
      {icon && <span className="text-slate-400 mt-0.5">{icon}</span>}
      <div className="flex-1 min-w-0">
        <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">{label}</p>
        <div className="text-sm text-slate-700 mt-0.5">{value || <span className="text-slate-300 italic">Not provided</span>}</div>
      </div>
    </div>
  );
}

export default function PatientProfileCard({ profile, missingFields }: PatientProfileProps) {
  const hasProfile = profile && (profile.name || profile.age || profile.symptoms?.length > 0);

  return (
    <div className="bg-white/90 rounded-2xl border border-slate-200/40 shadow-lg overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-med-green to-med-green-dark flex items-center justify-center">
            <User className="w-5 h-5 text-white" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-slate-800">Patient Profile</h2>
            <p className="text-xs text-slate-400">Auto-updated from conversation</p>
          </div>
        </div>
        {hasProfile && profile?.urgencyLevel && <UrgencyBadge level={profile.urgencyLevel} />}
      </div>

      <div className="px-5 py-3">
        {!hasProfile ? (
          <div className="flex flex-col items-center justify-center py-10 text-center text-slate-400 gap-2">
            <User className="w-10 h-10 text-slate-200" />
            <p className="text-sm">No patient data yet</p>
            <p className="text-xs text-slate-300">Start chatting to auto-populate</p>
          </div>
        ) : (
          <div className="divide-y divide-slate-50">
            <Field label="Name" value={profile.name} icon={<User className="w-4 h-4" />} />
            <Field label="Age / Gender" value={profile.age && profile.gender ? `${profile.age} / ${profile.gender}` : profile.age ? `${profile.age}` : profile.gender} icon={<Heart className="w-4 h-4" />} />
            <Field
              label="Symptoms"
              value={
                profile.symptoms?.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5 mt-1">
                    {profile.symptoms.map((s, i) => (
                      <span key={i} className="px-2 py-0.5 rounded-md bg-med-pink/40 text-xs font-medium text-slate-700">
                        {s}
                      </span>
                    ))}
                  </div>
                ) : null
              }
              icon={<Heart className="w-4 h-4" />}
            />
            <Field label="Condition" value={profile.condition} icon={<Heart className="w-4 h-4" />} />

            {profile.insurance && (
              <div className="py-2">
                <div className="flex items-center gap-2 mb-2">
                  <Shield className="w-4 h-4 text-slate-400" />
                  <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Insurance</p>
                  {profile.insurance.verified === true && (
                    <CheckCircle2 className="w-3.5 h-3.5 text-urgency-low" />
                  )}
                  {profile.insurance.verified === false && (
                    <XCircle className="w-3.5 h-3.5 text-urgency-critical" />
                  )}
                </div>
                <div className="ml-6 space-y-1 text-sm text-slate-600">
                  {profile.insurance.provider && <p>Provider: {profile.insurance.provider}</p>}
                  {profile.insurance.policyNumber && <p>Policy: {profile.insurance.policyNumber}</p>}
                  {profile.insurance.coverageStatus && <p>Coverage: {profile.insurance.coverageStatus}</p>}
                  {profile.insurance.copayAmount && <p>Copay: {profile.insurance.copayAmount}</p>}
                </div>
              </div>
            )}

            {profile.appointment && (
              <div className="py-2">
                <div className="flex items-center gap-2 mb-2">
                  <Calendar className="w-4 h-4 text-slate-400" />
                  <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Appointment</p>
                </div>
                <div className="ml-6 space-y-1 text-sm text-slate-600">
                  {profile.appointment.scheduledTime && <p>Time: {profile.appointment.scheduledTime}</p>}
                  {profile.appointment.department && <p>Dept: {profile.appointment.department}</p>}
                  {profile.appointment.provider && <p>Provider: {profile.appointment.provider}</p>}
                  {profile.appointment.notes && <p>Notes: {profile.appointment.notes}</p>}
                </div>
              </div>
            )}
          </div>
        )}

        {missingFields.length > 0 && (
          <div className="mt-4 p-3 rounded-xl bg-amber-50 border border-amber-200/60">
            <p className="text-xs font-semibold text-amber-700 mb-1.5">Missing Information</p>
            <div className="flex flex-wrap gap-1.5">
              {missingFields.map((f) => (
                <span key={f} className="px-2 py-0.5 rounded-md bg-amber-100 text-xs text-amber-700">
                  {f.replace(/_/g, ' ')}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
