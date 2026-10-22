import {invalid} from './errors.mjs';
export function validateBusinessSettings(timezone,cutoffMinutes) {
  if(typeof timezone!=='string' || timezone.length>100 || !Number.isInteger(cutoffMinutes) || cutoffMinutes<0 || cutoffMinutes>1439) throw invalid();
  try { new Intl.DateTimeFormat('en',{timeZone:timezone}).format(); } catch { throw invalid('Timezone tidak dikenali.'); }
}
export function businessDate(at,timezone,cutoffMinutes) {
  validateBusinessSettings(timezone,cutoffMinutes);
  const parts=Object.fromEntries(new Intl.DateTimeFormat('en-GB',{timeZone:timezone,
    year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(at).map(p=>[p.type,p.value]));
  const date=new Date(`${parts.year}-${parts.month}-${parts.day}T12:00:00Z`);
  if(Number(parts.hour)*60+Number(parts.minute)<cutoffMinutes) date.setUTCDate(date.getUTCDate()-1);
  return date.toISOString().slice(0,10);
}
